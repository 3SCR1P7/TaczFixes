package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.util.ChargeStorage;
import com.tacz.guns.client.gui.overlay.GunHudOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** replace_ammo_hud: 把弹药数/上限显示替换为电量百分比。 */
@Mixin(GunHudOverlay.class)
public class MixinGunHudOverlayCharge {

    @ModifyVariable(method = "render", at = @At("STORE"), index = 16, remap = false)
    private int taczfixes$chargeHudCount(int value) {
        ItemStack stack = taczfixes$chargeHudStack();
        if (stack == null) {
            return value;
        }
        int cachedMax = MixinGunHudOverlayAccessor.taczfixes$getCacheMaxAmmoCount();
        int percent = taczfixes$chargePercent(stack);
        return Math.max(0, Math.round(cachedMax * (percent / 100.0f)));
    }

    @ModifyVariable(method = "render", at = @At("STORE"), index = 19, remap = false)
    private String taczfixes$chargeHudCountText(String value) {
        ItemStack stack = taczfixes$chargeHudStack();
        return stack == null ? value : String.format("%03d%%", Integer.valueOf(taczfixes$chargePercent(stack)));
    }

    @ModifyVariable(method = "render", at = @At("STORE"), index = 20, remap = false)
    private String taczfixes$chargeHudMaxText(String value) {
        return taczfixes$chargeHudStack() == null ? value : "";
    }

    private static int taczfixes$chargePercent(ItemStack stack) {
        int max = ChargeStorage.getMax(stack);
        if (max <= 0) {
            return 0;
        }
        return Math.round(ChargeStorage.get(stack) * 100.0f / max);
    }

    private static ItemStack taczfixes$chargeHudStack() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        ItemStack stack = player.getMainHandItem();
        GunTaczFixesData.ChargeConfig cfg = ChargeStorage.config(stack);
        if (cfg == null || !Boolean.TRUE.equals(cfg.replace_ammo_hud) || ChargeStorage.getMax(stack) <= 0) {
            return null;
        }
        return stack;
    }
}
