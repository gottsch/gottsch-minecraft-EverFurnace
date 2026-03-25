/*
 * This file is part of EverFurnace.
 * Copyright (c) 2024 Mark Gottschling (gottsch)
 *
 * EverFurnace is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * EverFurnace is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with EverFurnace.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package mod.gottsch.forge.everfurnace.core.mixin;

import mod.gottsch.forge.everfurnace.core.config.EverFurnaceConfig;
import mod.gottsch.forge.everfurnace.core.network.CatchupParticlePacket;
import mod.gottsch.forge.everfurnace.core.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.RecipeHolder;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author Mark Gottschling on 12/9/2024
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class ModFurnaceBlockEntityMixin extends BaseContainerBlockEntity implements WorldlyContainer, RecipeHolder, StackedContentsCompatible {

    // -------------------------------------------------------------------------
    // constants
    // -------------------------------------------------------------------------

    @Unique private static final int INPUT_SLOT  = 0;
    @Unique private static final int FUEL_SLOT   = 1;
    @Unique private static final int OUTPUT_SLOT = 2;

    @Unique private static final String LAST_GAME_TIME_TAG      = "everfurnace_lastGameTime";
    @Unique private static final String PENDING_NOTIFICATION_TAG = "everfurnace_pendingNotification";
    @Unique private static final int    CURRENT_NBT_VERSION      = 1;
    @Unique private static final String NBT_VERSION_TAG          = "everfurnace_version";

    // -------------------------------------------------------------------------
    // instance fields
    // -------------------------------------------------------------------------

    /** the game-time (ticks) recorded at the end of the previous onTick call. */
    @Unique
    private long everFurnace_1_20_1$lastGameTime;

    // -------------------------------------------------------------------------
    // Required mixin constructor
    // -------------------------------------------------------------------------

    protected ModFurnaceBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // -------------------------------------------------------------------------
    // NBT persistence
    // -------------------------------------------------------------------------

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void onSave(CompoundTag tag, CallbackInfo ci) {
        tag.putInt(NBT_VERSION_TAG, CURRENT_NBT_VERSION);
        tag.putLong(LAST_GAME_TIME_TAG, this.everFurnace_1_20_1$lastGameTime);
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void onLoad(CompoundTag tag, CallbackInfo ci) {
        // For v0 saves, lastGameTime tag is simply absent — getLong returns 0,
        // which triggers the safe first-tick initialisation on next load.
        // No special migration needed; vanilla catch-up behaviour is preserved.
        this.everFurnace_1_20_1$lastGameTime = tag.getLong(LAST_GAME_TIME_TAG);
    }

    // -------------------------------------------------------------------------
    // tick injection
    // -------------------------------------------------------------------------

    /**
     * injected at the HEAD of AbstractFurnaceBlockEntity#serverTick so we can
     * simulate time that passed while the chunk was unloaded (or the server was
     * offline).
     *
     * <p>key design decisions:
     * <ul>
     *   <li>lastGameTime is always updated, even when the furnace is unlit, so
     *       that re-lighting the furnace does not trigger a spurious catch-up.</li>
     *   <li>On a brand-new furnace (lastGameTime == 0) we initialise the
     *       timestamp and return without doing any catch-up work.</li>
     *   <li>deltaTime is capped at MAX_CATCHUP_TICKS to prevent server hangs.</li>
     *   <li>the entire block of catch-up logic is skipped when deltaTime < 2
     *       (i.e. normal tick rate); vanilla handles that fine on its own.</li>
     * </ul>
     */
    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void onTick(Level world, BlockPos pos, BlockState state,
                               AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci) {

        // Master toggle — bail immediately if the mechanic is disabled.
        if (!EverFurnaceConfig.COMMON.catchupEnabled.get()) {
            return;
        }

        ModFurnaceBlockEntityMixin mixin = (ModFurnaceBlockEntityMixin)(Object) blockEntity;

        long currentGameTime   = blockEntity.getLevel().getGameTime();
        long localLastGameTime = mixin.everFurnace_1_20_1$getLastGameTime();

        // always advance the timestamp so unlit periods don't accumulate debt.
        mixin.everFurnace_1_20_1$setLastGameTime(currentGameTime);

        // first-ever tick for this furnace: just initialise and bail.
        if (localLastGameTime == 0L) {
            return;
        }

        // only run catch-up when there is a meaningful gap.
        // a delta under the threshold means the furnace is ticking normally (player
        // nearby, chunk loaded) — vanilla handles that fine. a delta at or above the
        // threshold indicates a chunk unload or server pause, which is when catch-up
        // should fire. the threshold is configurable but defaults to 20 (1 second).
        long deltaTime = currentGameTime - localLastGameTime;
        if (deltaTime < EverFurnaceConfig.COMMON.minDeltaThreshold.get()) {
            return;
        }

        // nothing to do if the furnace was not already lit.
        if (!blockEntity.isLit()) {
            return;
        }

        // cap delta to avoid enormous loops after long offline periods (configurable).
        deltaTime = Math.min(deltaTime, EverFurnaceConfig.COMMON.maxCatchupTicks.get());

        // ── pre-checks ──────────────────────────────────────────────────────

        ItemStack cookStack = blockEntity.items.get(INPUT_SLOT);
        if (cookStack.isEmpty()) return;

        ItemStack outputStack = blockEntity.items.get(OUTPUT_SLOT);
        if (!outputStack.isEmpty() && outputStack.getCount() == blockEntity.getMaxStackSize()) return;

        Recipe<?> recipe = blockEntity.quickCheck.getRecipeFor(blockEntity, world).orElse(null);
        if (!blockEntity.canBurn(world.registryAccess(), recipe, blockEntity.items, blockEntity.getMaxStackSize())) return;

        ItemStack fuelStack = blockEntity.items.get(FUEL_SLOT);
        if (fuelStack.isEmpty()) return;

        // ── how much virtual time can we consume? ────────────────────────────

        // total ticks of burn remaining across all fuel items currently in the slot.
        long totalBurnTimeRemaining  = (long)(fuelStack.getCount() - 1) * blockEntity.litDuration
                + blockEntity.litTime;

        // total ticks of cooking remaining across all items currently in the input slot.
        long totalCookTimeRemaining  = (long)(cookStack.getCount() - 1) * blockEntity.cookingTotalTime
                + (blockEntity.cookingTotalTime - blockEntity.cookingProgress);

        // we can only apply time up to whichever resource runs out first.
        long maxApplicableTime  = Math.min(totalBurnTimeRemaining, totalCookTimeRemaining);
        long actualAppliedTime  = Math.min(deltaTime, maxApplicableTime);

        if (actualAppliedTime <= 0) return;

        // ── consume fuel ─────────────────────────────────────────────────────

        applyFuelTime(blockEntity, fuelStack, actualAppliedTime);

        // ── advance cooking ──────────────────────────────────────────────────

        int itemsCooked = applyCookTime(world, blockEntity, recipe, cookStack, actualAppliedTime);

        // store pending notification count so the event handler can deliver it
        // when a player next opens this furnace.
        if (itemsCooked > 0 && EverFurnaceConfig.COMMON.notifyPlayerOnCatchup.get()) {
            CompoundTag persistTag = blockEntity.getPersistentData();
            int existing = persistTag.getInt(PENDING_NOTIFICATION_TAG);
            persistTag.putInt(PENDING_NOTIFICATION_TAG, existing + itemsCooked);
        }

        boolean anythingCooked = itemsCooked > 0;

        // ── sync block state / dirty flag ────────────────────────────────────

        // always mark dirty when we changed inventory or progress.
        AbstractFurnaceBlockEntity.setChanged(world, pos, state);

        // notify nearby clients to spawn a particle burst if anything was cooked.
        if (anythingCooked) {
            ModNetwork.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.NEAR.with(
                            () -> new net.minecraftforge.network.PacketDistributor.TargetPoint(
                                    pos.getX(), pos.getY(), pos.getZ(), 32, world.dimension())),
                    new CatchupParticlePacket(pos)
            );
        }

        // update the LIT block-state if the furnace has gone out.
        if (!blockEntity.isLit()) {
            BlockState newState = state.setValue(AbstractFurnaceBlock.LIT, false);
            world.setBlock(pos, newState, 3);
            AbstractFurnaceBlockEntity.setChanged(world, pos, newState);
        }
    }

    // -------------------------------------------------------------------------
    // private helpers
    // -------------------------------------------------------------------------

    /**
     * removes fuel items to account for {@code ticks} of burn time, updating
     * {@code blockEntity.litTime} accordingly.
     *
     * <p>we keep the logic linear: figure out how many whole fuel items are
     * consumed by {@code ticks}, then handle the remainder inside the current
     * item being burned.
     */
    @Unique
    private static void applyFuelTime(AbstractFurnaceBlockEntity blockEntity,
                                      ItemStack fuelStack, long ticks) {

        // The current item being burned has litTime ticks left.
        // We subtract ticks from that first, carrying over into additional items.

        long totalConsumed = ticks;
        int litDuration    = blockEntity.litDuration;

        // how much does the current burning item cover?
        if (totalConsumed <= blockEntity.litTime) {
            // still within the current item — just subtract.
            blockEntity.litTime -= (int) totalConsumed;
        } else {
            // exhaust the current item.
            totalConsumed -= blockEntity.litTime;
            blockEntity.litTime = 0;

            // shrink fuel for every additional full item consumed.
            int wholeItemsNeeded = (int) Math.ceil((double) totalConsumed / litDuration);
            // guard against over-shrinking.
            wholeItemsNeeded = Math.min(wholeItemsNeeded, fuelStack.getCount());

            long ticksCoveredByNewItems = (long) wholeItemsNeeded * litDuration;
            long leftover = ticksCoveredByNewItems - totalConsumed; // ticks remaining in last new item

            fuelStack.shrink(wholeItemsNeeded);

            if (fuelStack.isEmpty()) {
                blockEntity.litTime = 0;
                blockEntity.items.set(1, fuelStack.getCraftingRemainingItem());
            } else {
                // the last item that was opened still has 'leftover' ticks remaining.
                blockEntity.litTime = (int) leftover;
                // if for some reason litTime is 0 here, vanilla will handle re-lighting
                // from the fuel slot on its own next normal tick.
            }
        }
    }

    /**
     * advances cooking progress by {@code ticks}, calling
     * {@link AbstractFurnaceBlockEntity#burn} for each item that completes.
     *
     * @return {@code true} if at least one item was cooked successfully.
     */
    @Unique
    private static int applyCookTime(Level world,
                                     AbstractFurnaceBlockEntity blockEntity,
                                     Recipe<?> recipe,
                                     ItemStack cookStack,
                                     long ticks) {

        int cookingTotalTime = blockEntity.cookingTotalTime;
        if (cookingTotalTime <= 0) return 0;

        int itemsCooked = 0;

        // ticks remaining until the current item in progress finishes.
        long ticksToFinishCurrent = cookingTotalTime - blockEntity.cookingProgress;

        if (ticks < ticksToFinishCurrent) {
            // didn't finish even the current item — just advance progress.
            blockEntity.cookingProgress += (int) ticks;
        } else {
            // finish the current in-progress item.
            ticks -= ticksToFinishCurrent;
            blockEntity.cookingProgress = cookingTotalTime;

            if (blockEntity.burn(world.registryAccess(), recipe, blockEntity.items, blockEntity.getMaxStackSize())) {
                blockEntity.setRecipeUsed(recipe);
                itemsCooked++;
            }
            blockEntity.cookingProgress = 0;

            if (!cookStack.isEmpty() && cookingTotalTime > 0) {
                long additionalItems = ticks / cookingTotalTime;
                long remainder       = ticks % cookingTotalTime;

                for (long i = 0; i < additionalItems; i++) {
                    if (!blockEntity.canBurn(world.registryAccess(), recipe,
                            blockEntity.items, blockEntity.getMaxStackSize())) {
                        break;
                    }
                    if (blockEntity.burn(world.registryAccess(), recipe,
                            blockEntity.items, blockEntity.getMaxStackSize())) {
                        blockEntity.setRecipeUsed(recipe);
                        itemsCooked++;
                    }
                }

                blockEntity.cookingProgress = (int) remainder;
            }
        }

        return itemsCooked;
    }

    // -------------------------------------------------------------------------
    // accessors (required by Mixin @Unique convention)
    // -------------------------------------------------------------------------

    @Unique
    public long everFurnace_1_20_1$getLastGameTime() {
        return this.everFurnace_1_20_1$lastGameTime;
    }

    @Unique
    public void everFurnace_1_20_1$setLastGameTime(long gameTime) {
        this.everFurnace_1_20_1$lastGameTime = gameTime;
    }
}