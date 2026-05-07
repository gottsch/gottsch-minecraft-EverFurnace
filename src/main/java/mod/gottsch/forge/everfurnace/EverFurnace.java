package mod.gottsch.forge.everfurnace;

import mod.gottsch.forge.everfurnace.core.command.ModCommands;
import mod.gottsch.forge.everfurnace.core.config.EverFurnaceConfig;
import mod.gottsch.forge.everfurnace.core.network.ModNetwork;
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

        // Register admin commands (inspect / tick / simulate) on the game bus.
        MinecraftForge.EVENT_BUS.register(ModCommands.class);
    }
}
