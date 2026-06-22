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

        /**
         * per-block toggle for brewing stand catch-up. brewing potions offline
         * (especially with automated, hopper-fed stands) has a different balance
         * profile than smelting, so it can be disabled independently while leaving
         * furnace/campfire catch-up on. requires the master {@code catchupEnabled}
         * to also be true.
         */
        public final ForgeConfigSpec.BooleanValue brewingStandCatchupEnabled;

        // ── Player notifications ──────────────────────────────────────────────

        /**
         * when {@code true}, a chat message is sent to the first player who
         * opens a furnace after it has processed items during an offline or
         * unloaded period.
         *
         * @see EverFurnaceConfig.Common#notifyPlayerOnCatchup
         */
        public final ForgeConfigSpec.BooleanValue notifyPlayerOnCatchup;

        /**
         * minimum game-time ticks that must have passed since the last
         * notification was sent before a new one is armed.
         *
         * <p>items cooked during the cooldown period are <em>always</em>
         * accumulated into {@code everfurnace_pendingNotification} — the
         * cooldown only controls whether the notification timer resets,
         * preventing rapid chunk-load/unload cycles from spamming chat.
         *
         * <p>set to {@code 0} to disable the cooldown entirely (every
         * catch-up pass that cooks items will arm a notification).
         * default: {@code 200} ticks (10 seconds). Range: 0 – 72 000.
         */
        public final ForgeConfigSpec.LongValue notificationCooldownTicks;

        /**
         * when {@code true}, queued notifications are also delivered on player
         * login, not only when the player manually opens the furnace.
         *
         * <p>useful on multiplayer servers where catch-up fires on chunk load
         * before the owning player has connected. without this option the
         * notification sits in NBT until the player physically opens each furnace.
         */
        public final ForgeConfigSpec.BooleanValue notifyOnLogin;


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

            brewingStandCatchupEnabled = builder
                    .comment("Per-block toggle for brewing stand catch-up.",
                            "Brewing potions offline (especially with automated, hopper-fed stands)",
                            "has a different balance profile than smelting, so it can be disabled",
                            "independently while leaving furnace/campfire catch-up on.",
                            "Requires the master 'catchupEnabled' to also be true.",
                            "Default: true")
                    .define("brewingStandCatchupEnabled", true);

            builder.pop().push("notifications");

            notifyPlayerOnCatchup = builder
                    .comment("Send a chat message to the player when they open a furnace that",
                            "cooked items while they were away. Requires Feature B to be implemented.")
                    .define("notifyPlayerOnCatchup", true);

            notificationCooldownTicks = builder
                    .comment("Minimum ticks between notification arms for a single furnace.",
                            "Items cooked during the cooldown are still counted — they are batched",
                            "into the existing pending notification rather than dropped.",
                            "Set to 0 to disable the cooldown (notify on every catch-up pass).",
                            "Range: 0 – 72 000  |  Default: 200 (10 seconds)")
                    .defineInRange("notificationCooldownTicks", 200L, 0L, 72_000L);

            notifyOnLogin = builder
                    .comment("Deliver pending furnace notifications when the player logs in,",
                            "in addition to when they open a furnace.",
                            "Recommended on multiplayer servers where catch-up may fire before",
                            "the owning player has connected.",
                            "Default: true")
                    .define("notifyOnLogin", true);

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

        /**
         * play a furnace crackle sound at the furnace position when catch-up
         * completes and at least one item was cooked.
         * client-side only — no effect on dedicated servers.
         */
        public final ForgeConfigSpec.BooleanValue soundCueEnabled;

        /**
         * snap the furnace block's LIT state on the client immediately after
         * catch-up completes, ensuring the visual light level reflects the
         * post-catch-up state without waiting for the next server sync.
         * C=client-side only — no effect on dedicated servers.
         */
        public final ForgeConfigSpec.BooleanValue lightFlickerEnabled;

        Client(ForgeConfigSpec.Builder builder) {
            builder.comment("EverFurnace — Client-side configuration")
                    .push("visuals");

            particleBurstEnabled = builder
                    .comment("Spawn a flame/smoke particle burst at a furnace when catch-up completes",
                            "and at least one item was cooked. Client-side only — no effect on servers.")
                    .define("particleBurstEnabled", true);

            soundCueEnabled = builder
                    .comment("Play a furnace crackle sound when catch-up completes",
                            "and at least one item was cooked.")
                    .define("soundCueEnabled", true);

            lightFlickerEnabled = builder
                    .comment("Immediately sync the furnace LIT block state on the client",
                            "when catch-up completes, so the light level updates without",
                            "waiting for the next server block update.")
                    .define("lightFlickerEnabled", true);

            builder.pop();
        }
    }
}