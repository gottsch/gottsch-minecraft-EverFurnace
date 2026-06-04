package mod.gottsch.forge.everfurnace.core.catchup;

import mod.gottsch.forge.everfurnace.api.CookingCatchupHandler;
import mod.gottsch.forge.everfurnace.core.mixin.ICampfireBlockEntityMixin;
import mod.gottsch.forge.everfurnace.core.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;

/**
 * Built-in {@link CookingCatchupHandler} for vanilla campfire and soul campfire.
 *
 * <p>One instance is registered for both block entity types in
 * {@link mod.gottsch.forge.everfurnace.EverFurnace}.
 *
 * <p>Rather than re-implementing recipe assembly, item drops, and slot clearing,
 * this handler advances each slot's {@code cookingProgress} up to its
 * {@code cookingTime} cap.  Vanilla's own {@code ++}/threshold check immediately
 * after our HEAD inject then handles completion for any slot pushed to its cap.
 *
 * <p>Output is inherently bounded to one item per slot because each slot holds
 * a single item and is never restocked mid-cook.
 *
 * @author Mark Gottschling
 */
public class CampfireCatchupHandler implements CookingCatchupHandler {

    @Override
    public void applyCatchup(BlockEntity blockEntity, long deltaTime, ServerLevel level, BlockPos pos) {

        CampfireBlockEntity       campfire = (CampfireBlockEntity) blockEntity;
        ICampfireBlockEntityMixin accessor = (ICampfireBlockEntityMixin)(Object) blockEntity;

        NonNullList<ItemStack> items          = campfire.getItems();
        int[]                  cookingProgress = accessor.getCookingProgress();
        int[]                  cookingTime     = accessor.getCookingTime();

        boolean anyCompleted = false;

        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isEmpty()) continue;

            int total = cookingTime[i];
            if (total <= 0) continue;

            int remaining = total - cookingProgress[i];
            if (deltaTime >= remaining) {
                // Push to total; vanilla's ++/threshold check completes the craft this tick.
                cookingProgress[i] = total;
                anyCompleted = true;
            } else {
                cookingProgress[i] += (int) deltaTime;
            }
        }

        if (anyCompleted) {
            ModNetwork.sendCatchupParticles(level, pos);
        }
    }
}
