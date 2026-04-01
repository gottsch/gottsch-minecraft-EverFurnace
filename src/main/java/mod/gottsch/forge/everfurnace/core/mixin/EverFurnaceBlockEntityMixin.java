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
import mod.gottsch.forge.everfurnace.core.furnace.IEverFurnaceBlockEntity;
import mod.gottsch.forge.everfurnace.core.network.CatchupParticlePacket;
import mod.gottsch.forge.everfurnace.core.network.ModNetwork;
import mod.gottsch.forge.everfurnace.core.util.CookResult;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.RecipeHolder;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Created by Mark Gottschling on 12/9/2024
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class EverFurnaceBlockEntityMixin extends BaseContainerBlockEntity
        implements IEverFurnaceBlockEntity, WorldlyContainer, RecipeHolder, StackedContentsCompatible {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    @Unique private static final int INPUT_SLOT  = 0;
    @Unique private static final int FUEL_SLOT   = 1;
    @Unique private static final int OUTPUT_SLOT = 2;

    @Unique private static final String LAST_GAME_TIME_TAG          = "everfurnace_lastGameTime";
    @Unique private static final String PENDING_NOTIFICATION_TAG    = "everfurnace_pendingNotification";
    @Unique private static final String LAST_NOTIFICATION_TIME_TAG  = "everfurnace_lastNotificationTime";
    @Unique private static final String PENDING_XP_TAG              = "everfurnace_pendingXp";

    @Unique private static final int    CURRENT_NBT_VERSION         = 2;
    @Unique private static final String NBT_VERSION_TAG             = "everfurnace_version";

    // -------------------------------------------------------------------------
    // Instance fields
    // -------------------------------------------------------------------------

    @Unique private long  everFurnace_1_19_2$lastGameTime;
    @Unique private int   everFurnace_1_19_2$pendingNotification;
    @Unique private long  everFurnace_1_19_2$lastNotificationTime;
    @Unique private float everFurnace_1_19_2$pendingXp;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    protected EverFurnaceBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // -------------------------------------------------------------------------
    // NBT  (1.19.2: no HolderLookup.Provider parameter)
    // -------------------------------------------------------------------------

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void onSave(CompoundTag tag, CallbackInfo ci) {
        tag.putInt  (NBT_VERSION_TAG,            CURRENT_NBT_VERSION);
        tag.putLong (LAST_GAME_TIME_TAG,         this.everFurnace_1_19_2$lastGameTime);
        tag.putInt  (PENDING_NOTIFICATION_TAG,   this.everFurnace_1_19_2$pendingNotification);
        tag.putLong (LAST_NOTIFICATION_TIME_TAG, this.everFurnace_1_19_2$lastNotificationTime);
        tag.putFloat(PENDING_XP_TAG,             this.everFurnace_1_19_2$pendingXp);
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void onLoad(CompoundTag tag, CallbackInfo ci) {
        this.everFurnace_1_19_2$lastGameTime         = tag.getLong (LAST_GAME_TIME_TAG);
        this.everFurnace_1_19_2$pendingNotification  = tag.getInt  (PENDING_NOTIFICATION_TAG);
        this.everFurnace_1_19_2$lastNotificationTime = tag.getLong (LAST_NOTIFICATION_TIME_TAG);
        this.everFurnace_1_19_2$pendingXp            = tag.getFloat(PENDING_XP_TAG);
    }

    // -------------------------------------------------------------------------
    // Tick injection
    // -------------------------------------------------------------------------

    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void onTick(Level world, BlockPos pos, BlockState state,
                               AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci) {

        if (!EverFurnaceConfig.COMMON.catchupEnabled.get()) return;

        EverFurnaceBlockEntityMixin mixin = (EverFurnaceBlockEntityMixin)(Object) blockEntity;
        IEverFurnaceBlockEntityMixin ife  = (IEverFurnaceBlockEntityMixin)(Object) blockEntity;

        long currentGameTime   = world.getGameTime();
        long localLastGameTime = mixin.everFurnace_1_19_2$getLastGameTime();

        mixin.everFurnace_1_19_2$setLastGameTime(currentGameTime);

        if (localLastGameTime == 0L) return;

        long deltaTime = currentGameTime - localLastGameTime;
        if (deltaTime < EverFurnaceConfig.COMMON.minDeltaThreshold.get()) return;

        if (!ife.callIsLit()) return;

        deltaTime = Math.min(deltaTime, EverFurnaceConfig.COMMON.maxCatchupTicks.get());

        // ------------------------------------------------------------------
        // Guard checks
        // 1.19.2: getRecipeFor takes block entity directly, no SingleRecipeInput
        // ------------------------------------------------------------------

        ItemStack cookStack = ife.getItems().get(INPUT_SLOT);
        if (cookStack.isEmpty()) return;

        ItemStack outputStack = ife.getItems().get(OUTPUT_SLOT);
        if (!outputStack.isEmpty() && outputStack.getCount() == blockEntity.getMaxStackSize()) return;

        Recipe<?> recipe = ife.getQuickCheck().getRecipeFor(blockEntity, world).orElse(null);

        if (!ife.callCanBurn(recipe, ife.getItems(),
                blockEntity.getMaxStackSize())) return;

        ItemStack fuelStack = ife.getItems().get(FUEL_SLOT);
        if (fuelStack.isEmpty()) return;

        // ------------------------------------------------------------------
        // Time budget
        // ------------------------------------------------------------------

        long totalBurnTimeRemaining = (long)(fuelStack.getCount() - 1) * ife.getLitDuration()
                + ife.getLitTime();
        long totalCookTimeRemaining = (long)(cookStack.getCount() - 1) * ife.getCookingTotalTime()
                + (ife.getCookingTotalTime() - ife.getCookingProgress());

        long maxApplicableTime = Math.min(totalBurnTimeRemaining, totalCookTimeRemaining);
        long actualAppliedTime = Math.min(deltaTime, maxApplicableTime);

        if (actualAppliedTime <= 0) return;

        // ------------------------------------------------------------------
        // Apply fuel / cooking
        // ------------------------------------------------------------------

        applyFuelTime(ife, fuelStack, actualAppliedTime);

        CookResult result = applyCookTime(blockEntity, ife, recipe, cookStack, actualAppliedTime);

        // ------------------------------------------------------------------
        // Notification bookkeeping
        // ------------------------------------------------------------------

        if (result.itemsCooked() > 0 && EverFurnaceConfig.COMMON.notifyPlayerOnCatchup.get()) {
            mixin.everFurnace_1_19_2$pendingNotification += result.itemsCooked();

            long cooldown  = EverFurnaceConfig.COMMON.notificationCooldownTicks.get();
            long lastArmed = mixin.everFurnace_1_19_2$getLastNotificationTime();

            if (cooldown == 0 || lastArmed == 0 || (currentGameTime - lastArmed) >= cooldown) {
                mixin.everFurnace_1_19_2$setLastNotificationTime(currentGameTime);
            }
        }

        // ------------------------------------------------------------------
        // XP accumulation
        // ------------------------------------------------------------------

        if (result.xpEarned() > 0f) {
            mixin.everFurnace_1_19_2$pendingXp += result.xpEarned();
        }

        // ------------------------------------------------------------------
        // Particle / sound / light packet
        // ------------------------------------------------------------------

        if (result.itemsCooked() > 0) {
            ModNetwork.CHANNEL.send(
                    PacketDistributor.NEAR.with(() ->
                            new PacketDistributor.TargetPoint(
                                    pos.getX(), pos.getY(), pos.getZ(), 32, world.dimension())),
                    new CatchupParticlePacket(pos));
        }

        // ------------------------------------------------------------------
        // Mark dirty; update LIT state if furnace burned out
        // ------------------------------------------------------------------

        AbstractFurnaceBlockEntity.setChanged(world, pos, state);

        if (!ife.callIsLit()) {
            BlockState newState = state.setValue(AbstractFurnaceBlock.LIT, false);
            world.setBlock(pos, newState, 3);
            AbstractFurnaceBlockEntity.setChanged(world, pos, newState);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    @Unique
    private static void applyFuelTime(IEverFurnaceBlockEntityMixin ife,
                                      ItemStack fuelStack, long ticks) {
        long totalConsumed = ticks;
        int  litDuration   = ife.getLitDuration();

        if (totalConsumed <= ife.getLitTime()) {
            ife.setLitTime(ife.getLitTime() - (int) totalConsumed);
        } else {
            totalConsumed -= ife.getLitTime();
            ife.setLitTime(0);

            int wholeItemsNeeded = (int) Math.ceil((double) totalConsumed / litDuration);
            wholeItemsNeeded = Math.min(wholeItemsNeeded, fuelStack.getCount());

            long ticksCoveredByNewItems = (long) wholeItemsNeeded * litDuration;
            long leftover = ticksCoveredByNewItems - totalConsumed;

            fuelStack.shrink(wholeItemsNeeded);

            if (fuelStack.isEmpty()) {
                ife.setLitTime(0);
                ife.getItems().set(FUEL_SLOT, fuelStack.getCraftingRemainingItem());
            } else {
                ife.setLitTime((int) leftover);
            }
        }
    }

    @Unique
    private static CookResult applyCookTime(AbstractFurnaceBlockEntity blockEntity,
                                            IEverFurnaceBlockEntityMixin ife,
                                            Recipe<?> recipe,
                                            ItemStack cookStack,
                                            long ticks) {
        int cookingTotalTime = ife.getCookingTotalTime();
        if (cookingTotalTime <= 0) return new CookResult(0, 0f);

        float xpPerItem = (recipe instanceof AbstractCookingRecipe cookingRecipe)
                ? cookingRecipe.getExperience()
                : 0f;

        int   cooked   = 0;
        float xpEarned = 0f;

        long ticksToFinishCurrent = cookingTotalTime - ife.getCookingProgress();

        if (ticks < ticksToFinishCurrent) {
            ife.setCookingProgress(ife.getCookingProgress() + (int) ticks);
        } else {
            ticks -= ticksToFinishCurrent;
            ife.setCookingProgress(cookingTotalTime);

            if (ife.callBurn(recipe, ife.getItems(),
                    blockEntity.getMaxStackSize())) {
                blockEntity.setRecipeUsed(recipe);
                cooked++;
                xpEarned += xpPerItem;
            }
            ife.setCookingProgress(0);

            if (!cookStack.isEmpty() && cookingTotalTime > 0) {
                long additionalItems = ticks / cookingTotalTime;
                long remainder       = ticks % cookingTotalTime;

                for (long i = 0; i < additionalItems; i++) {
                    if (!ife.callCanBurn(recipe, ife.getItems(),
                            blockEntity.getMaxStackSize())) {
                        break;
                    }
                    if (ife.callBurn(recipe, ife.getItems(),
                            blockEntity.getMaxStackSize())) {
                        blockEntity.setRecipeUsed(recipe);
                        cooked++;
                        xpEarned += xpPerItem;
                    }
                }

                ife.setCookingProgress((int) remainder);
            }
        }

        return new CookResult(cooked, xpEarned);
    }

    // -------------------------------------------------------------------------
    // IEverFurnaceBlockEntity accessors (implements contract)
    // -------------------------------------------------------------------------

    @Unique public long  everFurnace_1_19_2$getLastGameTime()               { return this.everFurnace_1_19_2$lastGameTime; }
    @Unique public void  everFurnace_1_19_2$setLastGameTime(long t)         { this.everFurnace_1_19_2$lastGameTime = t; }

    @Unique public int   everFurnace_1_19_2$getPendingNotification()        { return this.everFurnace_1_19_2$pendingNotification; }
    @Unique public void  everFurnace_1_19_2$setPendingNotification(int n)   { this.everFurnace_1_19_2$pendingNotification = n; }

    @Unique public long  everFurnace_1_19_2$getLastNotificationTime()       { return this.everFurnace_1_19_2$lastNotificationTime; }
    @Unique public void  everFurnace_1_19_2$setLastNotificationTime(long t) { this.everFurnace_1_19_2$lastNotificationTime = t; }

    @Unique public float everFurnace_1_19_2$getPendingXp()                  { return this.everFurnace_1_19_2$pendingXp; }
    @Unique public void  everFurnace_1_19_2$setPendingXp(float xp)          { this.everFurnace_1_19_2$pendingXp = xp; }
}