package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 火控瞄具装在自定义瞄具槽时, 落点预测锁定在准星位置(不真正预测):
 * updateImpactPrediction 基于 getCurrentFov 的投影计算在自定义槽瞄具下会错位/乱飞,
 * 此处强制 impactProjectionY=0(屏幕中心/准星), showImpactPrediction 保持原判定。
 */
@Mixin(targets = "com.tacz.guns.client.resource.pojo.display.gun.RangefinderManager", remap = false)
public class MixinRangefinderLock {

    @Shadow(remap = false)
    private static float impactProjectionY;

    @Inject(method = "updateImpactPrediction", at = @At("TAIL"), remap = false)
    private static void taczfixes$lockProjection(net.minecraft.client.player.LocalPlayer player,
                                                 ItemStack stack,
                                                 com.tacz.guns.api.item.IGun iGun,
                                                 com.tacz.guns.client.resource.pojo.display.gun.Rangefinder config,
                                                 double distance, CallbackInfo ci) {
        if (stack == null || stack.isEmpty()) return;
        if (ScopeSwitchState.getActiveSlot(stack) != null) {
            impactProjectionY = 0.0F;
        }
    }
}
