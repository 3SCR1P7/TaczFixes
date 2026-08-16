package com.ssscript.taczfixes.util;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;

public final class LiberateCompat {
    private static Method isLiberated;
    private static Method useVirtualInventory;
    private static boolean failed;

    private LiberateCompat() {
    }

    public static boolean isLiberated(Player player) {
        if (failed) return false;
        try {
            if (isLiberated == null) {
                Class<?> cls = Class.forName("com.mafuyu404.taczaddon.common.LiberateAttachment");
                isLiberated = cls.getMethod("isLiberated", Player.class);
            }
            return Boolean.TRUE.equals(isLiberated.invoke(null, player));
        } catch (Throwable t) {
            failed = true;
            return false;
        }
    }

    public static Inventory getVirtualInventory(Inventory inventory) {
        if (failed) return inventory;
        try {
            if (useVirtualInventory == null) {
                Class<?> cls = Class.forName("com.mafuyu404.taczaddon.common.LiberateAttachment");
                useVirtualInventory = cls.getMethod("useVirtualInventory", Inventory.class);
            }
            Inventory result = (Inventory) useVirtualInventory.invoke(null, inventory);
            return result == null ? inventory : result;
        } catch (Throwable t) {
            failed = true;
            return inventory;
        }
    }
}
