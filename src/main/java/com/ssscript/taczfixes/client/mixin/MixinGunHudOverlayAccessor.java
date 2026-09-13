package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.overlay.GunHudOverlay;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 复用 tacz 原生 HUD 的弹药计算逻辑(缓存弹容/背包弹量)与结果。 */
@Mixin(GunHudOverlay.class)
public interface MixinGunHudOverlayAccessor {

    @Invoker("handleCacheCount")
    static void taczfixes$handleCacheCount(LocalPlayer player, ItemStack stack, GunData gunData, IGun gun,
                                           boolean useInventoryAmmo) {
        throw new AssertionError();
    }

    @Accessor("cacheMaxAmmoCount")
    static int taczfixes$getCacheMaxAmmoCount() {
        throw new AssertionError();
    }

    @Accessor("cacheInventoryAmmoCount")
    static int taczfixes$getCacheInventoryAmmoCount() {
        throw new AssertionError();
    }
}
