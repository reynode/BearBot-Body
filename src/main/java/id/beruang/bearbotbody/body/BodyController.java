package id.beruang.bearbotbody.body;

import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.List;

/** Server-side physical controls; normal ChunkMap tracking synchronizes entity state to viewers. */
public final class BodyController {

    private final BearBotBodyEntity body;
    private float forwardInput;
    private float strafeInput;
    private boolean jumpRequested;
    private boolean sprinting;
    private boolean sneaking;

    public BodyController(BearBotBodyEntity body) {
        this.body = Objects.requireNonNull(body, "body");
    }

    public void setPosition(double x, double y, double z) {
        body.setPos(x, y, z);
    }

    /** Sets persistent forward/backward movement input in vanilla's [-1, 1] input scale. */
    public void moveForward(float speed) {
        move(speed, 0.0F);
    }

    public void moveBackward(float speed) {
        move(-Math.abs(speed), 0.0F);
    }

    public void strafeLeft(float speed) {
        move(0.0F, -Math.abs(speed));
    }

    public void strafeRight(float speed) {
        move(0.0F, Math.abs(speed));
    }

    /** Sets persistent vanilla movement input; diagonal input is normalized like player input. */
    public void move(float forward, float strafe) {
        float forwardValue = clampInput(forward);
        float strafeValue = clampInput(strafe);
        float magnitude = (float) Math.sqrt(forwardValue * forwardValue + strafeValue * strafeValue);
        if (magnitude > 1.0F) {
            forwardValue /= magnitude;
            strafeValue /= magnitude;
        }
        forwardInput = forwardValue;
        strafeInput = strafeValue;
    }

    /** Clears horizontal input; vanilla friction handles any remaining momentum. */
    public void stop() {
        forwardInput = 0.0F;
        strafeInput = 0.0F;
        body.xxa = 0.0F;
        body.zza = 0.0F;
    }

    /** Requests one vanilla jump on the next physical entity tick. */
    public void jump() {
        jumpRequested = true;
    }

    public void sprint(boolean enabled) {
        sprinting = enabled;
        body.setSprinting(enabled);
    }

    public void sneak(boolean enabled) {
        sneaking = enabled;
        body.setShiftKeyDown(enabled);
    }

    /** Called by BearBotBodyEntity once at the start of its server entity tick. */
    void applyMovementInput() {
        body.xxa = strafeInput;
        body.zza = forwardInput;
        body.setSprinting(sprinting);
        body.setShiftKeyDown(sneaking);
    }

    /** Consumes a queued jump and leaves jump velocity/cooldown to vanilla LivingEntity logic. */
    boolean beginJumpForTick() {
        boolean shouldJump = jumpRequested
                && body.isAlive()
                && body.onGround()
                && !body.isPassenger();
        jumpRequested = false;
        body.setJumping(shouldJump);
        return shouldJump;
    }

    void endJumpForTick() {
        body.setJumping(false);
    }

    public void setRotation(float yaw, float pitch) {
        body.setYRot(yaw);
        body.setXRot(pitch);
        body.setYBodyRot(yaw);
        body.setYHeadRot(yaw);
    }

    public void lookAt(double x, double y, double z) {
        double dx = x - body.getX();
        double dy = y - body.getEyeY();
        double dz = z - body.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDistance));
        setRotation(yaw, pitch);
    }

    public void lookAt(Entity target) {
        Objects.requireNonNull(target, "target");
        lookAt(target.getX(), target.getEyeY(), target.getZ());
    }

    public void lookAt(Player target) {
        Objects.requireNonNull(target, "target");
        lookAt(target.getX(), target.getEyeLocation().getY(), target.getZ());
    }

    public boolean startRiding(Entity vehicle) {
        return body.startRiding(Objects.requireNonNull(vehicle, "vehicle"));
    }

    public void stopRiding() {
        body.stopRiding();
    }

    /** Executes the vanilla ServerPlayer attack path for a Brain-selected target. */
    public boolean attack(Entity target) {
        Objects.requireNonNull(target, "target");
        if (!body.isAlive() || target == body || target.isRemoved() || !target.isAlive()
                || target.level() != body.level()
                || body.distanceToSqr(target) > interactionRangeSquared()) {
            return false;
        }
        body.attack(target);
        body.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    /** Executes an entity's normal NMS interaction with this ServerPlayer. */
    public InteractionResult interact(Entity target, InteractionHand hand) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(hand, "hand");
        if (!body.isAlive() || target.isRemoved() || target.level() != body.level()
                || body.distanceToSqr(target) > interactionRangeSquared()) {
            return InteractionResult.PASS;
        }
        return body.interactOn(target, hand);
    }

    /** Executes the ServerPlayerGameMode item use path. */
    public InteractionResult useItem(InteractionHand hand) {
        Objects.requireNonNull(hand, "hand");
        ItemStack held = body.getItemInHand(hand);
        if (!body.isAlive() || held.isEmpty()) {
            return InteractionResult.PASS;
        }
        return body.gameMode.useItem(body, body.level(), held, hand);
    }

    /** Executes vanilla block/item use at the specified hit location. */
    public InteractionResult useItemOn(
            BlockPos position, Direction face, Vec3 hitLocation, InteractionHand hand) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(hitLocation, "hitLocation");
        Objects.requireNonNull(hand, "hand");
        if (!body.isAlive()) {
            return InteractionResult.PASS;
        }
        double blockRange = body.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        if (hitLocation.distanceToSqr(body.getX(), body.getEyeY(), body.getZ()) > blockRange * blockRange) {
            return InteractionResult.PASS;
        }
        BlockHitResult hit = new BlockHitResult(hitLocation, face, position, false);
        ItemStack held = body.getItemInHand(hand);
        return body.gameMode.useItemOn(body, body.level(), held, hand, hit);
    }

    public void setItemInHand(InteractionHand hand, ItemStack item) {
        body.setItemInHand(Objects.requireNonNull(hand, "hand"), Objects.requireNonNull(item, "item"));
    }

    public ItemStack getItemInHand(InteractionHand hand) {
        return body.getItemInHand(Objects.requireNonNull(hand, "hand"));
    }

    public net.minecraft.world.entity.player.Inventory getInventory() {
        return body.getInventory();
    }

    public List<Entity> getNearbyEntities(double radius) {
        double boundedRadius = Math.max(0.0, Math.min(128.0, radius));
        return List.copyOf(body.level().getEntities(body, body.getBoundingBox().inflate(boundedRadius)));
    }

    public Entity getVehicle() {
        return body.getVehicle();
    }

    public boolean isPassenger() {
        return body.isPassenger();
    }

    public boolean isAlive() {
        return body.isAlive();
    }

    public boolean isDeadOrDying() {
        return body.isDeadOrDying();
    }

    public float getHealth() {
        return body.getHealth();
    }

    public float getYaw() {
        return body.getYRot();
    }

    public float getPitch() {
        return body.getXRot();
    }

    public float getMovementSpeed() {
        return body.getSpeed();
    }

    public Vec3 getPosition() {
        return new Vec3(body.getX(), body.getY(), body.getZ());
    }

    public Vec3 getVelocity() {
        return body.getDeltaMovement();
    }

    public boolean isOnGround() {
        return body.onGround();
    }

    public boolean isInWater() {
        return body.isInWater();
    }

    public boolean isInLava() {
        return body.isInLava();
    }

    public boolean isSwimming() {
        return body.isSwimming();
    }

    public int getAirSupply() {
        return body.getAirSupply();
    }

    public boolean isSprinting() {
        return body.isSprinting();
    }

    public boolean isSneaking() {
        return body.isShiftKeyDown();
    }

    public float getConfiguredForwardInput() {
        return forwardInput;
    }

    public float getConfiguredStrafeInput() {
        return strafeInput;
    }

    public boolean isConfiguredSprinting() {
        return sprinting;
    }

    public boolean isConfiguredSneaking() {
        return sneaking;
    }

    public double getFallDistance() {
        return body.fallDistance;
    }

    public BearBotBodyEntity getEntity() {
        return body;
    }

    private static float clampInput(float value) {
        return Math.max(-1.0F, Math.min(1.0F, value));
    }

    private double interactionRangeSquared() {
        double range = body.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        return range * range;
    }
}
