package mod.gottsch.forge.everfurnace.api;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Public API entry point for EverFurnace.
 *
 * <h2>For third-party mods</h2>
 * <p>During your mod's initialisation, register a {@link CookingCatchupHandler}
 * for each custom cooking block entity type you want catch-up support for:
 * <pre>{@code
 *   EverFurnaceApi.registerHandler(MyBlockEntityTypes.COOKING_POT.get(),
 *                                   new MyCookingPotCatchupHandler());
 * }</pre>
 * Your handler is also responsible for reading {@link #isCatchupEnabled()},
 * {@link #getMaxCatchupTicks()}, and {@link #getMinDeltaThreshold()} so that
 * it respects the server-owner's EverFurnace config.
 *
 * <h2>For EverFurnace internally</h2>
 * <p>Call {@link #bindConfig} once during mod construction (after the config is
 * registered) to wire the live config values into the API.  The suppliers are
 * called on every tick, so config reloads are reflected automatically.
 *
 * @author Mark Gottschling
 */
public final class EverFurnaceApi {

    private static final Map<BlockEntityType<?>, CookingCatchupHandler> HANDLERS = new LinkedHashMap<>();

    // Capability-based fallbacks, matched in registration order when no exact-type
    // handler exists.  This restores the implicit coverage the inline mixin used to
    // give every AbstractFurnaceBlockEntity subclass before catch-up moved behind
    // this registry: exact registration is the explicit override, the fallback is
    // the structural default.  Never registration-only.
    private static final List<Map.Entry<Predicate<BlockEntity>, CookingCatchupHandler>> FALLBACKS = new ArrayList<>();

    // Defaults match the config defaults; overwritten by bindConfig() on startup.
    private static BooleanSupplier catchupEnabled    = () -> true;
    private static LongSupplier    maxCatchupTicks   = () -> 24_000L;
    private static IntSupplier     minDeltaThreshold = () -> 20;

    private EverFurnaceApi() {}

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Register a catch-up handler for a block entity type.
     *
     * <p>Call this during mod initialisation, before any block entities of the
     * given type begin ticking.  Registering a handler for a type that already
     * has one replaces the previous handler.
     *
     * @param <T>     the block entity type
     * @param type    the {@link BlockEntityType} to associate with this handler
     * @param handler the handler to invoke during catch-up ticks
     */
    public static <T extends BlockEntity> void registerHandler(BlockEntityType<T> type,
                                                               CookingCatchupHandler handler) {
        HANDLERS.put(type, handler);
    }

    /**
     * Register a capability-based fallback handler, matched when no exact-type
     * handler is registered for a block entity.
     *
     * <p>Use this to cover a whole family of block entities by structure rather
     * than by exact type — e.g. {@code be instanceof AbstractFurnaceBlockEntity}
     * to catch every modded furnace subclass that reuses the vanilla ticker.
     * Fallbacks are tested in registration order and only consulted after the
     * exact-type lookup misses, so an explicit {@link #registerHandler} always
     * overrides a fallback.
     *
     * @param predicate matches the block entities this fallback should handle
     * @param handler   the handler to invoke for matching block entities
     */
    public static void registerFallback(Predicate<BlockEntity> predicate,
                                        CookingCatchupHandler handler) {
        FALLBACKS.add(Map.entry(predicate, handler));
    }

    /**
     * Look up the handler for the given block entity.
     *
     * <p>Exact-type registrations win first (explicit override); if none match,
     * capability fallbacks are tested in registration order (structural default).
     *
     * @param be the block entity being ticked
     * @return the matching handler, or {@link Optional#empty()} if none
     */
    public static Optional<CookingCatchupHandler> findHandler(BlockEntity be) {
        CookingCatchupHandler exact = HANDLERS.get(be.getType());
        if (exact != null) {
            return Optional.of(exact);
        }
        for (Map.Entry<Predicate<BlockEntity>, CookingCatchupHandler> fallback : FALLBACKS) {
            if (fallback.getKey().test(be)) {
                return Optional.of(fallback.getValue());
            }
        }
        return Optional.empty();
    }

    // -------------------------------------------------------------------------
    // Config bridge
    // -------------------------------------------------------------------------

    /**
     * Wire live config suppliers into the API.  Called once by EverFurnace
     * during mod construction.  The suppliers are read on every tick, so
     * in-game config reloads are reflected without restarting.
     *
     * @param enabled   supplier for the master catch-up toggle
     * @param maxTicks  supplier for the maximum catch-up tick cap
     * @param minDelta  supplier for the minimum delta threshold
     */
    public static void bindConfig(BooleanSupplier enabled,
                                  LongSupplier maxTicks,
                                  IntSupplier minDelta) {
        catchupEnabled    = enabled;
        maxCatchupTicks   = maxTicks;
        minDeltaThreshold = minDelta;
    }

    /** @return {@code true} if the catch-up mechanic is globally enabled. */
    public static boolean isCatchupEnabled()     { return catchupEnabled.getAsBoolean(); }

    /** @return the maximum ticks of offline time to simulate in one catch-up pass. */
    public static long    getMaxCatchupTicks()   { return maxCatchupTicks.getAsLong(); }

    /** @return the minimum tick gap before catch-up logic fires. */
    public static int     getMinDeltaThreshold() { return minDeltaThreshold.getAsInt(); }
}
