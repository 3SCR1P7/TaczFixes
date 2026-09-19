package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.util.ChargeStorage;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.overlay.HeatBarOverlay;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunHeatData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** charge.overheat_bar: 用过热条显示剩余电量(填充比例、百分比文本与尺寸动画均按原生过热条逻辑, 数据来自电量)。 */
@Mixin(HeatBarOverlay.class)
public class MixinHeatBarOverlayChargeBar {
    private static final float CHARGE_HEAT_MAX = 100.0F;
    private static final GunHeatData SYNTHETIC_HEAT_DATA = new GunHeatData();

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/resource/pojo/data/gun/GunData;getHeatData()Lcom/tacz/guns/resource/pojo/data/gun/GunHeatData;",
            remap = false), remap = false)
    private GunHeatData taczfixes$overheatBarHeatData(GunData gunData) {
        return taczfixes$usesChargeBar() ? SYNTHETIC_HEAT_DATA : gunData.getHeatData();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/resource/pojo/data/gun/GunHeatData;getHeatMax()F",
            remap = false), remap = false)
    private float taczfixes$overheatBarHeatMax(GunHeatData heatData) {
        return heatData == SYNTHETIC_HEAT_DATA ? CHARGE_HEAT_MAX : heatData.getHeatMax();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/api/item/IGun;hasHeatData(Lnet/minecraft/world/item/ItemStack;)Z",
            remap = false), remap = false)
    private boolean taczfixes$overheatBarHasHeatData(IGun gun, ItemStack stack) {
        return gun.hasHeatData(stack) || taczfixes$usesChargeBar(stack);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/api/item/IGun;getHeatAmount(Lnet/minecraft/world/item/ItemStack;)F",
            remap = false), remap = false)
    private float taczfixes$overheatBarHeatAmount(IGun gun, ItemStack stack) {
        if (taczfixes$usesChargeBar(stack)) {
            int max = ChargeStorage.getMax(stack);
            return max <= 0 ? 0.0F : CHARGE_HEAT_MAX * ChargeStorage.get(stack) / (float) max;
        }
        return gun.getHeatAmount(stack);
    }

    private static boolean taczfixes$usesChargeBar() {
        if (Minecraft.getInstance().player == null) {
            return false;
        }
        return taczfixes$usesChargeBar(Minecraft.getInstance().player.getMainHandItem());
    }

    private static boolean taczfixes$usesChargeBar(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        GunTaczFixesData.ChargeConfig cfg = ChargeStorage.config(stack);
        return cfg != null && Boolean.TRUE.equals(cfg.overheat_bar) && ChargeStorage.getMax(stack) > 0;
    }
}
