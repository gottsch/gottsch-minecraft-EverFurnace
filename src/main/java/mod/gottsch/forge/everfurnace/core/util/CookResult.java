/*
 * This file is part of EverFurnace.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
 *
 * Licensed under the MIT License. See LICENSE.txt in the project root
 * for the full license text.
 *
 * SPDX-License-Identifier: MIT
 */
package mod.gottsch.forge.everfurnace.core.util;

/**
 * return value from {@code ModFurnaceBlockEntityMixin#applyCookTime}.
 * carries both the item count and raw XP accumulated in a single catch-up pass.
 *
 * @param itemsCooked number of items successfully cooked.
 * @param xpEarned    raw XP earned (may be fractional; caller is responsible
 *                    for flooring and accumulating the remainder).
 *
 * @author Mark Gottschling on 3/28/2026
 */
public record CookResult(int itemsCooked, float xpEarned) {}