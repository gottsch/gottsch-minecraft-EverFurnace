package mod.gottsch.forge.everfurnace.core.furnace;

/**
 * Accessor interface for mixin instance fields on {@code ModFurnaceBlockEntityMixin}.
 *
 * <p>All external access to mixin instance fields (from event handlers or other
 * non-mixin classes) must go through this interface. Direct casting to the mixin
 * class itself is not permitted by Mixin's verifier.
 *
 * <p>Usage pattern:
 * <pre>{@code
 * ModFurnaceBlockEntityInterface mixin =
 *         (ModFurnaceBlockEntityInterface)(Object) furnaceBlockEntity;
 * int pending = mixin.everFurnace_1_19_2$getPendingNotification();
 * }</pre>
 *
 * @author by Mark Gottschling on 3/31/2026
 */
public interface IEverFurnaceBlockEntity {

    long  everFurnace_1_19_2$getLastGameTime();
    void  everFurnace_1_19_2$setLastGameTime(long gameTime);

    int   everFurnace_1_19_2$getPendingNotification();
    void  everFurnace_1_19_2$setPendingNotification(int count);

    long  everFurnace_1_19_2$getLastNotificationTime();
    void  everFurnace_1_19_2$setLastNotificationTime(long gameTime);

    float everFurnace_1_19_2$getPendingXp();
    void  everFurnace_1_19_2$setPendingXp(float xp);
}
