package mod.gottsch.forge.everfurnace.core.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor / invoker interface for the private brewing state and helpers on
 * {@link BrewingStandBlockEntity}, used by {@code BrewingStandCatchupHandler}.
 *
 * <p>The catch-up handler re-uses vanilla's own {@code isBrewable} / {@code doBrew}
 * via {@link Invoker} so all brewing rules, ingredient remainders, and the brew
 * level-event are handled correctly rather than re-implemented.
 *
 * <p>Note: on 1.20.1 {@code isBrewable} takes only the inventory (no
 * {@code PotionBrewing} argument — that became instance-based on {@code Level} in
 * 1.20.5+).
 *
 * @author Mark Gottschling
 */
@Mixin(BrewingStandBlockEntity.class)
public interface IEverFurnaceBrewingStandMixin {

    @Accessor
    int getBrewTime();
    @Accessor
    void setBrewTime(int brewTime);

    @Accessor
    int getFuel();
    @Accessor
    void setFuel(int fuel);

    @Accessor
    Item getIngredient();
    @Accessor
    void setIngredient(Item ingredient);

    /** The private 5-slot inventory (0–2 bottles, 3 ingredient, 4 blaze-powder fuel). */
    @Accessor("items")
    NonNullList<ItemStack> getBrewingItems();

    @Invoker("isBrewable")
    static boolean callIsBrewable(NonNullList<ItemStack> items) {
        throw new AssertionError();
    }

    @Invoker("doBrew")
    static void callDoBrew(Level level, BlockPos pos, NonNullList<ItemStack> items) {
        throw new AssertionError();
    }
}
