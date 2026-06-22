package mod.gottsch.forge.everfurnace.core.mixin;

import mod.gottsch.forge.everfurnace.api.EverFurnaceApi;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Timing infrastructure for EverFurnace catch-up on the brewing stand.
 *
 * <p>Mirrors {@link CampfireBlockEntityMixin} in structure: persists
 * {@code lastGameTime}, computes {@code deltaTime}, and delegates to
 * {@link EverFurnaceApi}.  All brewing-cycle logic lives in
 * {@link mod.gottsch.forge.everfurnace.core.catchup.BrewingStandCatchupHandler}.
 *
 * <p>The vanilla {@code serverTick} ticker only runs for chunks within simulation
 * distance, so proximity gating is implicit.
 *
 * @author Mark Gottschling
 */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityMixin {

    @Unique private static final String LAST_GAME_TIME_TAG  = "everfurnace_lastGameTime";
    @Unique private static final String NBT_VERSION_TAG     = "everfurnace_version";
    @Unique private static final int    CURRENT_NBT_VERSION = 1;

    @Unique private long everFurnace_1_20_1$lastGameTime;

    // -------------------------------------------------------------------------
    // NBT persistence
    // -------------------------------------------------------------------------

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void everFurnace_1_20_1$onSave(CompoundTag tag, CallbackInfo ci) {
        tag.putInt (NBT_VERSION_TAG,    CURRENT_NBT_VERSION);
        tag.putLong(LAST_GAME_TIME_TAG, this.everFurnace_1_20_1$lastGameTime);
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void everFurnace_1_20_1$onLoad(CompoundTag tag, CallbackInfo ci) {
        this.everFurnace_1_20_1$lastGameTime = tag.getLong(LAST_GAME_TIME_TAG);
    }

    // -------------------------------------------------------------------------
    // Tick injection — timing only; logic delegated to BrewingStandCatchupHandler
    // -------------------------------------------------------------------------

    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void everFurnace_1_20_1$onServerTick(Level world, BlockPos pos, BlockState state,
                                                         BrewingStandBlockEntity blockEntity, CallbackInfo ci) {

        if (!EverFurnaceApi.isCatchupEnabled()) return;

        BrewingStandBlockEntityMixin mixin = (BrewingStandBlockEntityMixin)(Object) blockEntity;

        long currentGameTime   = world.getGameTime();
        long localLastGameTime = mixin.everFurnace_1_20_1$lastGameTime;

        mixin.everFurnace_1_20_1$lastGameTime = currentGameTime;

        if (localLastGameTime == 0L) return;

        long deltaTime = currentGameTime - localLastGameTime;
        if (deltaTime < EverFurnaceApi.getMinDeltaThreshold()) return;

        deltaTime = Math.min(deltaTime, EverFurnaceApi.getMaxCatchupTicks());

        if (!(world instanceof ServerLevel serverLevel)) return;

        final long finalDelta = deltaTime;
        EverFurnaceApi.findHandler(blockEntity)
                .ifPresent(handler -> handler.applyCatchup(blockEntity, finalDelta, serverLevel, pos));
    }
}
