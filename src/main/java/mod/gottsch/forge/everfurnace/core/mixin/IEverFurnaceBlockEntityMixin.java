package mod.gottsch.forge.everfurnace.core.mixin;

import mod.gottsch.forge.everfurnace.core.furnace.IEverFurnaceBlockEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import javax.annotation.Nullable;

/**
 * @author by Mark Gottschling on 5/7/2026
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface IEverFurnaceBlockEntityMixin extends IEverFurnaceBlockEntity {

    @Invoker
    public boolean callCanBurn(RegistryAccess registryAccess, @Nullable Recipe<?> recipe, NonNullList<ItemStack> inventory, int i);

    @Invoker
    public boolean callBurn(RegistryAccess registryAccess, @Nullable Recipe<?> recipe, NonNullList<ItemStack> inventory, int maxStackSize);

}