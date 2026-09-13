package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.data.CustomFireModeManager;
import com.tacz.guns.client.input.ShootKey;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 客户端 Tick 级激活自定义模式:
 * ShootKey.autoShoot 每 tick 在客户端主线程执行, 而该流程内会读取 gunData.getBurstData()(连发判定),
 * 该调用点不在 shoot() 栈内, ThreadLocal 需要在主线程持续保持当前手持枪的自定义模式。
 */
@Mixin(value = ShootKey.class, remap = false)
public class MixinShootKey {

    private static void activateForMainHand() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            CustomFireModeManager.resetActive();
            return;
        }
        CustomFireModeManager.activateFor(mc.player.getMainHandItem());
    }

    @Inject(method = "autoShoot", at = @At("HEAD"), remap = false)
    private static void taczfixes$activateTick(TickEvent.ClientTickEvent event, CallbackInfo ci) {
        activateForMainHand();
    }

    @Inject(method = "shootControllerTick", at = @At("HEAD"), remap = false)
    private static void taczfixes$activateController(boolean shoot, CallbackInfoReturnable<Boolean> cir) {
        activateForMainHand();
    }
}
