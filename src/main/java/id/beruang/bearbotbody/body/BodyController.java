package id.beruang.bearbotbody.body;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.bukkit.entity.Player;

import java.util.Objects;

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

    public boolean isSprinting() {
        return body.isSprinting();
    }

    public boolean isSneaking() {
        return body.isShiftKeyDown();
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
}
