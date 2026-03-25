package mod.gottsch.forge.everfurnace.core.event;

import mod.gottsch.forge.everfurnace.core.config.EverFurnaceConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * @author Mark Gottschling on 3/24/2026
 */
@Mod.EventBusSubscriber(modid = mod.gottsch.forge.everfurnace.EverFurnace.MOD_ID)
public class FurnaceEventHandler {

    private static final String PENDING_NOTIFICATION_TAG = "everfurnace_pendingNotification";

    /**
     * fired on the server when a player opens any container. if the container
     * is a furnace menu and its block entity has a pending catch-up notification,
     * send the player a simple count message and clear the tag.
     */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!EverFurnaceConfig.COMMON.notifyPlayerOnCatchup.get()) return;

        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        // abstractFurnaceMenu holds the furnace container (the block entity) directly.
        if (!(event.getContainer() instanceof AbstractFurnaceMenu furnaceMenu)) return;

        // the container field on AbstractFurnaceMenu is the block entity itself.
        if (!(furnaceMenu.container instanceof AbstractFurnaceBlockEntity furnace)) return;

        CompoundTag persistTag = furnace.getPersistentData();
        int count = persistTag.getInt(PENDING_NOTIFICATION_TAG);
        if (count <= 0) return;

        persistTag.remove(PENDING_NOTIFICATION_TAG);
        furnace.setChanged();

        player.sendSystemMessage(
                Component.literal("[EverFurnace] ")
                        .withStyle(style -> style.withColor(0xFFA500))  // orange
                        .append(Component.literal("Your furnace cooked ")
                                .withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                        .append(Component.literal(String.valueOf(count))
                                .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)))  // gold, bold
                        .append(Component.literal((count == 1 ? " item" : " items") + " while you were away.")
                                .withStyle(style -> style.withColor(ChatFormatting.WHITE)))
        );
    }
}
