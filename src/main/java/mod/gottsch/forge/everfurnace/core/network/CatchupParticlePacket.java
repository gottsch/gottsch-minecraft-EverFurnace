package mod.gottsch.forge.everfurnace.core.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import mod.gottsch.forge.everfurnace.core.config.EverFurnaceConfig;

import java.util.Random;
import java.util.function.Supplier;

/**
 * sent server → client when catch-up completes and at least one item was cooked.
 * the client handler spawns a brief flame/smoke particle burst at the furnace position.
 *
 * @author by Mark Gottschling on 3/24/2026
 */

public class CatchupParticlePacket {

    private final BlockPos pos;

    public CatchupParticlePacket(BlockPos pos) {
        this.pos = pos;
    }

    // -------------------------------------------------------------------------
    // serialization
    // -------------------------------------------------------------------------

    public static void encode(CatchupParticlePacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
    }

    public static CatchupParticlePacket decode(FriendlyByteBuf buf) {
        return new CatchupParticlePacket(buf.readBlockPos());
    }

    // -------------------------------------------------------------------------
    // client handler
    // -------------------------------------------------------------------------

    public static void handle(CatchupParticlePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (!EverFurnaceConfig.CLIENT.particleBurstEnabled.get()) return;

            Level level = Minecraft.getInstance().level;
            if (level == null) return;

            Random rand = new Random();
            double cx = packet.pos.getX() + 0.5;
            double cy = packet.pos.getY() + 0.5;
            double cz = packet.pos.getZ() + 0.5;

            // Spawn a small burst of flame and smoke around the furnace face.
            for (int i = 0; i < 12; i++) {
                double ox = (rand.nextDouble() - 0.5) * 0.8;
                double oy = rand.nextDouble() * 0.6;
                double oz = (rand.nextDouble() - 0.5) * 0.8;
                level.addParticle(ParticleTypes.FLAME, cx + ox, cy + oy, cz + oz,
                        0, 0.02, 0);
            }
            for (int i = 0; i < 8; i++) {
                double ox = (rand.nextDouble() - 0.5) * 0.8;
                double oy = rand.nextDouble() * 0.8;
                double oz = (rand.nextDouble() - 0.5) * 0.8;
                level.addParticle(ParticleTypes.LARGE_SMOKE, cx + ox, cy + oy, cz + oz,
                        0, 0.03, 0);
            }
        });
        ctx.setPacketHandled(true);
    }
}
