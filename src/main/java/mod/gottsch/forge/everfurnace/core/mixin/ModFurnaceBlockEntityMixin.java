package mod.gottsch.forge.everfurnace.core.mixin;

import mod.gottsch.forge.everfurnace.core.config.EverFurnaceConfig;
import mod.gottsch.forge.everfurnace.core.furnace.ModFurnaceBlockEntityInterface;
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

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class ModFurnaceBlockEntityMixin extends BaseContainerBlockEntity implements ModFurnaceBlockEntityInterface, WorldlyContainer, RecipeHolder, StackedContentsCompatible {

    // -------------------------------------------------------------------------
    // constants
    // -------------------------------------------------------------------------

    @Unique private static final int INPUT_SLOT  = 0;
    @Unique private static final int FUEL_SLOT   = 1;
    @Unique private static final int OUTPUT_SLOT = 2;

    // NBT keys
    @Unique private static final String LAST_GAME_TIME_TAG          = "everfurnace_lastGameTime";
    @Unique private static final String PENDING_NOTIFICATION_TAG    = "everfurnace_pendingNotification";
    @Unique private static final String LAST_NOTIFICATION_TIME_TAG  = "everfurnace_lastNotificationTime";
    @Unique private static final String PENDING_XP_TAG              = "everfurnace_pendingXp";

    // NBT version — increment when the on-disk format changes.
    @Unique private static final int    CURRENT_NBT_VERSION         = 1;
    @Unique private static final String NBT_VERSION_TAG             = "everfurnace_version";

    /**
     * maximum ticks of catch-up we will simulate in a single call (1 in-game day).
     * overridden at runtime by {@code EverFurnaceConfig.COMMON.maxCatchupTicks}.
     */
    @Unique private static final long MAX_CATCHUP_TICKS = 24_000L;

    // -------------------------------------------------------------------------
    // Instance fields
    // -------------------------------------------------------------------------

    /** game-time (ticks) recorded at the end of the previous onTick call. */
    @Unique private long everFurnace_1_20_1$lastGameTime;

    /**
     * items cooked during catch-up that have not yet been reported to the player.
     * persisted in NBT so counts survive chunk unload/reload cycles.
     */
    @Unique private int  everFurnace_1_20_1$pendingNotification;

    /**
     * game-time of the last notification arm. used to enforce the configurable
     * cooldown so rapid chunk-load/unload cycles cannot spam chat.
     * a value of {@code 0} means no notification has ever been armed on this furnace.
     */
    @Unique private long everFurnace_1_20_1$lastNotificationTime;

    /**
     * XP owed to the next player who opens this furnace after a catch-up pass.
     * stored as a float so fractional XP values from recipes accumulate correctly
     * across multiple cook cycles. the integer part is awarded via
     * {@code player.giveExperiencePoints()}; the remainder is written back into
     * this field and carries over to the next award.
     */
    @Unique private float everFurnace_1_20_1$pendingXp;

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
        tag.putInt  (NBT_VERSION_TAG,            CURRENT_NBT_VERSION);
        tag.putLong (LAST_GAME_TIME_TAG,         this.everFurnace_1_20_1$lastGameTime);
        tag.putInt  (PENDING_NOTIFICATION_TAG,   this.everFurnace_1_20_1$pendingNotification);
        tag.putLong (LAST_NOTIFICATION_TIME_TAG, this.everFurnace_1_20_1$lastNotificationTime);
        tag.putFloat(PENDING_XP_TAG,             this.everFurnace_1_20_1$pendingXp);
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void onLoad(CompoundTag tag, CallbackInfo ci) {
        // all get* calls return 0 when the tag is absent — correct default for
        // every field. the first-tick guard handles lastGameTime == 0.
        // v1 saves lack PENDING_XP_TAG; getFloat returns 0f naturally — no migration needed.
        this.everFurnace_1_20_1$lastGameTime         = tag.getLong (LAST_GAME_TIME_TAG);
        this.everFurnace_1_20_1$pendingNotification  = tag.getInt  (PENDING_NOTIFICATION_TAG);
        this.everFurnace_1_20_1$lastNotificationTime = tag.getLong (LAST_NOTIFICATION_TIME_TAG);
        this.everFurnace_1_20_1$pendingXp            = tag.getFloat(PENDING_XP_TAG);
    }

    // -------------------------------------------------------------------------
    // tick injection
    // -------------------------------------------------------------------------

    /**
     * injected at the HEAD of AbstractFurnaceBlockEntity#serverTick so we can
     * simulate time that passed while the chunk was unloaded or the server was
     * offline.
     *
     * <p>key design decisions:
     * <ul>
     *   <li>lastGameTime is always updated, even when the furnace is unlit, so
     *       that re-lighting the furnace does not trigger a spurious catch-up.</li>
     *   <li>On a brand-new furnace (lastGameTime == 0) we initialise the
     *       timestamp and return without doing any catch-up work.</li>
     *   <li>deltaTime is capped at maxCatchupTicks (config) to prevent lag spikes.</li>
     *   <li>catch-up is skipped entirely when deltaTime < minDeltaThreshold (config).</li>
     * </ul>
     */
    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void onTick(Level world, BlockPos pos, BlockState state,
                               AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci) {

        if (!EverFurnaceConfig.COMMON.catchupEnabled.get()) return;

        ModFurnaceBlockEntityMixin mixin = (ModFurnaceBlockEntityMixin)(Object) blockEntity;

        long currentGameTime   = blockEntity.getLevel().getGameTime();
        long localLastGameTime = mixin.everFurnace_1_20_1$getLastGameTime();

        mixin.everFurnace_1_20_1$setLastGameTime(currentGameTime);

        if (localLastGameTime == 0L) return;

        long deltaTime = currentGameTime - localLastGameTime;
        if (deltaTime < EverFurnaceConfig.COMMON.minDeltaThreshold.get()) return;

        if (!blockEntity.isLit()) return;

        long maxCatchup = EverFurnaceConfig.COMMON.maxCatchupTicks.get();
        deltaTime = Math.min(deltaTime, maxCatchup);

        // ── Pre-checks ───────────────────────────────────────────────────────

        ItemStack cookStack = blockEntity.items.get(INPUT_SLOT);
        if (cookStack.isEmpty()) return;

        ItemStack outputStack = blockEntity.items.get(OUTPUT_SLOT);
        if (!outputStack.isEmpty() && outputStack.getCount() == blockEntity.getMaxStackSize()) return;

        Recipe<?> recipe = blockEntity.quickCheck.getRecipeFor(blockEntity, world).orElse(null);
        if (!blockEntity.canBurn(world.registryAccess(), recipe, blockEntity.items, blockEntity.getMaxStackSize())) return;

        ItemStack fuelStack = blockEntity.items.get(FUEL_SLOT);
        if (fuelStack.isEmpty()) return;

        // ── How much virtual time can we consume? ────────────────────────────

        long totalBurnTimeRemaining = (long)(fuelStack.getCount() - 1) * blockEntity.litDuration
                + blockEntity.litTime;
        long totalCookTimeRemaining = (long)(cookStack.getCount() - 1) * blockEntity.cookingTotalTime
                + (blockEntity.cookingTotalTime - blockEntity.cookingProgress);

        long maxApplicableTime = Math.min(totalBurnTimeRemaining, totalCookTimeRemaining);
        long actualAppliedTime = Math.min(deltaTime, maxApplicableTime);

        if (actualAppliedTime <= 0) return;

        // ── Consume fuel ─────────────────────────────────────────────────────

        applyFuelTime(blockEntity, fuelStack, actualAppliedTime);

        // ── Advance cooking ──────────────────────────────────────────────────

        CookResult result = applyCookTime(world, blockEntity, recipe, cookStack, actualAppliedTime);

        // ── Notification bookkeeping (Feature B + J) ─────────────────────────

        if (result.itemsCooked() > 0 && EverFurnaceConfig.COMMON.notifyPlayerOnCatchup.get()) {
            mixin.everFurnace_1_20_1$pendingNotification += result.itemsCooked();

            long cooldown  = EverFurnaceConfig.COMMON.notificationCooldownTicks.get();
            long lastArmed = mixin.everFurnace_1_20_1$getLastNotificationTime();

            if (cooldown == 0 || lastArmed == 0 || (currentGameTime - lastArmed) >= cooldown) {
                mixin.everFurnace_1_20_1$setLastNotificationTime(currentGameTime);
            }
        }

        // ── XP bookkeeping (Feature F) ───────────────────────────────────────

        if (result.xpEarned() > 0f) {
            mixin.everFurnace_1_20_1$pendingXp += result.xpEarned();
        }

        // ── Particle burst (Feature D) ───────────────────────────────────────

        if (result.itemsCooked() > 0) {
            ModNetwork.CHANNEL.send(
                    PacketDistributor.NEAR.with(() ->
                            new PacketDistributor.TargetPoint(
                                    pos.getX(), pos.getY(), pos.getZ(), 32, world.dimension())),
                    new CatchupParticlePacket(pos));
        }

        // ── Sync ─────────────────────────────────────────────────────────────

        AbstractFurnaceBlockEntity.setChanged(world, pos, state);

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
     */
    @Unique
    private static void applyFuelTime(AbstractFurnaceBlockEntity blockEntity,
                                      ItemStack fuelStack, long ticks) {
        long totalConsumed = ticks;
        int  litDuration   = blockEntity.litDuration;

        if (totalConsumed <= blockEntity.litTime) {
            blockEntity.litTime -= (int) totalConsumed;
        } else {
            totalConsumed -= blockEntity.litTime;
            blockEntity.litTime = 0;

            int wholeItemsNeeded = (int) Math.ceil((double) totalConsumed / litDuration);
            wholeItemsNeeded = Math.min(wholeItemsNeeded, fuelStack.getCount());

            long ticksCoveredByNewItems = (long) wholeItemsNeeded * litDuration;
            long leftover = ticksCoveredByNewItems - totalConsumed;

            fuelStack.shrink(wholeItemsNeeded);

            if (fuelStack.isEmpty()) {
                blockEntity.litTime = 0;
                blockEntity.items.set(FUEL_SLOT, fuelStack.getCraftingRemainingItem());
            } else {
                blockEntity.litTime = (int) leftover;
            }
        }
    }

    /**
     * advances cooking progress by {@code ticks}, calling
     * {@link AbstractFurnaceBlockEntity#burn} for each item that completes.
     *
     * @return the number of items successfully cooked.
     */
    @Unique
    private static CookResult applyCookTime(Level world,
                                            AbstractFurnaceBlockEntity blockEntity,
                                            Recipe<?> recipe,
                                            ItemStack cookStack,
                                            long ticks) {
        int cookingTotalTime = blockEntity.cookingTotalTime;
        if (cookingTotalTime <= 0) return new CookResult(0, 0f);

        // XP per item — only AbstractCookingRecipe carries an XP value.
        float xpPerItem = (recipe instanceof AbstractCookingRecipe cookingRecipe)
                ? cookingRecipe.getExperience()
                : 0f;

        int   cooked    = 0;
        float xpEarned  = 0f;

        long ticksToFinishCurrent = cookingTotalTime - blockEntity.cookingProgress;

        if (ticks < ticksToFinishCurrent) {
            blockEntity.cookingProgress += (int) ticks;
        } else {
            ticks -= ticksToFinishCurrent;
            blockEntity.cookingProgress = cookingTotalTime;

            if (blockEntity.burn(world.registryAccess(), recipe, blockEntity.items, blockEntity.getMaxStackSize())) {
                blockEntity.setRecipeUsed(recipe);
                cooked++;
                xpEarned += xpPerItem;
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
                        cooked++;
                        xpEarned += xpPerItem;
                    }
                }

                blockEntity.cookingProgress = (int) remainder;
            }
        }

        return new CookResult(cooked, xpEarned);
    }

    // -------------------------------------------------------------------------
    // accessors
    // -------------------------------------------------------------------------

    @Unique
    public long everFurnace_1_20_1$getLastGameTime() {
        return this.everFurnace_1_20_1$lastGameTime;
    }

    @Unique
    public void everFurnace_1_20_1$setLastGameTime(long gameTime) {
        this.everFurnace_1_20_1$lastGameTime = gameTime;
    }

    @Unique
    public int everFurnace_1_20_1$getPendingNotification() {
        return this.everFurnace_1_20_1$pendingNotification;
    }

    @Unique
    public void everFurnace_1_20_1$setPendingNotification(int count) {
        this.everFurnace_1_20_1$pendingNotification = count;
    }

    @Unique
    public long everFurnace_1_20_1$getLastNotificationTime() {
        return this.everFurnace_1_20_1$lastNotificationTime;
    }

    @Unique
    public void everFurnace_1_20_1$setLastNotificationTime(long gameTime) {
        this.everFurnace_1_20_1$lastNotificationTime = gameTime;
    }

    @Unique
    public float everFurnace_1_20_1$getPendingXp() {
        return this.everFurnace_1_20_1$pendingXp;
    }

    @Unique
    public void everFurnace_1_20_1$setPendingXp(float xp) {
        this.everFurnace_1_20_1$pendingXp = xp;
    }
}