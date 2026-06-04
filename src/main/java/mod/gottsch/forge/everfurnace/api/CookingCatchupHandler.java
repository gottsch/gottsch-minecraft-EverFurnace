package mod.gottsch.forge.everfurnace.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Implemented by any mod that wants EverFurnace-style offline catch-up for
 * a custom cooking block entity.
 *
 * <p>Register an implementation via
 * {@link EverFurnaceApi#registerHandler(net.minecraft.world.level.block.entity.BlockEntityType, CookingCatchupHandler)}.
 * EverFurnace will invoke {@link #applyCatchup} once per tick of the target
 * block entity, but only when the elapsed game-time gap exceeds the configured
 * minimum delta threshold.
 *
 * <p>The handler is responsible for all catch-up logic: pre-condition checks,
 * cooking progress advancement, fuel consumption, notifications, XP, particles,
 * and marking the block entity dirty. EverFurnace only supplies the delta.
 *
 * @author Mark Gottschling
 */
@FunctionalInterface
public interface CookingCatchupHandler {

    /**
     * Apply offline catch-up to {@code blockEntity} for {@code deltaTime} ticks.
     *
     * @param blockEntity the cooking block entity that is now ticking
     * @param deltaTime   ticks elapsed since the block entity last ticked
     *                    (already clamped to {@link EverFurnaceApi#getMaxCatchupTicks()})
     * @param level       the server level the block entity lives in
     * @param pos         the block position of the block entity
     */
    void applyCatchup(BlockEntity blockEntity, long deltaTime, ServerLevel level, BlockPos pos);
}
