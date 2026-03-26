package mod.gottsch.forge.everfurnace.core.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

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
        ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> CatchupParticleHandler.spawnParticles(packet.pos))
        );
        ctx.setPacketHandled(true);
    }
}
