package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.common.util.GunsmithLibHelper;
import com.tacz.guns.api.TimelessAPI;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.lang.reflect.Method;
import java.util.Optional;

/** 通过 gunsmithlib 解析枪械当前弹种的显示文字。 */
@OnlyIn(Dist.CLIENT)
public final class GunsmithLibAmmoNameCompat {
    private static boolean resolved;
    private static Method getGunInfo;
    private static Method getAmmoId;

    private GunsmithLibAmmoNameCompat() {
    }

    /** 解析该枪械当前弹种显示名; 未安装 gunsmithlib 或无法解析时返回 null。 */
    public static String resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !GunsmithLibHelper.isLoaded()) {
            return null;
        }
        try {
            if (!resolved) {
                resolved = true;
                Class<?> gunsmith = Class.forName("mod.chloeprime.gunsmithlib.api.util.Gunsmith");
                Class<?> gunInfo = Class.forName("mod.chloeprime.gunsmithlib.api.util.GunInfo");
                Class<?> gsHelper = Class.forName("mod.chloeprime.gunsmithlib.common.util.GsHelper");
                getGunInfo = gunsmith.getMethod("getGunInfo", ItemStack.class);
                getAmmoId = gsHelper.getMethod("getAmmoId", gunInfo);
            }
            if (getGunInfo == null || getAmmoId == null) {
                return null;
            }
            Object info = ((Optional<?>) getGunInfo.invoke(null, stack)).orElse(null);
            if (info == null) {
                return null;
            }
            Object id = getAmmoId.invoke(null, info);
            if (!(id instanceof ResourceLocation ammoId)) {
                return null;
            }
            String key = TimelessAPI.getClientAmmoIndex(ammoId).map(index -> index.getName()).orElse(null);
            if (key == null || key.isBlank()) {
                return null;
            }
            return I18n.get(key);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
