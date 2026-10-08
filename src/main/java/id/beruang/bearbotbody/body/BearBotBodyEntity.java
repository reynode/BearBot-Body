package id.beruang.bearbotbody.body;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
}
