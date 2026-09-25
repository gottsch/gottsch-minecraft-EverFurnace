/*
 * This file is part of EverFurnace.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
 *
 * Licensed under the MIT License. See LICENSE.txt in the project root
 * for the full license text.
 *
 * SPDX-License-Identifier: MIT
 */
package mod.gottsch.forge.everfurnace.core.furnace;

/**
 * @author by Mark Gottschling on 3/29/2026
 */
public interface IEverFurnaceBlockEntity {

    public long everFurnace_1_20_1$getLastGameTime();
    public void everFurnace_1_20_1$setLastGameTime(long gameTime);
    public int everFurnace_1_20_1$getPendingNotification();
    public void everFurnace_1_20_1$setPendingNotification(int count);
    public long everFurnace_1_20_1$getLastNotificationTime();
    public void everFurnace_1_20_1$setLastNotificationTime(long gameTime);
    public float everFurnace_1_20_1$getPendingXp();
    public void everFurnace_1_20_1$setPendingXp(float xp);
}