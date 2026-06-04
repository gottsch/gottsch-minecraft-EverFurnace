package mod.gottsch.forge.everfurnace.core.catchup;

import mod.gottsch.forge.everfurnace.api.CookingCatchupHandler;
import mod.gottsch.forge.everfurnace.api.EverFurnaceApi;
import mod.gottsch.forge.everfurnace.core.config.EverFurnaceConfig;
import mod.gottsch.forge.everfurnace.core.furnace.IEverFurnaceBlockEntity;
import mod.gottsch.forge.everfurnace.core.mixin.IEverFurnaceBlockEntityMixin;
import mod.gottsch.forge.everfurnace.core.network.ModNetwork;
import mod.gottsch.forge.everfurnace.core.util.CookResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Built-in {@link CookingCatchupHandler} for vanilla furnace, blast furnace, and smoker.
 *
 * <p>One instance is registered for all three block entity types in
 * {@link mod.gottsch.forge.everfurnace.EverFurnace}.  The correct recipe type
 * (smelting / blasting / smoking) is determined at runtime from
 * {@link AbstractFurnaceBlockEntity#getType()}.
 *
 * <p>This class contains the catch-up logic that previously lived inline in
 * {@link mod.gottsch.forge.everfurnace.core.mixin.EverFurnaceBlockEntityMixin}.
 * Field access is performed through {@link IEverFurnaceBlockEntityMixin} accessors
 * so no Access Transformer entries are required for the furnace fields.
 *
 * @author Mark Gottschling
 */
public class FurnaceCatchupHandler implements CookingCatchupHandler {

    private static final int INPUT_SLOT  = 0;
    private static final int FUEL_SLOT   = 1;
    private static final int OUTPUT_SLOT = 2;

    @Override
    public void applyCatchup(BlockEntity blockEntity, long deltaTime, ServerLevel level, BlockPos pos) {

        AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) blockEntity;
        IEverFurnaceBlockEntityMixin  ife  = (IEverFurnaceBlockEntityMixin)(Object) blockEntity;
        IEverFurnaceBlockEntity      mixin = (IEverFurnaceBlockEntity)(Object) blockEntity;

        // ── Pre-checks ───────────────────────────────────────────────────────

        if (!ife.callIsLit()) return;

        NonNullList<ItemStack> items = ife.getItems();

        ItemStack cookStack = furnace.getItem(INPUT_SLOT);
        if (cookStack.isEmpty()) return;

        ItemStack outputStack = furnace.getItem(OUTPUT_SLOT);
        if (!outputStack.isEmpty() && outputStack.getCount() == furnace.getMaxStackSize()) return;

        Recipe<?> recipe = findRecipe(furnace, level);
        if (!ife.callCanBurn(level.registryAccess(), recipe, items, furnace.getMaxStackSize())) return;

        ItemStack fuelStack = furnace.getItem(FUEL_SLOT);
        if (fuelStack.isEmpty()) return;

        // ── How much virtual time can we consume? ────────────────────────────

        long totalBurnTimeRemaining = (long)(fuelStack.getCount() - 1) * ife.getLitDuration()
                + ife.getLitTime();
        long totalCookTimeRemaining = (long)(cookStack.getCount() - 1) * ife.getCookingTotalTime()
                + (ife.getCookingTotalTime() - ife.getCookingProgress());

        long maxApplicableTime = Math.min(totalBurnTimeRemaining, totalCookTimeRemaining);
        long actualAppliedTime = Math.min(deltaTime, maxApplicableTime);
        if (actualAppliedTime <= 0) return;

        // ── Consume fuel ─────────────────────────────────────────────────────

        applyFuelTime(ife, furnace, fuelStack, actualAppliedTime);

        // ── Advance cooking ──────────────────────────────────────────────────

        CookResult result = applyCookTime(level, furnace, ife, recipe, cookStack, actualAppliedTime);

        // ── Notification bookkeeping ──────────────────────────────────────────

        if (result.itemsCooked() > 0 && EverFurnaceConfig.COMMON.notifyPlayerOnCatchup.get()) {
            mixin.everFurnace_1_20_1$setPendingNotification(
                    mixin.everFurnace_1_20_1$getPendingNotification() + result.itemsCooked());

            long currentGameTime = level.getGameTime();
            long cooldown  = EverFurnaceConfig.COMMON.notificationCooldownTicks.get();
            long lastArmed = mixin.everFurnace_1_20_1$getLastNotificationTime();

            if (cooldown == 0 || lastArmed == 0 || (currentGameTime - lastArmed) >= cooldown) {
                mixin.everFurnace_1_20_1$setLastNotificationTime(currentGameTime);
            }
        }

        // ── XP bookkeeping ───────────────────────────────────────────────────

        if (result.xpEarned() > 0f) {
            mixin.everFurnace_1_20_1$setPendingXp(
                    mixin.everFurnace_1_20_1$getPendingXp() + result.xpEarned());
        }

        // ── Particle burst ───────────────────────────────────────────────────

        if (result.itemsCooked() > 0) {
            ModNetwork.sendCatchupParticles(level, pos);
        }

        // ── Mark dirty and sync LIT block state ──────────────────────────────

        furnace.setChanged();
        if (!ife.callIsLit()) {
            BlockState state = level.getBlockState(pos);
            BlockState newState = state.setValue(AbstractFurnaceBlock.LIT, false);
            level.setBlock(pos, newState, 3);
            furnace.setChanged();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Determines the correct {@link RecipeType} for this furnace variant and
     * returns the first matching recipe, or {@code null} if none.
     *
     * <p>Using the recipe manager directly (rather than the furnace's internal
     * {@code quickCheck} cache) avoids the need to expose {@code quickCheck}
     * through an accessor or AT entry.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Recipe<?> findRecipe(AbstractFurnaceBlockEntity furnace, Level level) {
        BlockEntityType<?> type = furnace.getType();
        RecipeType recipeType;
        if      (type == BlockEntityType.BLAST_FURNACE) recipeType = RecipeType.BLASTING;
        else if (type == BlockEntityType.SMOKER)        recipeType = RecipeType.SMOKING;
        else                                             recipeType = RecipeType.SMELTING;
        return (Recipe<?>) level.getRecipeManager().getRecipeFor(recipeType, furnace, level).orElse(null);
    }

    /**
     * Consumes fuel items to cover {@code ticks} of burn time, updating
     * {@code litTime} via the accessor.
     */
    private static void applyFuelTime(IEverFurnaceBlockEntityMixin ife,
                                      AbstractFurnaceBlockEntity furnace,
                                      ItemStack fuelStack,
                                      long ticks) {
        long totalConsumed = ticks;
        int  litDuration   = ife.getLitDuration();

        if (totalConsumed <= ife.getLitTime()) {
            ife.setLitTime((int)(ife.getLitTime() - totalConsumed));
        } else {
            totalConsumed -= ife.getLitTime();
            ife.setLitTime(0);

            int wholeItemsNeeded = (int) Math.ceil((double) totalConsumed / litDuration);
            wholeItemsNeeded = Math.min(wholeItemsNeeded, fuelStack.getCount());

            long ticksCoveredByNewItems = (long) wholeItemsNeeded * litDuration;
            long leftover               = ticksCoveredByNewItems - totalConsumed;

            fuelStack.shrink(wholeItemsNeeded);

            if (fuelStack.isEmpty()) {
                ife.setLitTime(0);
                furnace.setItem(FUEL_SLOT, fuelStack.getCraftingRemainingItem());
            } else {
                ife.setLitTime((int) leftover);
            }
        }
    }

    /**
     * Advances cooking progress by {@code ticks}, calling {@code burn()} for
     * each completed item.
     *
     * @return a {@link CookResult} containing the item count and XP earned
     */
    private static CookResult applyCookTime(Level world,
                                            AbstractFurnaceBlockEntity furnace,
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

        NonNullList<ItemStack> items             = ife.getItems();
        long ticksToFinishCurrent                = cookingTotalTime - ife.getCookingProgress();

        if (ticks < ticksToFinishCurrent) {
            ife.setCookingProgress(ife.getCookingProgress() + (int) ticks);
        } else {
            ticks -= ticksToFinishCurrent;
            ife.setCookingProgress(cookingTotalTime);

            if (ife.callBurn(world.registryAccess(), recipe, items, furnace.getMaxStackSize())) {
                furnace.setRecipeUsed(recipe);
                cooked++;
                xpEarned += xpPerItem;
            }
            ife.setCookingProgress(0);

            if (!cookStack.isEmpty() && cookingTotalTime > 0) {
                long additionalItems = ticks / cookingTotalTime;
                long remainder       = ticks % cookingTotalTime;

                for (long i = 0; i < additionalItems; i++) {
                    if (!ife.callCanBurn(world.registryAccess(), recipe,
                            items, furnace.getMaxStackSize())) break;
                    if (ife.callBurn(world.registryAccess(), recipe,
                            items, furnace.getMaxStackSize())) {
                        furnace.setRecipeUsed(recipe);
                        cooked++;
                        xpEarned += xpPerItem;
                    }
                }

                ife.setCookingProgress((int) remainder);
            }
        }

        return new CookResult(cooked, xpEarned);
    }
}
