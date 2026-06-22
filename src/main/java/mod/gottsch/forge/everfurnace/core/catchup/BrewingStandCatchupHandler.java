package mod.gottsch.forge.everfurnace.core.catchup;

import mod.gottsch.forge.everfurnace.api.CookingCatchupHandler;
import mod.gottsch.forge.everfurnace.core.config.EverFurnaceConfig;
import mod.gottsch.forge.everfurnace.core.mixin.IEverFurnaceBrewingStandMixin;
import mod.gottsch.forge.everfurnace.core.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/**
 * Built-in {@link CookingCatchupHandler} for the vanilla brewing stand.
 *
 * <p>Registered for {@code BlockEntityType.BREWING_STAND} (plus an
 * {@code instanceof BrewingStandBlockEntity} fallback) in
 * {@link mod.gottsch.forge.everfurnace.EverFurnace}.
 *
 * <h2>Why iterative simulation (not the furnace's single-burst math)</h2>
 * <p>A brew is a 400-tick cycle that consumes one blaze-powder fuel charge at the
 * start and, on completion, transforms the three bottles one step and shrinks the
 * ingredient. Each completed cycle mutates the inputs, so the only correct way to
 * fast-forward is to simulate cycle-by-cycle, re-checking {@code isBrewable} each
 * time and re-using vanilla's {@code doBrew} (so remainders, events, and the brew
 * level-event all stay correct). Without automation this completes at most the one
 * in-progress brew; hopper-fed stands catch up many cycles, bounded by fuel and
 * {@code maxCatchupTicks}.
 *
 * @author Mark Gottschling
 */
public class BrewingStandCatchupHandler implements CookingCatchupHandler {

    private static final int    BREW_TICKS      = 400;
    private static final int    FUEL_USES       = 20;
    private static final int    FUEL_SLOT       = 4;
    private static final int    INGREDIENT_SLOT = 3;
    private static final double NOTIFY_RADIUS_SQ = 32.0 * 32.0;

    @Override
    public void applyCatchup(BlockEntity blockEntity, long deltaTime, ServerLevel level, BlockPos pos) {

        if (!EverFurnaceConfig.COMMON.brewingStandCatchupEnabled.get()) return;

        BrewingStandBlockEntity       stand    = (BrewingStandBlockEntity) blockEntity;
        IEverFurnaceBrewingStandMixin accessor = (IEverFurnaceBrewingStandMixin)(Object) blockEntity;

        NonNullList<ItemStack> items = accessor.getBrewingItems();

        long    remaining      = deltaTime;
        int     brewsCompleted = 0;
        boolean changed        = false;

        // (a) finish an in-progress brew — its fuel was already spent when it started.
        int brewTime = accessor.getBrewTime();
        if (brewTime > 0) {
            boolean brewable = IEverFurnaceBrewingStandMixin.callIsBrewable(items);
            if (brewable && remaining >= brewTime) {
                remaining -= brewTime;
                accessor.setBrewTime(0);
                IEverFurnaceBrewingStandMixin.callDoBrew(level, pos, items);
                brewsCompleted++;
                changed = true;
            } else if (brewable) {
                // Window exhausted mid-brew: advance progress and keep the cached
                // ingredient consistent so vanilla doesn't cancel it next tick.
                accessor.setBrewTime(brewTime - (int) remaining);
                accessor.setIngredient(items.get(INGREDIENT_SLOT).getItem());
                stand.setChanged();
                return;
            } else {
                // No longer brewable: vanilla cancels the in-progress brew.
                accessor.setBrewTime(0);
                changed = true;
            }
        }

        // (b) subsequent full cycles, each consuming a fuel charge.
        while (remaining >= BREW_TICKS) {
            ensureFuel(accessor, items);
            if (accessor.getFuel() <= 0) break;
            if (!IEverFurnaceBrewingStandMixin.callIsBrewable(items)) break;
            accessor.setFuel(accessor.getFuel() - 1);
            remaining -= BREW_TICKS;
            IEverFurnaceBrewingStandMixin.callDoBrew(level, pos, items);
            brewsCompleted++;
            changed = true;
        }

        // (c) start a partial new brew with the leftover (<400) so timing stays seamless.
        if (remaining > 0) {
            ensureFuel(accessor, items);
            if (accessor.getFuel() > 0
                    && IEverFurnaceBrewingStandMixin.callIsBrewable(items)) {
                accessor.setFuel(accessor.getFuel() - 1);
                accessor.setBrewTime(BREW_TICKS - (int) remaining);
                accessor.setIngredient(items.get(INGREDIENT_SLOT).getItem());
                changed = true;
            }
        }

        if (changed) {
            stand.setChanged();
        }

        if (brewsCompleted > 0) {
            ModNetwork.sendCatchupParticles(level, pos);
            if (EverFurnaceConfig.COMMON.notifyPlayerOnCatchup.get()) {
                notifyNearbyPlayers(level, pos, brewsCompleted);
            }
        }
    }

    /** Mirror vanilla's refuel step: a blaze powder in the fuel slot grants 20 charges. */
    private static void ensureFuel(IEverFurnaceBrewingStandMixin accessor, NonNullList<ItemStack> items) {
        if (accessor.getFuel() > 0) return;
        ItemStack fuelStack = items.get(FUEL_SLOT);
        if (fuelStack.is(Items.BLAZE_POWDER)) {
            accessor.setFuel(FUEL_USES);
            fuelStack.shrink(1);
        }
    }

    private static void notifyNearbyPlayers(ServerLevel level, BlockPos pos, int brews) {
        Component message = brewMessage(brews);
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(cx, cy, cz) <= NOTIFY_RADIUS_SQ) {
                player.sendSystemMessage(message);
            }
        }
    }

    /** "[EverFurnace] Your brewing stand finished <n> brew(s) while you were away." */
    private static Component brewMessage(int brews) {
        return Component.literal("[EverFurnace] ")
                .withStyle(style -> style.withColor(0xFFA500))
                .append(Component.literal("Your brewing stand finished ")
                        .withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                .append(Component.literal(String.valueOf(brews))
                        .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)))
                .append(Component.literal(brews == 1 ? " brew" : " brews")
                        .withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                .append(Component.literal(" while you were away.")
                        .withStyle(style -> style.withColor(ChatFormatting.WHITE)));
    }
}
