package dev.vestige.core;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server→client sends that skip connections without VESTIGE's channels (fake players from other
 * mods, test players) instead of throwing. All common code sends through here.
 */
public final class Net {
    private Net() {}

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** To every player that has the entity's chunk loaded, plus the entity itself if it is a player. */
    public static void sendAround(Entity entity, CustomPacketPayload payload, boolean includeSelf) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        for (ServerPlayer p : level.getChunkSource().chunkMap.getPlayers(new ChunkPos(entity.blockPosition()), false)) {
            if (p != entity) {
                send(p, payload);
            }
        }
        if (includeSelf && entity instanceof ServerPlayer self) {
            send(self, payload);
        }
    }
}
