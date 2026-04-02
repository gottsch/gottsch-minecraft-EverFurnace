package mod.gottsch.forge.everfurnace;

import mod.gottsch.forge.everfurnace.core.config.EverFurnaceConfig;
import mod.gottsch.forge.everfurnace.core.event.FurnaceEventHandler;
import mod.gottsch.forge.everfurnace.core.network.ModNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Created by Mark Gottschling on 12/9/2024
 */
@Mod(value = EverFurnace.MOD_ID)
public class EverFurnace {
    public static final String MOD_ID = "everfurnace";

    public EverFurnace() {
        // Register configs
        EverFurnaceConfig.register();

        // Register networking on the mod event bus (FMLCommonSetupEvent fires register)
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
                (net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) ->
                        event.enqueueWork(ModNetwork::register)
        );

        // Register game-event listeners on the Forge game bus
        MinecraftForge.EVENT_BUS.register(FurnaceEventHandler.class);
    }
}
