package id.beruang.bearbotbody.body;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.entity.CraftPlayer;

import java.util.List;
import java.util.Objects;

/** Owns BearBot's NMS lifecycle, no-network connection shim, entity, and physical controller. */
public final class BearBotBody {

    private final ServerLevel level;
    private final BearBotBodyEntity entity;
    private final BodyController controller;
    private final PacketSinkConnection connectionShim;
    private boolean spawned;
    private boolean textFilterJoined;
    private boolean despawnInProgress;

    public BearBotBody(MinecraftServer server, ServerLevel level) {
        this.level = Objects.requireNonNull(level, "level");
        this.entity = new BearBotBodyEntity(Objects.requireNonNull(server, "server"), level);
        this.controller = new BodyController(entity);
        entity.setController(controller);

        this.connectionShim = new PacketSinkConnection();
        try {
            new ServerGamePacketListenerImpl(
                    server,
                    this.connectionShim,
                    entity,
                    CommonListenerCookie.createInitial(entity.getGameProfile(), false)
            );
            this.textFilterJoined = true;
        } catch (RuntimeException exception) {
            this.connectionShim.closeSink();
            throw exception;
        }
    }

    public void spawn(double x, double y, double z, float yaw, float pitch) {
        if (spawned) {
            throw new IllegalStateException("BearBot is already in the world");
        }

        controller.setPosition(x, y, z);
        controller.setRotation(yaw, pitch);

        try {
            if (!level.hasChunkAt(entity.blockPosition())) {
                throw new IllegalStateException("BearBot spawn chunk is not loaded");
            }
            Entity existing = level.getEntity(entity.getUUID());
            if (existing != null && existing != entity && !existing.isRemoved()) {
                throw new IllegalStateException("An entity with BearBot's stable UUID already exists");
            }
            // ServerEntity handles entity spawn, movement, metadata, and passenger packets. Player
            // profile data still belongs to PlayerList, which this agent intentionally does not join.
            broadcastPlayerInfo(new ClientboundPlayerInfoUpdatePacket(
                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                    entity
            ));
            level.addNewPlayer(entity);
            spawned = true;
        } catch (RuntimeException exception) {
            rollbackFailedSpawn(exception);
            throw exception;
        }
    }

    public void showPlayerInfoTo(Player viewer) {
        if (spawned && viewer.isOnline()) {
            send(viewer, new ClientboundPlayerInfoUpdatePacket(
                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                    entity
            ));
        }
    }

    public void despawn() {
        if (!spawned) {
            controller.stop();
            connectionShim.closeSink();
            return;
        }

        despawnInProgress = true;
        try {
            controller.stop();
            for (Entity passenger : List.copyOf(entity.getPassengers())) {
                passenger.stopRiding();
            }
            entity.stopRiding();
            level.removePlayerImmediately(entity, Entity.RemovalReason.DISCARDED);
            spawned = false;
        } finally {
            try {
                if (entity.isRemoved()) {
                    spawned = false;
                    try {
                        removePlayerInfo();
                    } finally {
                        try {
                            detachPlayerState();
                        } finally {
                            connectionShim.closeSink();
                        }
                    }
                } else {
                    connectionShim.closeSink();
                }
            } finally {
                despawnInProgress = false;
            }
        }
    }

    /** Cleans resources if vanilla removes the entity through death or another world lifecycle. */
    public void handleExternalRemoval() {
        if (despawnInProgress || !spawned || !entity.isRemoved()) {
            return;
        }
        spawned = false;
        try {
            removePlayerInfo();
        } finally {
            try {
                detachPlayerState();
            } finally {
                connectionShim.closeSink();
            }
        }
    }

    public boolean isDespawnInProgress() {
        return despawnInProgress;
    }

    public BodyController getController() {
        return controller;
    }

    public BearBotBodyEntity getEntity() {
        return entity;
    }

    public boolean isSpawned() {
        return spawned;
    }

    public boolean hasConnection() {
        return entity.connection != null;
    }

    public boolean hasChannel() {
        return connectionShim.channel != null;
    }

    public boolean isChannelActive() {
        return connectionShim.channel != null && connectionShim.channel.isActive();
    }

    public boolean isChannelOpen() {
        return connectionShim.channel != null && connectionShim.channel.isOpen();
    }

    public String getConnectionLifecycleState() {
        return connectionShim.getLifecycleState();
    }

    private void broadcastPlayerInfo(Packet<?> packet) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            send(viewer, packet);
        }
    }

    private void removePlayerInfo() {
        ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(List.of(entity.getUUID()));
        broadcastPlayerInfo(packet);
    }

    private void rollbackFailedSpawn(RuntimeException cause) {
        try {
            if (level.getEntity(entity.getUUID()) == entity && !entity.isRemoved()) {
                level.removePlayerImmediately(entity, Entity.RemovalReason.DISCARDED);
            }
        } catch (RuntimeException cleanupFailure) {
            cause.addSuppressed(cleanupFailure);
        }

        try {
            removePlayerInfo();
        } catch (RuntimeException cleanupFailure) {
            cause.addSuppressed(cleanupFailure);
        } finally {
            spawned = false;
            try {
                detachPlayerState();
            } catch (RuntimeException cleanupFailure) {
                cause.addSuppressed(cleanupFailure);
            } finally {
                try {
                    connectionShim.closeSink();
                } catch (RuntimeException cleanupFailure) {
                    cause.addSuppressed(cleanupFailure);
                }
            }
        }
    }

    private void detachPlayerState() {
        if (textFilterJoined) {
            textFilterJoined = false;
            entity.getTextFilter().leave();
        }
        entity.getAdvancements().stopListening();
        entity.getAdvancements().setPlayer(null);
    }

    private static void send(Player viewer, Packet<?> packet) {
        if (viewer.isOnline()) {
            ((CraftPlayer) viewer).getHandle().connection.send(packet);
        }
    }

    /** An in-memory, registered Netty channel with a sink for all outbound traffic. */
    private static final class PacketSinkConnection extends Connection {
        private final EmbeddedChannel sinkChannel;
        private boolean closed;

        private PacketSinkConnection() {
            super(PacketFlow.SERVERBOUND);
            this.sinkChannel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
                    ReferenceCountUtil.release(message);
                    promise.trySuccess();
                }
            });
            // Deliberately keep Connection out of this local channel's pipeline: its
            // channelInactive callback treats a close as a client disconnect. The sink channel
            // is active/registered itself; assign it directly because no network channelActive
            // lifecycle exists here.
            this.channel = sinkChannel;
        }

        @Override
        public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
            // Packets addressed to BearBot itself are discarded. Its viewers are updated through
            // ordinary ServerLevel entity tracking on their real network connections.
            if (listener != null && channel != null) {
                channel.newSucceededFuture().addListener(listener);
            }
        }

        private String getLifecycleState() {
            if (closed) {
                return "CLOSED";
            }
            if (channel == null) {
                return "INITIALIZING";
            }
            if (channel.isActive()) {
                return "ACTIVE_IN_MEMORY";
            }
            return channel.isOpen() ? "OPEN_INACTIVE" : "CLOSED";
        }

        private void closeSink() {
            if (closed) {
                return;
            }
            closed = true;
            sinkChannel.close();
            sinkChannel.finishAndReleaseAll();
        }
    }
}
