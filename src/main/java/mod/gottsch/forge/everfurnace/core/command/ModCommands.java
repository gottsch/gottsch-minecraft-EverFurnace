/*
 * This file is part of EverFurnace.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
 *
 * Licensed under the MIT License. See LICENSE.txt in the project root
 * for the full license text.
 *
 * SPDX-License-Identifier: MIT
 */
package mod.gottsch.forge.everfurnace.core.command;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Registers EverFurnace admin commands on the Forge game event bus.
 *
 * <p>Must be registered in {@code EverFurnace} constructor:
 * <pre>{@code
 * MinecraftForge.EVENT_BUS.register(ModCommands.class);
 * }</pre>
 *
 * @author Mark Gottschling on 4/28/2026
 */
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        EverFurnaceCommand.register(event.getDispatcher());
    }
}
