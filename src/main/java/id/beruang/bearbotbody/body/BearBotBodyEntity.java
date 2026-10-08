package id.beruang.bearbotbody.body;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** A ServerPlayer whose entity and player ticks are driven by its ServerLevel. */
public final class BearBotBodyEntity extends ServerPlayer {

    private static final UUID BEARBOT_UUID = UUID.nameUUIDFromBytes(
            "bearcraft:bearbot-body".getBytes(StandardCharsets.UTF_8)
    );

    private BodyController controller;
    private long physicalTickCount;
    private TickDiagnostics lastTickDiagnostics = TickDiagnostics.empty();
    private MovementDiagnostics lastMovementDiagnostics = MovementDiagnostics.empty();
    private boolean executingTravel;

    public BearBotBodyEntity(MinecraftServer server, ServerLevel level) {
        super(server, level, new GameProfile(BEARBOT_UUID, "BearBot"), ClientInformation.createDefault());
        // ServerPlayer's constructor obtains this cache from PlayerList even though BearBot is not
        // globally registered there. Reload it so a previous despawn's listener cleanup is undone.
        getAdvancements().reload(server.getAdvancements());
        setCustomName(Component.literal("BearBot"));
        setCustomNameVisible(true);
        setClientLoaded(true);
    }

    /** The network listener normally calls doTick; this bot has no client connection to do that. */
    @Override
    public void tick() {
        physicalTickCount++;
        controller.applyMovementInput();
        lastTickDiagnostics = TickDiagnostics.before(this);
        lastMovementDiagnostics = MovementDiagnostics.empty();
        boolean jumpThisTick = controller.beginJumpForTick();
        try {
            // ServerLevel drives this entity once. ServerPlayer.tick() does not call doTick();
            // its packet listener normally does, so this is the sole doTick() call for BearBot.
            doTick();
        } finally {
            lastTickDiagnostics = lastTickDiagnostics.withAfter(this);
            if (jumpThisTick) {
                controller.endJumpForTick();
            }
        }
        super.tick();
    }

    public long getPhysicalTickCount() {
        return physicalTickCount;
    }

    public TickDiagnostics getLastTickDiagnostics() {
        return lastTickDiagnostics;
    }

    public MovementDiagnostics getLastMovementDiagnostics() {
        return lastMovementDiagnostics;
    }

    /** Captures the vanilla travel call without changing its input or invoking it twice. */
    @Override
    public void travel(Vec3 travelVector) {
        lastMovementDiagnostics = lastMovementDiagnostics.withTravel(this, travelVector);
        executingTravel = true;
        try {
            super.travel(travelVector);
        } finally {
            executingTravel = false;
        }
    }

    /** Captures the exact request and resolved position delta from vanilla collision movement. */
    @Override
    public void move(MoverType type, Vec3 movement) {
        if (!executingTravel) {
            super.move(type, movement);
            return;
        }
        Vec3 before = this.position();
        super.move(type, movement);
        lastMovementDiagnostics = lastMovementDiagnostics.withMove(
                this, type, movement, this.position().subtract(before));
    }

    void setController(BodyController controller) {
        if (this.controller != null) {
            throw new IllegalStateException("BearBot controller is already attached");
        }
        this.controller = controller;
    }

    /** BearBot instances are runtime agents and must not be written into entity chunk data. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    public record TickDiagnostics(
            float forwardInputBefore,
            float strafeInputBefore,
            float forwardInputAfter,
            float strafeInputAfter,
            Vec3 deltaBefore,
            Vec3 deltaAfter,
            Vec3 positionBefore,
            Vec3 positionAfter,
            boolean immobile,
            boolean canSimulateMovement,
            boolean effectiveAi,
            boolean removed,
            boolean spectator
    ) {
        private static TickDiagnostics empty() {
            Vec3 zero = Vec3.ZERO;
            return new TickDiagnostics(0.0F, 0.0F, 0.0F, 0.0F,
                    zero, zero, zero, zero, false, false, false, false, false);
        }

        private static TickDiagnostics before(BearBotBodyEntity entity) {
            return new TickDiagnostics(
                    entity.zza, entity.xxa, entity.zza, entity.xxa,
                    entity.getDeltaMovement(), entity.getDeltaMovement(),
                    entity.position(), entity.position(),
                    entity.isImmobile(), entity.canSimulateMovement(), entity.isEffectiveAi(),
                    entity.isRemoved(), entity.isSpectator());
        }

        private TickDiagnostics withAfter(BearBotBodyEntity entity) {
            return new TickDiagnostics(
                    forwardInputBefore, strafeInputBefore,
                    entity.zza, entity.xxa,
                    deltaBefore, entity.getDeltaMovement(),
                    positionBefore, entity.position(),
                    immobile, canSimulateMovement, effectiveAi, removed, spectator);
        }
    }

    public record MovementDiagnostics(
            Vec3 travelInput,
            Vec3 requestedMovement,
            Vec3 appliedMovement,
            MoverType moverType,
            float movementSpeed,
            float airborneSpeed,
            float yaw,
            boolean onGround,
            boolean horizontalCollision,
            boolean verticalCollision,
            boolean noPhysics,
            boolean passenger,
            boolean controllingPassenger
    ) {
        private static MovementDiagnostics empty() {
            return new MovementDiagnostics(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, null,
                    0.0F, 0.0F, 0.0F, false, false, false, false, false, false);
        }

        private MovementDiagnostics withTravel(BearBotBodyEntity entity, Vec3 input) {
            return new MovementDiagnostics(
                    input, Vec3.ZERO, Vec3.ZERO, null,
                    entity.getSpeed(), entity.getFlyingSpeed(), entity.getYRot(), entity.onGround(),
                    false, false, entity.noPhysics, entity.isPassenger(), entity.getControllingPassenger() != null);
        }

        private MovementDiagnostics withMove(
                BearBotBodyEntity entity, MoverType type, Vec3 requested, Vec3 applied) {
            return new MovementDiagnostics(
                    travelInput, requested, applied, type,
                    movementSpeed, airborneSpeed, yaw, onGround,
                    entity.horizontalCollision, entity.verticalCollision, entity.noPhysics,
                    entity.isPassenger(), entity.getControllingPassenger() != null);
        }
    }
}
