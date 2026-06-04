package mod.gottsch.forge.everfurnace.core.mixin;

import mod.gottsch.forge.everfurnace.api.EverFurnaceApi;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.RecipeHolder;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.level.Level;
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
 * Timing infrastructure for EverFurnace catch-up on vanilla furnace variants.
 *
 * <p>This mixin is responsible for exactly three things:
 * <ol>
 *   <li>Persisting {@code lastGameTime} in NBT so the elapsed gap survives
 *       chunk unloads and server restarts.</li>
 *   <li>Computing {@code deltaTime} at the head of each server tick.</li>
 *   <li>Delegating to {@link EverFurnaceApi} to invoke the registered
 *       {@link mod.gottsch.forge.everfurnace.api.CookingCatchupHandler}.</li>
 * </ol>
 *
 * <p>All catch-up logic (fuel math, cook math, XP, notifications, particles,
 * dirty-marking) lives in
 * {@link mod.gottsch.forge.everfurnace.core.catchup.FurnaceCatchupHandler}.
 *
 * @author Mark Gottschling
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class EverFurnaceBlockEntityMixin extends BaseContainerBlockEntity
        implements IEverFurnaceBlockEntityMixin, WorldlyContainer, RecipeHolder, StackedContentsCompatible {

    // -------------------------------------------------------------------------
    // NBT keys
    // -------------------------------------------------------------------------

    @Unique private static final String LAST_GAME_TIME_TAG          = "everfurnace_lastGameTime";
    @Unique private static final String PENDING_NOTIFICATION_TAG    = "everfurnace_pendingNotification";
    @Unique private static final String LAST_NOTIFICATION_TIME_TAG  = "everfurnace_lastNotificationTime";
    @Unique private static final String PENDING_XP_TAG              = "everfurnace_pendingXp";
    @Unique private static final int    CURRENT_NBT_VERSION         = 1;
    @Unique private static final String NBT_VERSION_TAG             = "everfurnace_version";

    // -------------------------------------------------------------------------
    // Instance fields
    // -------------------------------------------------------------------------

    /** Game-time recorded at the end of the previous tick. */
    @Unique private long  everFurnace_1_20_1$lastGameTime;

    /** Items cooked during catch-up that have not yet been reported to the player. */
    @Unique private int   everFurnace_1_20_1$pendingNotification;

    /** Game-time of the last notification arm; {@code 0} means never armed. */
    @Unique private long  everFurnace_1_20_1$lastNotificationTime;

    /** XP owed to the next player who opens this furnace. */
    @Unique private float everFurnace_1_20_1$pendingXp;

    // -------------------------------------------------------------------------
    // Required mixin constructor
    // -------------------------------------------------------------------------

    protected EverFurnaceBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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
        this.everFurnace_1_20_1$lastGameTime         = tag.getLong (LAST_GAME_TIME_TAG);
        this.everFurnace_1_20_1$pendingNotification  = tag.getInt  (PENDING_NOTIFICATION_TAG);
        this.everFurnace_1_20_1$lastNotificationTime = tag.getLong (LAST_NOTIFICATION_TIME_TAG);
        this.everFurnace_1_20_1$pendingXp            = tag.getFloat(PENDING_XP_TAG);
    }

    // -------------------------------------------------------------------------
    // Tick injection — timing only; logic delegated to FurnaceCatchupHandler
    // -------------------------------------------------------------------------

    /**
     * Injected at the HEAD of {@code AbstractFurnaceBlockEntity#serverTick}.
     *
     * <p>Responsibilities:
     * <ul>
     *   <li>Update {@code lastGameTime} every tick (even when unlit) so that
     *       re-lighting does not trigger a spurious catch-up.</li>
     *   <li>Skip on the very first tick ({@code lastGameTime == 0}).</li>
     *   <li>Skip when {@code deltaTime} is below the configured threshold.</li>
     *   <li>Cap {@code deltaTime} at the configured maximum.</li>
     *   <li>Delegate to {@link EverFurnaceApi#findHandler} — the handler does
     *       all cooking math, side-effects, and dirty-marking.</li>
     * </ul>
     */
    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void onTick(Level world, BlockPos pos, BlockState state,
                                AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci) {

        if (!EverFurnaceApi.isCatchupEnabled()) return;

        EverFurnaceBlockEntityMixin mixin = (EverFurnaceBlockEntityMixin)(Object) blockEntity;

        long currentGameTime   = blockEntity.getLevel().getGameTime();
        long localLastGameTime = mixin.everFurnace_1_20_1$lastGameTime;

        mixin.everFurnace_1_20_1$lastGameTime = currentGameTime;

        if (localLastGameTime == 0L) return;

        long deltaTime = currentGameTime - localLastGameTime;
        if (deltaTime < EverFurnaceApi.getMinDeltaThreshold()) return;

        deltaTime = Math.min(deltaTime, EverFurnaceApi.getMaxCatchupTicks());

        final long finalDelta = deltaTime;
        EverFurnaceApi.findHandler(blockEntity)
                .ifPresent(handler -> handler.applyCatchup(blockEntity, finalDelta, (ServerLevel) world, pos));
    }

    // -------------------------------------------------------------------------
    // Accessors (used by FurnaceCatchupHandler and FurnaceEventHandler)
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
