package com.example.taczfixes.mixin;

import com.example.taczfixes.util.GunEnchantmentHelper;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 快速装填：换弹动画倍速播放，与服务器端缩短的换弹时间保持一致。
 * 创建 ObjectAnimationRunner 时若动画名含 "reload"，按本地玩家主手枪械的
 * 快速装填附魔等级计算播放倍速；updateProgress 按倍速推进动画进度。
 */
@Mixin(ObjectAnimationRunner.class)
public class MixinObjectAnimationRunner {
    @Unique
    private float taczfixes_speedFactor = 1.0f;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void taczfixes$initReloadSpeed(ObjectAnimation animation, CallbackInfo ci) {
        if (animation == null || animation.name == null || !animation.name.contains("reload")) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        float speed = GunEnchantmentHelper.getQuickChargeAnimationSpeed(player);
        if (speed > 1.0f) {
            this.taczfixes_speedFactor = speed;
        }
    }

    @ModifyVariable(method = "updateProgress", at = @At("HEAD"), argsOnly = true, index = 1, remap = false)
    private long taczfixes$applyReloadSpeed(long alphaProgress) {
        if (taczfixes_speedFactor > 1.0f) {
            return (long) (alphaProgress * taczfixes_speedFactor);
        }
        return alphaProgress;
    }
}
