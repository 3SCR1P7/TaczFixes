package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.handler.SteplessZoomHandler;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 无极变倍：重定向 CameraSetupEvent 中对 IGun#getAimingZoom 的调用。
 * 当当前瞄具启用了无极变倍时返回 SteplessZoomHandler 维护的当前倍率，
 * 否则调用原方法回落到原版逻辑。
 * 覆盖两个调用点：
 *  - applyScopeMagnification：开镜世界 FOV（真正的倍率）
 *  - initialCameraRecoil：开镜时基于倍率的后坐力镜头缩放
 */
@Mixin(targets = "com.tacz.guns.client.event.CameraSetupEvent", remap = false)
public class MixinCameraSetupEventZoom {
    @Redirect(method = {
            "applyScopeMagnification",
            "initialCameraRecoil"
    }, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/item/IGun;getAimingZoom(Lnet/minecraft/world/item/ItemStack;)F"), remap = false)
    private static float taczfixes$steplessZoom(IGun gun, ItemStack stack) {
        float stepless = SteplessZoomHandler.getSteplessZoom(stack);
        if (stepless > 0.0f) {
            return stepless;
        }
        return gun.getAimingZoom(stack);
    }
}
