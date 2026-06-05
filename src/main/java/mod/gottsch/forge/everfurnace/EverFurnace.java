package mod.gottsch.forge.everfurnace;

import mod.gottsch.forge.everfurnace.api.EverFurnaceApi;
import mod.gottsch.forge.everfurnace.core.catchup.CampfireCatchupHandler;
import mod.gottsch.forge.everfurnace.core.catchup.FurnaceCatchupHandler;
import mod.gottsch.forge.everfurnace.core.command.ModCommands;
import mod.gottsch.forge.everfurnace.core.config.EverFurnaceConfig;
import mod.gottsch.forge.everfurnace.core.network.ModNetwork;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

/**
 * @author Mark Gottschling on 12/9/2024
 */
@Mod(value = EverFurnace.MOD_ID)
public class EverFurnace {
    public static final String MOD_ID = "everfurnace";

    public EverFurnace() {
        EverFurnaceConfig.register();
        ModNetwork.register();

        // Wire live config values into the API so third-party handlers (and our
        // own) can read them without importing EverFurnaceConfig directly.
        EverFurnaceApi.bindConfig(
                () -> EverFurnaceConfig.COMMON.catchupEnabled.get(),
                () -> EverFurnaceConfig.COMMON.maxCatchupTicks.get(),
                () -> EverFurnaceConfig.COMMON.minDeltaThreshold.get()
        );

        // Register built-in catch-up handlers for all vanilla cooking blocks.
        FurnaceCatchupHandler  furnaceHandler  = new FurnaceCatchupHandler();
        CampfireCatchupHandler campfireHandler = new CampfireCatchupHandler();

        EverFurnaceApi.registerHandler(BlockEntityType.FURNACE,       furnaceHandler);
        EverFurnaceApi.registerHandler(BlockEntityType.BLAST_FURNACE, furnaceHandler);
        EverFurnaceApi.registerHandler(BlockEntityType.SMOKER,        furnaceHandler);
        // Both CampfireBlock and SoulCampfireBlock share BlockEntityType.CAMPFIRE in 1.20.1.
        EverFurnaceApi.registerHandler(BlockEntityType.CAMPFIRE, campfireHandler);

        // Capability default: catch up any modded furnace that subclasses
        // AbstractFurnaceBlockEntity and reuses the vanilla ticker, even though it
        // registers its own BlockEntityType.  The exact-type registrations above
        // remain the override path; this only fills the gap they leave.  (No
        // campfire fallback: CampfireBlockEntity is final and not subclassed.)
        EverFurnaceApi.registerFallback(be -> be instanceof AbstractFurnaceBlockEntity, furnaceHandler);

        // Register admin commands on the game bus.
        MinecraftForge.EVENT_BUS.register(ModCommands.class);
    }
}
