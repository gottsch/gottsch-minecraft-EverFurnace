/*
 * This file is part of EverFurnace.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
 *
 * Licensed under the MIT License. See LICENSE.txt in the project root
 * for the full license text.
 *
 * SPDX-License-Identifier: MIT
 */
package mod.gottsch.forge.everfurnace.core.event;

import mod.gottsch.forge.everfurnace.core.config.EverFurnaceConfig;
import mod.gottsch.forge.everfurnace.core.furnace.IEverFurnaceBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * @author Mark Gottschling on 3/24/2026
 */
@Mod.EventBusSubscriber(modid = mod.gottsch.forge.everfurnace.EverFurnace.MOD_ID)
public class FurnaceEventHandler {

    // -------------------------------------------------------------------------
    // Feature B + F — deliver notification and award XP when a furnace is opened
    // -------------------------------------------------------------------------

    /**
     * Fires whenever a player opens any container. When it is a furnace:
     * <ul>
     *   <li>If pending notification count is non-zero, sends a chat message
     *       and clears it (Feature B).</li>
     *   <li>If pending XP is non-zero, awards the integer portion as experience
     *       and writes the fractional remainder back (Feature F).</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        if (!(event.getContainer() instanceof AbstractFurnaceMenu furnaceMenu)) return;
        if (!(furnaceMenu.container instanceof AbstractFurnaceBlockEntity furnace)) return;

        boolean dirty = false;

        // ── Feature B — chat notification ────────────────────────────────────
        if (EverFurnaceConfig.COMMON.notifyPlayerOnCatchup.get()) {
            int count = consumePendingNotification(furnace);
            if (count > 0) {
                sendNotification(player,
                        blockDisplayName(player.level(), furnace.getBlockPos()), count);
                dirty = true;
            }
        }

        // ── Feature F — XP award ─────────────────────────────────────────────
        dirty |= awardPendingXp(player, furnace);

        if (dirty) {
            furnace.setChanged();
        }
    }

    // -------------------------------------------------------------------------
    // Feature I — deliver notifications on player login
    // -------------------------------------------------------------------------

    /**
     * Fires when a player completes login. Scans all furnace block entities in
     * currently ticking chunks and aggregates any pending catch-up notifications
     * into a single summary message.
     *
     * <p>XP is intentionally <em>not</em> awarded here — the player hasn't
     * interacted with the furnace yet, so awarding XP on login would feel
     * disconnected. XP delivery remains gated on furnace open via
     * {@link #onContainerOpen}.
     *
     * <p>After delivery the pending notification counts are cleared, so
     * {@link #onContainerOpen} will not double-deliver the same message.
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!EverFurnaceConfig.COMMON.notifyPlayerOnCatchup.get()) return;
        if (!EverFurnaceConfig.COMMON.notifyOnLogin.get()) return;

        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        int furnaceCount = 0;
        int totalItems   = 0;
        // Remembered only for the single-furnace case, where naming the block is
        // unambiguous. Mixed types are reported generically (see sendMultiNotification).
        BlockPos singlePos = null;

        for (ChunkHolder holder : serverLevel.getChunkSource().chunkMap.getChunks()) {
            LevelChunk chunk = holder.getTickingChunk();
            if (chunk == null) continue;

            for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                if (!(entry.getValue() instanceof AbstractFurnaceBlockEntity furnace)) continue;

                int count = consumePendingNotification(furnace);
                if (count <= 0) continue;

                furnace.setChanged();
                furnaceCount++;
                totalItems += count;
                singlePos = entry.getKey();
            }
        }

        if (totalItems <= 0) return;

        if (furnaceCount == 1) {
            sendNotification(player, blockDisplayName(serverLevel, singlePos), totalItems);
        } else {
            sendMultiNotification(player, furnaceCount, totalItems);
        }
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    /**
     * Reads the pending notification count directly from the mixin instance
     * field and clears it. Does NOT call {@code setChanged()} — the caller is
     * responsible for dirtying the block entity after all mutations are done.
     *
     * <p>Reading from the instance field (rather than persistent NBT) ensures
     * we see the current value even if the chunk has not been saved since the
     * last catch-up pass.
     *
     * @return the item count that was pending, or {@code 0} if none.
     */
    private static int consumePendingNotification(AbstractFurnaceBlockEntity furnace) {
        IEverFurnaceBlockEntity mixin = (IEverFurnaceBlockEntity) (Object) furnace;
        int count = mixin.everFurnace_1_20_1$getPendingNotification();
        if (count <= 0) return 0;
        mixin.everFurnace_1_20_1$setPendingNotification(0);
        return count;
    }

    /**
     * Awards any pending catch-up XP to the player.
     *
     * <p>The integer portion of {@code pendingXp} is awarded via
     * {@link Player#giveExperiencePoints}. The fractional remainder is written
     * back so it accumulates correctly across multiple cook cycles with
     * per-item XP values that are not whole numbers (e.g. iron ore = 0.7 XP).
     *
     * @return {@code true} if the field was modified and the block entity
     *         should be marked dirty.
     */
    private static boolean awardPendingXp(Player player, AbstractFurnaceBlockEntity furnace) {
        IEverFurnaceBlockEntity mixin = (IEverFurnaceBlockEntity) (Object) furnace;
        float pending = mixin.everFurnace_1_20_1$getPendingXp();
        if (pending <= 0f) return false;

        int   toAward   = (int) pending;
        float remainder = pending - toAward;

        if (toAward > 0) {
            player.giveExperiencePoints(toAward);
        }

        mixin.everFurnace_1_20_1$setPendingXp(remainder);
        return true;
    }

    /**
     * The localized display name of the cooking block at {@code pos}, for use as a
     * message argument.
     *
     * <p>{@code Block.getName()} returns a {@code translatable} component, so a
     * smoker reads "Smoker", a blast furnace "Blast Furnace", and a modded furnace
     * picked up by the {@code AbstractFurnaceBlockEntity} fallback yields its own
     * name — no hardcoded per-block mapping needed, and each player sees it in
     * their own language.
     *
     * <p>Pending notifications are persisted in block-entity NBT, and the login
     * path resolves them by position, so the block may no longer be a furnace by
     * the time it is read (broken or replaced while offline). Fall back to a
     * generic noun rather than naming whatever now occupies the space.
     */
    private static Component blockDisplayName(Level level, BlockPos pos) {
        if (pos == null) {
            return Component.translatable("message.everfurnace.generic_furnace");
        }
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof AbstractFurnaceBlock
                ? state.getBlock().getName()
                : Component.translatable("message.everfurnace.generic_furnace");
    }

    /** The orange "[EverFurnace] " brand tag. Intentionally not translated. */
    private static MutableComponent prefix() {
        return Component.literal("[EverFurnace] ")
                .withStyle(style -> style.withColor(0xFFA500));
    }

    private static Component count(int value) {
        return Component.literal(String.valueOf(value))
                .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true));
    }

    /**
     * Sends the standard single-block catch-up notification to a player.
     * Example: {@code [EverFurnace] Your Smoker cooked 32 items while you were away.}
     */
    private static void sendNotification(Player player, Component blockName, int count) {
        player.sendSystemMessage(prefix().append(Component.translatable(
                        count == 1 ? "message.everfurnace.cooked.single.one"
                                   : "message.everfurnace.cooked.single.many",
                        blockName, count(count))
                .withStyle(style -> style.withColor(ChatFormatting.WHITE))));
    }

    /**
     * Sends the aggregated multi-block catch-up notification.
     *
     * <p>Deliberately generic: a login sweep can aggregate a furnace, a smoker and a
     * blast furnace into one message, and no single block name would be correct.
     */
    private static void sendMultiNotification(Player player, int furnaceCount, int totalItems) {
        player.sendSystemMessage(prefix().append(Component.translatable(
                        furnaceCount == 1 ? "message.everfurnace.cooked.multi.one"
                                          : "message.everfurnace.cooked.multi.many",
                        count(furnaceCount), count(totalItems))
                .withStyle(style -> style.withColor(ChatFormatting.WHITE))));
    }
}