package mod.gottsch.forge.everfurnace.core.network;

import mod.gottsch.forge.everfurnace.core.config.EverFurnaceConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
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

    public static void spawnParticles(BlockPos pos) {
        if (!EverFurnaceConfig.CLIENT.particleBurstEnabled.get()) return;

        Level level = Minecraft.getInstance().level;
        if (level == null) return;

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
}
