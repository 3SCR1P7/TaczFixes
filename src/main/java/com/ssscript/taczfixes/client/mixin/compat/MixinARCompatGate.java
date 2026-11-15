package com.ssscript.taczfixes.client.mixin.compat;

import com.ssscript.taczfixes.client.camo.DynamicCamo;
import com.ssscript.taczfixes.client.client.ClientArGate;
import com.ssscript.taczfixes.client.util.GunRecolorManager;
import com.tacz.guns.compat.ar.ARCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ARCompat.class, remap = false)
public abstract class MixinARCompatGate {
    @Inject(method = "shouldAccelerate", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$forceVanillaForCustomOptic(CallbackInfoReturnable<Boolean> callback) {
        if (ClientArGate.isForcedVanilla()) {
            callback.setReturnValue(false);
            return;
        }
        // 动态迷彩启用时, 带迷彩的枪/配件不走加速路径:
        // 加速渲染会把模型绘制延后到镜片合成之后, 迷彩会漏进瞄具镜内且无法可靠遮罩;
        // 退回原版渲染路径后镜片会正常盖住镜内区域。
        if (hasCamoOn(GunRecolorManager.currentRenderItem())) {
            callback.setReturnValue(false);
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && hasCamoOn(player.getMainHandItem())) {
            callback.setReturnValue(false);
        }
    }

    private static boolean hasCamoOn(ItemStack stack) {
        try {
            return stack != null && !stack.isEmpty() && DynamicCamo.appliedOn(stack) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
