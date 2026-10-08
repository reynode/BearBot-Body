package id.beruang.bearbotbody.body;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** A ServerPlayer whose entity and player ticks are driven by its ServerLevel. */
public final class BearBotBodyEntity extends ServerPlayer {

    private static final UUID BEARBOT_UUID = UUID.nameUUIDFromBytes(
            "bearcraft:bearbot-body".getBytes(StandardCharsets.UTF_8)
    );

    private BodyController controller;
    private long physicalTickCount;

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
        boolean jumpThisTick = controller.beginJumpForTick();
        try {
            // ServerLevel drives this entity once. ServerPlayer.tick() does not call doTick();
            // its packet listener normally does, so this is the sole doTick() call for BearBot.
            doTick();
        } finally {
            if (jumpThisTick) {
                controller.endJumpForTick();
            }
        }
        super.tick();
    }

    public long getPhysicalTickCount() {
        return physicalTickCount;
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
}
