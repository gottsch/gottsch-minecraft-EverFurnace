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
 * Created by Mark Gottschling on 12/9/2024
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class ModFurnaceBlockEntityMixin extends BaseContainerBlockEntity implements WorldlyContainer, RecipeHolder, StackedContentsCompatible {

    @Unique
    private static final int INPUT_SLOT = 0;
    @Unique
    private static final int FUEL_SLOT = 1;
    @Unique
    private static final int OUTPUT_SLOT = 2;
    @Unique
    private static final String LAST_GAME_TIME_TAG = "everfurnace_lastGameTime";

    @Unique
    private long everFurnace_1_20_1$lastGameTime;

    protected ModFurnaceBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void onSave(CompoundTag tag, CallbackInfo ci) {
        tag.putLong(LAST_GAME_TIME_TAG, this.everFurnace_1_20_1$lastGameTime);
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void onLoad(CompoundTag tag, CallbackInfo ci) {
        this.everFurnace_1_20_1$lastGameTime = tag.getLong(LAST_GAME_TIME_TAG);
    }

    /**
     * a simple mixin that executes at the beginning of the Furnace's (BlastFurnace, Smoker) tick event.
     * @param world
     * @param pos
     * @param state
     * @param blockEntity
     * @param ci
     */
    @Inject(method = "serverTick", at = @At("HEAD")) // target more specifically somewhere closer to the actual calculations?
    private static void onTick(Level world, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci) {
        // cast block entity as a mixin block entity
        ModFurnaceBlockEntityMixin blockEntityMixin = (ModFurnaceBlockEntityMixin)(Object) blockEntity;

        // record last world time
        long localLastGameTime = blockEntityMixin.everFurnace_1_20_1$getLastGameTime();
        blockEntityMixin.everFurnace_1_20_1$setLastGameTime(blockEntity.getLevel().getGameTime());

        if (!blockEntity.isLit()){
            return;
        }

        // calculate the difference between game time and the lastGameTime
        long deltaTime = blockEntity.getLevel().getGameTime() - localLastGameTime;

        // exit if not enough time has passed
        if (deltaTime < 20) {
            return;
        }

        /*
         * //////////////////////
         * validations
         * //////////////////////
         */
        ItemStack cookStack = blockEntity.items.get(INPUT_SLOT);
        if (cookStack.isEmpty()) return;

        // get the output stack
        ItemStack outputStack = blockEntity.items.get(OUTPUT_SLOT);
        // return if it is already maxed out
        if (!outputStack.isEmpty() && outputStack.getCount() == blockEntity.getMaxStackSize()) return;

        // test if can accept recipe output
        Recipe<?> recipe = blockEntity.quickCheck.getRecipeFor(blockEntity, world).orElse(null);
        if (!blockEntity.canBurn(world.registryAccess(), recipe, blockEntity.items, blockEntity.getMaxStackSize())) return;
        /////////////////////////

        /*
         * begin processing
         */
        // calculate totalBurnTimeRemaining
        ItemStack fuelStack = blockEntity.items.get(FUEL_SLOT);
        if (fuelStack.isEmpty()) return;
        long totalBurnTimeRemaining = (long) (fuelStack.getCount() - 1) * blockEntity.litDuration + blockEntity.litTime;

        // calculate totalCookTimeRemaining
        long totalCookTimeRemaining = (long) (cookStack.getCount() -1) * blockEntity.cookingTotalTime + (blockEntity.cookingTotalTime - blockEntity.cookingProgress);

        // determine the max amount of time that can be used before one or both input run out.
        long maxInputTime = Math.min(totalBurnTimeRemaining, totalCookTimeRemaining);

        /*
         * determine  the actual max time that can be applied to processing. ie if elapsed time is < maxInputTime,
         * then only the elapse time can be used.
         */
        long actualAppliedTime = Math.min(deltaTime, maxInputTime);

        if (actualAppliedTime < blockEntity.litDuration) {
            // reduce burn time
            blockEntity.litTime =- (int) actualAppliedTime;
            if (blockEntity.litTime <= 0) {
//                Item fuelItem = fuelStack.getItem();
                // reduce the size of the fuel stack
                fuelStack.shrink(1);
                if (fuelStack.isEmpty()) {
                    blockEntity.litTime = 0;
                    blockEntity.items.set(1, fuelStack.getCraftingRemainingItem());
                } else {
                    blockEntity.litTime =+ blockEntity.litDuration;
                }
            }
        } else {
            int quotient = (int) (Math.floor((double) actualAppliedTime / blockEntity.litDuration));
            long remainder = actualAppliedTime % blockEntity.litDuration;
            // reduced stack by quotient
//            Item fuelItem = fuelStack.getItem();
            fuelStack.shrink(quotient);
            // reduce litTime by remainder
            blockEntity.litTime =- (int)remainder;
            if (blockEntity.litTime <= 0) {
                // reduce the size of the fuel stack
                fuelStack.shrink(1);
            }
            if (fuelStack.isEmpty()) {
                blockEntity.litTime = 0;
                blockEntity.items.set(1, fuelStack.getCraftingRemainingItem());
            } else {
                blockEntity.litTime =+ blockEntity.litDuration;
            }
        }

        if (actualAppliedTime < blockEntity.cookingTotalTime) {
            // increment cook time
            blockEntity.cookingProgress =+ (int) actualAppliedTime;
            if (blockEntity.cookingProgress >= blockEntity.cookingTotalTime) {
                if (blockEntity.burn(world.registryAccess(), recipe, blockEntity.items, blockEntity.getMaxStackSize())) {
                    blockEntity.setRecipeUsed(recipe);
                }
                if (cookStack.isEmpty()) {
                    blockEntity.cookingProgress = 0;
                    blockEntity.cookingTotalTime = 0;
                } else {
                    blockEntity.cookingTotalTime -= blockEntity.cookingTotalTime;
                }
            }
        }
        // actual applied time is greated that cook time total,
        // there, need to apply a factor of
        else {
            int quotient = (int) (Math.floor((double) actualAppliedTime / blockEntity.cookingTotalTime));
            long remainder = actualAppliedTime % blockEntity.cookingTotalTime;
            // reduced stack by quotient
            boolean isSuccessful = false;
            for (int iterations = 0; iterations < quotient; iterations++) {
                isSuccessful |= blockEntity.burn(world.registryAccess(), recipe, blockEntity.items, blockEntity.getMaxStackSize());
            }
            // update last recipe
            if (isSuccessful) blockEntity.setRecipeUsed(recipe);

            // increment cook time
            blockEntity.cookingProgress =+ (int) remainder;
            if (blockEntity.cookingProgress >= blockEntity.cookingTotalTime) {
                if (blockEntity.burn(world.registryAccess(), recipe, blockEntity.items, blockEntity.getMaxStackSize())) {
                    blockEntity.setRecipeUsed(recipe);
                }
                if (cookStack.isEmpty()) {
                    blockEntity.cookingProgress = 0;
                    blockEntity.cookingTotalTime = 0;
                } else {
                    blockEntity.cookingTotalTime -= blockEntity.cookingTotalTime;
                }
            }
        }

        if(!blockEntity.isLit()) {
            state = state.setValue(AbstractFurnaceBlock.LIT, blockEntity.isLit());
            world.setBlock(pos, state, 3);
            AbstractFurnaceBlockEntity.setChanged(world, pos, state);
        }
    }

    @Unique
    public long everFurnace_1_20_1$getLastGameTime() {
        return this.everFurnace_1_20_1$lastGameTime;
    }

    @Unique
    public void everFurnace_1_20_1$setLastGameTime(long gameTime) {
        this.everFurnace_1_20_1$lastGameTime = gameTime;
    }

}
