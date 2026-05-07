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

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import mod.gottsch.forge.everfurnace.core.config.EverFurnaceConfig;
import mod.gottsch.forge.everfurnace.core.furnace.IEverFurnaceBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin/debug commands for EverFurnace.
 *
 * <p>/everfurnace inspect [x y z]    — show EverFurnace NBT state for the furnace
 *                                      at the player's feet (or given coordinates).
 *
 * <p>/everfurnace tick &lt;radius&gt;       — call serverTick on every loaded furnace within
 *                                      radius blocks; the mixin catch-up logic fires
 *                                      immediately. Combine with simulate for instant results.
 *
 * <p>/everfurnace simulate &lt;radius&gt; &lt;ticks&gt;
 *                                    — backdate lastGameTime by ticks on every loaded
 *                                      furnace within radius blocks; the next natural
 *                                      serverTick (or an explicit tick command) will
 *                                      see the full simulated delta and apply catch-up.
 *
 * @author Mark Gottschling on 4/28/2026
 */
public class EverFurnaceCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("everfurnace")
                .requires(source -> source.hasPermission(2))

                // /everfurnace simulate <radius> <ticks>
                .then(Commands.literal("simulate")
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                        .then(Commands.argument("ticks", LongArgumentType.longArg(1))
                            .executes(ctx -> simulate(
                                    ctx.getSource(),
                                    IntegerArgumentType.getInteger(ctx, "radius"),
                                    LongArgumentType.getLong(ctx, "ticks"))))))

                // /everfurnace tick <radius>
                .then(Commands.literal("tick")
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                        .executes(ctx -> tick(
                                ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "radius")))))

                // /everfurnace inspect  /  /everfurnace inspect <x> <y> <z>
                .then(Commands.literal("inspect")
                    .executes(ctx -> inspect(ctx.getSource(), null))
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> inspect(
                                ctx.getSource(),
                                BlockPosArgument.getLoadedBlockPos(ctx, "pos")))))
        );
    }

    // ------------------------------------------------------------------
    // /everfurnace simulate <radius> <ticks>
    // ------------------------------------------------------------------

    private static int simulate(CommandSourceStack source, int radius, long ticks)
            throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();
        BlockPos origin = player.blockPosition();

        List<BlockPos> positions = furnacePositionsInRadius(level, origin, radius);

        int count = 0;
        for (BlockPos pos : positions) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AbstractFurnaceBlockEntity furnace) {
                IEverFurnaceBlockEntity mixin = (IEverFurnaceBlockEntity)(Object) furnace;
                long current = mixin.everFurnace_1_20_1$getLastGameTime();
                if (current > 0L) {
                    mixin.everFurnace_1_20_1$setLastGameTime(current - ticks);
                    furnace.setChanged();
                    count++;
                }
            }
        }

        double minutes = ticks / 1200.0;
        final int result = count;
        source.sendSuccess(() -> Component.literal(
                String.format("Backdated %d furnace(s) by %d ticks (%.1f min) within %d blocks. " +
                              "Use '/everfurnace tick <radius>' to apply catch-up immediately.",
                        result, ticks, minutes, radius))
                .withStyle(ChatFormatting.GREEN), false);
        return count;
    }

    // ------------------------------------------------------------------
    // /everfurnace tick <radius>
    // ------------------------------------------------------------------

    private static int tick(CommandSourceStack source, int radius)
            throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();
        BlockPos origin = player.blockPosition();

        // Snapshot positions before triggering any ticks; serverTick calls setChanged()
        // which could theoretically disturb in-progress chunk iteration.
        List<BlockPos> positions = furnacePositionsInRadius(level, origin, radius);

        int triggered = 0;
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AbstractFurnaceBlockEntity furnace) {
                AbstractFurnaceBlockEntity.serverTick(level, pos, state, furnace);
                triggered++;
            }
        }

        final int result = triggered;
        source.sendSuccess(() -> Component.literal(
                String.format("Triggered serverTick for %d furnace(s) within %d blocks.",
                        result, radius))
                .withStyle(ChatFormatting.GREEN), false);
        return triggered;
    }

    // ------------------------------------------------------------------
    // /everfurnace inspect [x y z]
    // ------------------------------------------------------------------

    private static int inspect(CommandSourceStack source, BlockPos targetPos)
            throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        BlockPos pos = targetPos != null
                ? targetPos
                : source.getPlayerOrException().blockPosition();

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AbstractFurnaceBlockEntity furnace)) {
            source.sendSuccess(() -> Component.literal(
                    "No furnace block entity at " + pos.toShortString() + " in "
                    + level.dimension().location())
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        IEverFurnaceBlockEntity mixin = (IEverFurnaceBlockEntity)(Object) furnace;
        long now          = level.getGameTime();
        long lastGameTime = mixin.everFurnace_1_20_1$getLastGameTime();
        long delta        = now - lastGameTime;
        long threshold    = EverFurnaceConfig.COMMON.minDeltaThreshold.get();
        boolean wouldTrigger = lastGameTime > 0L && delta >= threshold;

        source.sendSuccess(() -> Component.literal(
                "=== EverFurnace inspect @ " + pos.toShortString() + " ===")
                .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal(
                "  lastGameTime         : " + lastGameTime
                + "  (delta: " + delta + " ticks / "
                + String.format("%.1f", delta / 1200.0) + " min)")
                .withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal(
                "  pendingNotification  : " + mixin.everFurnace_1_20_1$getPendingNotification())
                .withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal(
                "  lastNotificationTime : " + mixin.everFurnace_1_20_1$getLastNotificationTime())
                .withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal(
                "  pendingXp            : " + String.format("%.2f", mixin.everFurnace_1_20_1$getPendingXp()))
                .withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal(
                "  catch-up next tick?  : " + (wouldTrigger ? "YES" : "no")
                + "  (need delta>=" + threshold + ")")
                .withStyle(wouldTrigger ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // Helper — snapshot loaded furnace positions within a block radius
    // ------------------------------------------------------------------

    /**
     * Collects the {@link BlockPos} of every loaded {@link AbstractFurnaceBlockEntity}
     * within {@code radius} blocks of {@code origin} into a new list.
     *
     * <p>Returning a snapshot (rather than a live view) lets callers iterate and
     * mutate block entities without risking concurrent-modification errors.
     */
    private static List<BlockPos> furnacePositionsInRadius(
            ServerLevel level, BlockPos origin, int radius) {
        List<BlockPos> result = new ArrayList<>();
        long radiusSq = (long) radius * radius;
        int minCx = (origin.getX() - radius) >> 4;
        int maxCx = (origin.getX() + radius) >> 4;
        int minCz = (origin.getZ() - radius) >> 4;
        int maxCz = (origin.getZ() + radius) >> 4;

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!(level.getChunkSource().getChunkNow(cx, cz) instanceof LevelChunk)) continue;
                LevelChunk chunk = (LevelChunk)level.getChunk(cx, cz);
                for (var entry : chunk.getBlockEntities().entrySet()) {
                    if (entry.getValue() instanceof AbstractFurnaceBlockEntity
                            && entry.getKey().distSqr(origin) <= radiusSq) {
                        result.add(entry.getKey());
                    }
                }
            }
        }
        return result;
    }
}
