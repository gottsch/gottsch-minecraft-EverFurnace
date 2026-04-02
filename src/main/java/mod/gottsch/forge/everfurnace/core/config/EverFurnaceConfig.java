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
 *   <li>{@link Common} — server-side tunables (catch-up behaviour,
 *       notifications). Stored in {@code config/everfurnace-common.toml}. applies in
 *       both singleplayer and multiplayer.</li>
 *   <li>{@link Client} — client-only visual settings (particle burst).
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

    public static void register() {
        ModLoadingContext ctx = ModLoadingContext.get();
        ctx.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
        ctx.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
    }

    // =========================================================================
    // Common config  (everfurnace-common.toml)
    // =========================================================================

    public static final class Common {

        public final ForgeConfigSpec.BooleanValue catchupEnabled;
        public final ForgeConfigSpec.LongValue    maxCatchupTicks;
        public final ForgeConfigSpec.IntValue     minDeltaThreshold;

        public final ForgeConfigSpec.BooleanValue notifyPlayerOnCatchup;
        public final ForgeConfigSpec.LongValue    notificationCooldownTicks;
        public final ForgeConfigSpec.BooleanValue notifyOnLogin;

        Common(ForgeConfigSpec.Builder builder) {
            builder.comment("EverFurnace — Common (server-side) configuration")
                    .push("catchup");

            catchupEnabled = builder
                    .comment("Master toggle for the catch-up mechanic.")
                    .define("catchupEnabled", true);

            maxCatchupTicks = builder
                    .comment("Maximum ticks of offline time to simulate in one catch-up pass.",
                            "Default: 24000 (1 in-game day). Range: 1 – 192000.")
                    .defineInRange("maxCatchupTicks", 24_000L, 1L, 192_000L);

            minDeltaThreshold = builder
                    .comment("Minimum tick gap required before catch-up logic fires.",
                            "Default: 20 (1 second at normal TPS). Range: 1 – 72000.")
                    .defineInRange("minDeltaThreshold", 20, 1, 72_000);

            builder.pop().push("notifications");

            notifyPlayerOnCatchup = builder
                    .comment("Send a chat message when items are cooked offline.")
                    .define("notifyPlayerOnCatchup", true);

            notificationCooldownTicks = builder
                    .comment("Minimum ticks between notification arms for a single furnace.",
                            "Items cooked during the cooldown are batched, never dropped.",
                            "Set to 0 to disable. Range: 0 – 72000  |  Default: 200 (10 seconds)")
                    .defineInRange("notificationCooldownTicks", 200L, 0L, 72_000L);

            notifyOnLogin = builder
                    .comment("Deliver pending furnace notifications when the player logs in.",
                            "Default: true")
                    .define("notifyOnLogin", true);

            builder.pop();
        }
    }

    // =========================================================================
    // Client config  (everfurnace-client.toml)
    // =========================================================================

    public static final class Client {

        public final ForgeConfigSpec.BooleanValue particleBurstEnabled;
        public final ForgeConfigSpec.BooleanValue soundCueEnabled;
        public final ForgeConfigSpec.BooleanValue lightFlickerEnabled;

        Client(ForgeConfigSpec.Builder builder) {
            builder.comment("EverFurnace — Client-side configuration")
                    .push("visuals");

            particleBurstEnabled = builder
                    .comment("Spawn a flame/smoke particle burst when catch-up completes",
                            "and at least one item was cooked.")
                    .define("particleBurstEnabled", true);

            soundCueEnabled = builder
                    .comment("Play a furnace crackle sound when catch-up completes",
                            "and at least one item was cooked.")
                    .define("soundCueEnabled", true);

            lightFlickerEnabled = builder
                    .comment("Immediately sync the furnace LIT block state on the client",
                            "when catch-up completes.")
                    .define("lightFlickerEnabled", true);

            builder.pop();
        }
    }
}