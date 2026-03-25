package mod.gottsch.forge.everfurnace.core.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Forge configuration for EverFurnace.
 *
 * <p>two config types are used:
 * <ul>
 *   <li>{@link EverFurnaceConfig.Common} — server-side tunables (catch-up behaviour,
 *       notifications). Stored in {@code config/everfurnace-common.toml}. applies in
 *       both singleplayer and multiplayer.</li>
 *   <li>{@link EverFurnaceConfig.Client} — client-only visual settings (particle burst).
 *       stored in {@code config/everfurnace-client.toml}. ignored on dedicated servers.</li>
 * </ul>
 *
 * <p>usage from the mixin or event handlers:
 * <pre>{@code
 *   if (EverFurnaceConfig.COMMON.catchupEnabled.get()) { ... }
 *   if (EverFurnaceConfig.CLIENT.particleBurstEnabled.get()) { ... }
 * }</pre>
 *
 * @author by Mark Gottschling on 3/23/2026
 */

public final class EverFurnaceConfig {

    // -------------------------------------------------------------------------
    // public config instances (populated during mod construction)
    // -------------------------------------------------------------------------

    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        final Pair<Common, ForgeConfigSpec> commonPair =
                new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON      = commonPair.getLeft();
        COMMON_SPEC = commonPair.getRight();

        final Pair<Client, ForgeConfigSpec> clientPair =
                new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT      = clientPair.getLeft();
        CLIENT_SPEC = clientPair.getRight();
    }

    private EverFurnaceConfig() {}

    /**
     * call this from {@link EverFurnace#EverFurnace()} (the mod constructor)
     * to register both config specs with Forge.
     */
    public static void register() {
        ModLoadingContext ctx = ModLoadingContext.get();
        ctx.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
        ctx.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
    }

    // =========================================================================
    // common (server-side) config
    // =========================================================================

    public static final class Common {

        // ── catch-up behaviour ────────────────────────────────────────────────

        /**
         * master on/off switch for the entire catch-up mechanic.
         * when {@code false}, EverFurnace's {@code onTick} injection returns
         * immediately after updating {@code lastGameTime}, leaving all smelting
         * to vanilla.
         */
        public final ForgeConfigSpec.BooleanValue catchupEnabled;

        /**
         * maximum number of ticks that will be simulated in a single catch-up
         * call. defaults to 24 000 (one full in-game day). raise this if you
         * want longer offline periods to be fully simulated; lower it if you
         * are seeing tick-time spikes on chunk load.
         *
         * <p>minimum enforced value: 1. maximum enforced value: 192 000
         * (8 in-game days).
         */
        public final ForgeConfigSpec.LongValue maxCatchupTicks;

        /**
         * minimum tick gap between the stored {@code lastGameTime} and the
         * current game time before catch-up logic runs. at the default of
         * {@code 20}, any gap of less than (normal server operation) is
         * ignored and left to vanilla. increase this if you only want catch-up
         * to fire after genuinely large gaps (e.g. set to {@code 100} to
         * require a 5-second gap before simulating).
         */
        public final ForgeConfigSpec.IntValue minDeltaThreshold;

        // ── Player notifications ──────────────────────────────────────────────

        /**
         * when {@code true}, a chat message is sent to the first player who
         * opens a furnace after it has processed items during an offline or
         * unloaded period.
         *
         * @see EverFurnaceConfig.Common#notifyPlayerOnCatchup
         */
        public final ForgeConfigSpec.BooleanValue notifyPlayerOnCatchup;

        Common(ForgeConfigSpec.Builder builder) {
            builder.comment("EverFurnace — Common (server-side) configuration")
                    .push("catchup");

            catchupEnabled = builder
                    .comment("Master toggle for the catch-up mechanic.",
                            "Set to false to disable EverFurnace entirely and let vanilla handle all smelting.")
                    .define("catchupEnabled", true);

            maxCatchupTicks = builder
                    .comment("Maximum ticks of offline time to simulate in one catch-up pass.",
                            "Default: 24000 (1 in-game day). Range: 1 – 192000.")
                    .defineInRange("maxCatchupTicks", 24_000L, 1L, 192_000L);

            minDeltaThreshold = builder
                    .comment("Minimum tick gap required before catch-up logic fires.",
                            "Default: 20 (1 second at normal TPS). Below this the furnace is considered",
                            "actively ticking and vanilla handles smelting. Raise to require a larger gap.")
                    .defineInRange("minDeltaThreshold", 20, 1, 72_000);

            builder.pop().push("notifications");

            notifyPlayerOnCatchup = builder
                    .comment("Send a chat message to the player when they open a furnace that",
                            "cooked items while they were away. Requires Feature B to be implemented.")
                    .define("notifyPlayerOnCatchup", true);

            builder.pop();
        }
    }

    // =========================================================================
    // client config
    // =========================================================================

    public static final class Client {

        /**
         * when {@code true}, a brief burst of flame and smoke particles is
         * spawned at the furnace position on the client whenever catch-up
         * completes and at least one item was cooked. pure cosmetic — has no
         * effect on a dedicated server.
         */
        public final ForgeConfigSpec.BooleanValue particleBurstEnabled;

        Client(ForgeConfigSpec.Builder builder) {
            builder.comment("EverFurnace — Client-side configuration")
                    .push("visuals");

            particleBurstEnabled = builder
                    .comment("Spawn a flame/smoke particle burst at a furnace when catch-up completes",
                            "and at least one item was cooked. Client-side only — no effect on servers.")
                    .define("particleBurstEnabled", true);

            builder.pop();
        }
    }
}