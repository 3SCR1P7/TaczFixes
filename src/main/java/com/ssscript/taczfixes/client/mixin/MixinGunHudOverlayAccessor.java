package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.client.gui.overlay.GunHudOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 复用 tacz 原生 HUD 的弹药计算逻辑(缓存弹容/背包弹量)与结果。 */
@Mixin(GunHudOverlay.class)
public interface MixinGunHudOverlayAccessor {

    @Accessor("cacheMaxAmmoCount")
    static int taczfixes$getCacheMaxAmmoCount() {
        throw new AssertionError();
    }

    @Accessor("cacheInventoryAmmoCount")
    static int taczfixes$getCacheInventoryAmmoCount() {
        throw new AssertionError();
    }
}
