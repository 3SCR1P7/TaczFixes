package com.ssscript.taczfixes.client.hud;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.overlay.GunHudOverlay;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Method;

/** TaCZ 0929 removed the useInventoryAmmo parameter from its HUD cache method. */
public final class GunHudCacheCompat {
    private static final Method UPDATE = findUpdate();

    private static Method findUpdate() {
        try {
            Method method;
            try {
                method = GunHudOverlay.class.getDeclaredMethod("handleCacheCount", LocalPlayer.class,
                        ItemStack.class, GunData.class, IGun.class);
            } catch (NoSuchMethodException oldVersion) {
                method = GunHudOverlay.class.getDeclaredMethod("handleCacheCount", LocalPlayer.class,
                        ItemStack.class, GunData.class, IGun.class, boolean.class);
            }
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unsupported TaCZ HUD cache API", exception);
        }
    }

    public static void update(LocalPlayer player, ItemStack stack, GunData data, IGun gun, boolean inventoryAmmo) {
        try {
            if (UPDATE.getParameterCount() == 4) UPDATE.invoke(null, player, stack, data, gun);
            else UPDATE.invoke(null, player, stack, data, gun, inventoryAmmo);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot update TaCZ HUD ammo cache", exception);
        }
    }

    private GunHudCacheCompat() {}
}
