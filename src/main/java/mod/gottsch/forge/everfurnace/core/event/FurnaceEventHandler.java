package mod.gottsch.forge.everfurnace.core.event;

import mod.gottsch.forge.everfurnace.core.config.EverFurnaceConfig;
import mod.gottsch.forge.everfurnace.core.furnace.IEverFurnaceBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * delivers offline-cooking notifications and pending XP to players.
 *
 * <p>registered on the Forge game event bus in {@code EverFurnace}:
 * <pre>{@code
 * MinecraftForge.EVENT_BUS.register(FurnaceEventHandler.class);
 * }</pre>
 *
 * <p><b>access transformer required</b> — add to
 * {@code src/main/resources/META-INF/accesstransformer.cfg}:
 * <pre>
 * public net.minecraft.server.level.ChunkMap getChunks()Ljava/lang/Iterable;
 * </pre>
 *
 * @author by Mark Gottschling on 3/31/2026
 */
public class FurnaceEventHandler {

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!EverFurnaceConfig.COMMON.notifyPlayerOnCatchup.get()) return;

        AbstractFurnaceBlockEntity furnace = resolveFurnace(event.getContainer());
        if (furnace == null) return;

        deliverFurnaceRewards(player, furnace);
    }

    /**
     * returns the {@link AbstractFurnaceBlockEntity} backing {@code menu} via
     * slot 0's container, or {@code null} if it is not a furnace menu.
     *
     * <p>in 1.19.2 the three furnace menu classes are
     * {@code FurnaceMenu}, {@code BlastFurnaceMenu}, and {@code SmokerMenu};
     * all store the block entity as the {@code WorldlyContainer} in slot 0.
     */
    private static AbstractFurnaceBlockEntity resolveFurnace(AbstractContainerMenu menu) {
        // Slot 0 of every vanilla furnace menu holds the block entity directly.
        if (menu.slots.isEmpty()) return null;
        var container = menu.getSlot(0).container;
        return container instanceof AbstractFurnaceBlockEntity be ? be : null;
    }

    /**
     * delivers any pending notification and XP for {@code furnace} to
     * {@code player}, then clears both fields.
     */
    public static void deliverFurnaceRewards(ServerPlayer player,
                                             AbstractFurnaceBlockEntity furnace) {
        IEverFurnaceBlockEntity mixin =
                (IEverFurnaceBlockEntity)(Object) furnace;

        // --- Notification (Feature B) ---
        int pending = mixin.everFurnace_1_19_2$getPendingNotification();
        if (pending > 0) {
            player.sendSystemMessage(singleFurnaceMessage(pending));
            mixin.everFurnace_1_19_2$setPendingNotification(0);
        }

        // --- XP (Feature F) ---
        float pendingXp = mixin.everFurnace_1_19_2$getPendingXp();
        if (pendingXp > 0f) {
            int whole = (int) pendingXp;
            float remainder = pendingXp - whole;
            if (whole > 0) player.giveExperiencePoints(whole);
            mixin.everFurnace_1_19_2$setPendingXp(remainder);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!EverFurnaceConfig.COMMON.notifyOnLogin.get()) return;
        if (!EverFurnaceConfig.COMMON.notifyPlayerOnCatchup.get()) return;

        ServerLevel level = (ServerLevel) player.getLevel();
        ChunkMap chunkMap = level.getChunkSource().chunkMap;

        int furnaceCount = 0;
        int totalCooked  = 0;

        for (ChunkHolder holder : chunkMap.getChunks()) {
            LevelChunk chunk = holder.getTickingChunk();
            if (chunk == null) continue;

            for (var be : chunk.getBlockEntities().values()) {
                if (!(be instanceof AbstractFurnaceBlockEntity furnace)) continue;

                IEverFurnaceBlockEntity mixin =
                        (IEverFurnaceBlockEntity)(Object) furnace;

                int pending = mixin.everFurnace_1_19_2$getPendingNotification();
                if (pending <= 0) continue;

                furnaceCount++;
                totalCooked += pending;
                mixin.everFurnace_1_19_2$setPendingNotification(0);

                float pendingXp = mixin.everFurnace_1_19_2$getPendingXp();
                if (pendingXp > 0f) {
                    int whole = (int) pendingXp;
                    float remainder = pendingXp - whole;
                    if (whole > 0) player.giveExperiencePoints(whole);
                    mixin.everFurnace_1_19_2$setPendingXp(remainder);
                }
            }
        }

        if (furnaceCount == 1) {
            player.sendSystemMessage(singleFurnaceMessage(totalCooked));
        } else if (furnaceCount > 1) {
            player.sendSystemMessage(multiFurnaceMessage(furnaceCount, totalCooked));
        }
    }

    // -------------------------------------------------------------------------
    // message builders
    // -------------------------------------------------------------------------

    private static Component singleFurnaceMessage(int itemCount) {
        return Component.literal("[EverFurnace] ")
                .withStyle(style -> style.withColor(0xFFA500))
                .append(Component.literal("Your furnace cooked ")
                        .withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                .append(Component.literal(String.valueOf(itemCount))
                        .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)))
                .append(Component.literal(itemCount == 1 ? " item" : " items")
                        .withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                .append(Component.literal(" while you were away.")
                        .withStyle(style -> style.withColor(ChatFormatting.WHITE)));
    }

    private static Component multiFurnaceMessage(int furnaceCount, int totalItems) {
        return Component.literal("[EverFurnace] ")
                .withStyle(style -> style.withColor(0xFFA500))
                .append(Component.literal(String.valueOf(furnaceCount))
                        .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)))
                .append(Component.literal(" furnaces cooked a combined ")
                        .withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                .append(Component.literal(String.valueOf(totalItems))
                        .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)))
                .append(Component.literal(" items while you were away.")
                        .withStyle(style -> style.withColor(ChatFormatting.WHITE)));
    }
}