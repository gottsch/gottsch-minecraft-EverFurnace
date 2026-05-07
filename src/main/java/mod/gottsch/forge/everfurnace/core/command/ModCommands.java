/*
 * This file is part of EverFurnace.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
 *
 * EverFurnace is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * EverFurnace is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with EverFurnace.  If not, see <http://www.gnu.org/licenses/lgpl>.
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
