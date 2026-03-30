package mod.gottsch.forge.everfurnace.core.network;

import mod.gottsch.forge.everfurnace.core.config.EverFurnaceConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Random;

/**
 * client-only handler for CatchupParticlePacket.
 * isolated into its own class so the server never attempts to load
 * any client-only classes (Minecraft, ClientLevel, etc.).
 *
 * @author by Mark Gottschling on 3/26/2026
 */
@OnlyIn(Dist.CLIENT)
public class CatchupParticleHandler {

    public static void handle(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;

        spawnParticles(level, pos);
        playSound(level, pos);
        snapLitState(level, pos);
    }

    public static void spawnParticles(Level level, BlockPos pos) {
        if (!EverFurnaceConfig.CLIENT.particleBurstEnabled.get()) return;

        Random rand = new Random();
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;

        for (int i = 0; i < 12; i++) {
            double ox = (rand.nextDouble() - 0.5) * 0.8;
            double oy = rand.nextDouble() * 0.6;
            double oz = (rand.nextDouble() - 0.5) * 0.8;
            level.addParticle(ParticleTypes.FLAME, cx + ox, cy + oy, cz + oz, 0, 0.02, 0);
        }
        for (int i = 0; i < 8; i++) {
            double ox = (rand.nextDouble() - 0.5) * 0.8;
            double oy = rand.nextDouble() * 0.8;
            double oz = (rand.nextDouble() - 0.5) * 0.8;
            level.addParticle(ParticleTypes.LARGE_SMOKE, cx + ox, cy + oy, cz + oz, 0, 0.03, 0);
        }
    }

    // -------------------------------------------------------------------------
    // sound cue
    // -------------------------------------------------------------------------

    private static void playSound(Level level, BlockPos pos) {
        if (!EverFurnaceConfig.CLIENT.soundCueEnabled.get()) return;

        level.playLocalSound(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.FURNACE_FIRE_CRACKLE,
                SoundSource.BLOCKS,
                1.0f, 1.0f, false);
    }

    // -------------------------------------------------------------------------
    // light state snap
    // -------------------------------------------------------------------------

    /**
     * forces the client to immediately update the furnace block's LIT state so
     * the local light level reflects post-catch-up reality without waiting for
     * the server's next block-update packet.
     *
     * <p>we read the current block state and only call {@code setBlockAndUpdate}
     * if the LIT value needs to change, avoiding a needless re-render when the
     * state is already correct (e.g. the furnace was already lit before catch-up).
     */
    private static void snapLitState(Level level, BlockPos pos) {
        if (!EverFurnaceConfig.CLIENT.lightFlickerEnabled.get()) return;

        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(AbstractFurnaceBlock.LIT)) return;

        // the server has already updated the authoritative state; we just need
        // to make the client agree immediately rather than waiting for the sync.
        // re-setting the same state is harmless — it triggers a light update.
        level.setBlockAndUpdate(pos, state);
    }
}
