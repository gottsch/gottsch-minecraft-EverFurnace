package mod.gottsch.forge.everfurnace.core.mixin;

import mod.gottsch.forge.everfurnace.core.furnace.IEverFurnaceBlockEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import javax.annotation.Nullable;

/**
 * @author by Mark Gottschling on 4/1/2026
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface IEverFurnaceBlockEntityMixin extends IEverFurnaceBlockEntity {

    @Accessor
    int getLitTime();

    @Accessor("litTime")
    void setLitTime(int litTime);

    @Accessor
    int getLitDuration();

    @Accessor
    int getCookingProgress();

    @Accessor
    void setCookingProgress(int cookingProgress);

    @Accessor
    int getCookingTotalTime();

    @Accessor
    void setCookingTotalTime(int cookingTotalTime);

    @Accessor
    NonNullList<ItemStack> getItems();

    @Accessor
    RecipeManager.CachedCheck<AbstractFurnaceBlockEntity, ? extends AbstractFurnaceBlockEntity> getQuickCheck();

    @Invoker
    boolean callIsLit();

    @Invoker
    boolean callCanBurn(@Nullable Recipe<?> recipe,
                               NonNullList<ItemStack> inventory,
                               int maxStackSize);

    @Invoker
    boolean callBurn(@Nullable Recipe<?> recipe,
                            NonNullList<ItemStack> inventory,
                            int maxStackSize);
}