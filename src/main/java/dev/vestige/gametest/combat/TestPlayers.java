package dev.vestige.gametest.combat;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.phys.Vec3;

/** Survival-mode mock players for combat GameTests (vanilla's mock player and the GameTest world are creative). */
public final class TestPlayers {
    private TestPlayers() {}

    public static ServerPlayer survival(GameTestHelper h, Vec3 relativePos, float yaw) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "vestige-test"), false);
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        // Behave like a fully negotiated NeoForge client so payload encoding and syncing really run.
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        // The GameTest world defaults to creative (invulnerable); combat tests need a real survival player.
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        Vec3 abs = h.absoluteVec(relativePos);
        player.teleportTo(h.getLevel(), abs.x, abs.y, abs.z, yaw, 0f);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setHealth(player.getMaxHealth());
        // Let vanilla's 60-tick spawn protection run out, as it would for a real player.
        for (int i = 0; i < 61; i++) {
            player.tick();
        }
        return player;
    }

    public static void remove(ServerPlayer player) {
        player.getServer().getPlayerList().remove(player);
    }
}
