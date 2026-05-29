package mod.gottsch.forge.everfurnace.core.mixin;

import mod.gottsch.forge.everfurnace.core.config.EverFurnaceConfig;
import mod.gottsch.forge.everfurnace.core.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds offline catch-up to vanilla campfires (and soul campfires).
 *
 * <p>The vanilla {@code cookTick} ticker only runs for chunks that are actually
 * ticking — i.e. within a player's simulation distance — and only while the
 * campfire is lit (otherwise {@code cooldownTick} is registered instead). So
 * proximity gating and the "is lit" check are both implicit in hooking this
 * method: while no player is near, the ticker doesn't run and the real-world
 * gap accumulates in {@link #everFurnace_1_20_1$lastGameTime}; when a player
 * brings the chunk back into range the first tick applies the catch-up.
 *
 * <p>Rather than re-implement the recipe assemble / drop / slot-clear that the
 * vanilla body performs on completion, this inject simply advances each slot's
 * progress (capped at its total). A slot pushed to its total is completed by the
 * vanilla body's own {@code ++}/threshold check that runs immediately after this
 * inject returns. Each slot holds a single item and is not restocked, so output
 * is inherently bounded to one item per slot.
 */
@Mixin(CampfireBlockEntity.class)
public abstract class CampfireBlockEntityMixin {

    @Unique private static final String LAST_GAME_TIME_TAG = "everfurnace_lastGameTime";
    @Unique private static final String NBT_VERSION_TAG    = "everfurnace_version";
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
    // tick injection
    // -------------------------------------------------------------------------

    @Inject(method = "cookTick", at = @At("HEAD"))
    private static void everFurnace_1_20_1$onCookTick(Level world, BlockPos pos, BlockState state,
                                                      CampfireBlockEntity blockEntity, CallbackInfo ci) {

        if (!EverFurnaceConfig.COMMON.catchupEnabled.get()) return;

        CampfireBlockEntityMixin  mixin    = (CampfireBlockEntityMixin)(Object) blockEntity;
        ICampfireBlockEntityMixin accessor = (ICampfireBlockEntityMixin)(Object) blockEntity;

        long currentGameTime   = world.getGameTime();
        long localLastGameTime = mixin.everFurnace_1_20_1$lastGameTime;

        mixin.everFurnace_1_20_1$lastGameTime = currentGameTime;

        if (localLastGameTime == 0L) return;

        long deltaTime = currentGameTime - localLastGameTime;
        if (deltaTime < EverFurnaceConfig.COMMON.minDeltaThreshold.get()) return;

        deltaTime = Math.min(deltaTime, EverFurnaceConfig.COMMON.maxCatchupTicks.get());

        NonNullList<ItemStack> items = blockEntity.getItems();
        int[] cookingProgress = accessor.getCookingProgress();
        int[] cookingTime     = accessor.getCookingTime();

        boolean anyCompleted = false;

        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isEmpty()) continue;

            int total = cookingTime[i];
            if (total <= 0) continue;

            int remaining = total - cookingProgress[i];
            if (deltaTime >= remaining) {
                // Push to total; the vanilla body's ++/threshold check completes it this tick.
                cookingProgress[i] = total;
                anyCompleted = true;
            } else {
                cookingProgress[i] += (int) deltaTime;
            }
        }

        if (anyCompleted && world instanceof ServerLevel serverLevel) {
            ModNetwork.sendCatchupParticles(serverLevel, pos);
        }
    }
}
