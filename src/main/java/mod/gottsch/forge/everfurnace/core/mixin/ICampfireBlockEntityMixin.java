package mod.gottsch.forge.everfurnace.core.mixin;

import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor interface for the private cooking arrays on {@link CampfireBlockEntity}.
 *
 * <p>{@code items} is already public via {@code getItems()}, so only the two
 * per-slot progress arrays need accessors.
 */
@Mixin(CampfireBlockEntity.class)
public interface ICampfireBlockEntityMixin {

    @Accessor
    int[] getCookingProgress();

    @Accessor
    int[] getCookingTime();
}
