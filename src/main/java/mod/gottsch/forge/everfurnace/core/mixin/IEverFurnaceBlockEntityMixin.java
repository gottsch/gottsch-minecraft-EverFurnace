package mod.gottsch.forge.everfurnace.core.mixin;

import mod.gottsch.forge.everfurnace.core.furnace.IEverFurnaceBlockEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import javax.annotation.Nullable;

/**
 * Accessor / invoker interface for {@link AbstractFurnaceBlockEntity}.
 *
 * <p>{@code @Accessor} methods expose private fields so that
 * {@link mod.gottsch.forge.everfurnace.core.catchup.FurnaceCatchupHandler}
 * can read and write them without needing Access Transformer entries.
 *
 * <p>{@code @Invoker} methods expose private/protected methods for the same reason.
 *
 * @author Mark Gottschling
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface IEverFurnaceBlockEntityMixin extends IEverFurnaceBlockEntity {

    // -------------------------------------------------------------------------
    // Field accessors — replace AT entries for these fields
    // -------------------------------------------------------------------------

    /** The full inventory list (input, fuel, output slots). */
    @Accessor("items")
    NonNullList<ItemStack> getItems();

    /** Remaining burn time from the current fuel item, in ticks. */
    @Accessor("litTime")
    int getLitTime();

    /** @see #getLitTime() */
    @Accessor("litTime")
    void setLitTime(int litTime);

    /** Total burn duration of the most recently consumed fuel item, in ticks. */
    @Accessor("litDuration")
    int getLitDuration();

    /** Cooking progress toward the current recipe, in ticks. */
    @Accessor("cookingProgress")
    int getCookingProgress();

    /** @see #getCookingProgress() */
    @Accessor("cookingProgress")
    void setCookingProgress(int cookingProgress);

    /** Total cook time required for the current recipe, in ticks. */
    @Accessor("cookingTotalTime")
    int getCookingTotalTime();

    // -------------------------------------------------------------------------
    // Method invokers — replace AT entries for these methods
    // -------------------------------------------------------------------------

    /** Invokes the private {@code isLit()} method. */
    @Invoker("isLit")
    boolean callIsLit();

    /** Invokes the private {@code canBurn()} method. */
    @Invoker
    boolean callCanBurn(RegistryAccess registryAccess, @Nullable Recipe<?> recipe,
                        NonNullList<ItemStack> inventory, int i);

    /** Invokes the private {@code burn()} method. */
    @Invoker
    boolean callBurn(RegistryAccess registryAccess, @Nullable Recipe<?> recipe,
                     NonNullList<ItemStack> inventory, int maxStackSize);
}
