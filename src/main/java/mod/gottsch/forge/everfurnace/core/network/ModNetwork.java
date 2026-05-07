package mod.gottsch.forge.everfurnace.core.network;

import mod.gottsch.forge.everfurnace.EverFurnace;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * @author Mark Gottschling on 3/24/2026
 */
public class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";

    // acceptMissingOr — vanilla clients (without the mod) are accepted silently
    // rather than being kicked with a channel-mismatch error.
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(EverFurnace.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION::equals),
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        CHANNEL.registerMessage(
                0,
                CatchupParticlePacket.class,
                CatchupParticlePacket::encode,
                CatchupParticlePacket::decode,
                CatchupParticlePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    /**
     * Sends a {@link CatchupParticlePacket} to nearby players that have EverFurnace installed.
     *
     * <p>Vanilla clients (who lack the channel) are skipped via
     * {@link SimpleChannel#isRemotePresent}; this prevents sending to connections
     * that accepted the handshake via {@code acceptMissingOr} but never registered
     * the channel on their side.
     */
    public static void sendCatchupParticles(ServerLevel level, BlockPos pos) {
        CatchupParticlePacket packet = new CatchupParticlePacket(pos);
        double radiusSq = 32.0 * 32.0;
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        for (ServerPlayer player : level.players()) {
            if (!CHANNEL.isRemotePresent(player.connection.connection)) continue;
            if (player.distanceToSqr(cx, cy, cz) > radiusSq) continue;
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }
}
