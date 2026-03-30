package mod.gottsch.forge.everfurnace.core.furnace;

/**
 * @author by Mark Gottschling on 3/29/2026
 */
public interface ModFurnaceBlockEntityInterface {

    public long everFurnace_1_20_1$getLastGameTime();
    public void everFurnace_1_20_1$setLastGameTime(long gameTime);
    public int everFurnace_1_20_1$getPendingNotification();
    public void everFurnace_1_20_1$setPendingNotification(int count);
    public long everFurnace_1_20_1$getLastNotificationTime();
    public void everFurnace_1_20_1$setLastNotificationTime(long gameTime);
    public float everFurnace_1_20_1$getPendingXp();
    public void everFurnace_1_20_1$setPendingXp(float xp);
}