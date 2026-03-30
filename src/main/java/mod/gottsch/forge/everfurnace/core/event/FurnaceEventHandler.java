package mod.gottsch.forge.everfurnace.core.event;

import mod.gottsch.forge.everfurnace.core.config.EverFurnaceConfig;
import mod.gottsch.forge.everfurnace.core.furnace.ModFurnaceBlockEntityInterface;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
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
                sendNotification(player, count);
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
            }
        }

        if (totalItems <= 0) return;

        if (furnaceCount == 1) {
            sendNotification(player, totalItems);
        } else {
            player.sendSystemMessage(
                    Component.literal("[EverFurnace] ")
                            .withStyle(style -> style.withColor(0xFFA500))
                            .append(Component.literal(String.valueOf(furnaceCount))
                                    .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)))
                            .append(Component.literal(" furnaces cooked a combined ")
                                    .withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                            .append(Component.literal(String.valueOf(totalItems))
                                    .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)))
                            .append(Component.literal(" items while you were away.")
                                    .withStyle(style -> style.withColor(ChatFormatting.WHITE)))
            );
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
        ModFurnaceBlockEntityInterface mixin = (ModFurnaceBlockEntityInterface) (Object) furnace;
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
        ModFurnaceBlockEntityInterface mixin = (ModFurnaceBlockEntityInterface) (Object) furnace;
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
     * Sends the standard single-furnace catch-up notification to a player.
     * Example: {@code [EverFurnace] Your furnace cooked 32 items while you were away.}
     */
    private static void sendNotification(Player player, int count) {
        player.sendSystemMessage(
                Component.literal("[EverFurnace] ")
                        .withStyle(style -> style.withColor(0xFFA500))
                        .append(Component.literal("Your furnace cooked ")
                                .withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                        .append(Component.literal(String.valueOf(count))
                                .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)))
                        .append(Component.literal((count == 1 ? " item" : " items") + " while you were away.")
                                .withStyle(style -> style.withColor(ChatFormatting.WHITE)))
        );
    }
}