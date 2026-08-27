package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ObjectAnimationRunner.class)
public class MixinObjectAnimationRunner {
    @Unique
    private float taczfixes_speedFactor = 1.0f;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void taczfixes$initAnimSpeed(ObjectAnimation animation, CallbackInfo ci) {
        if (animation == null || animation.name == null) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack gun = player.getMainHandItem();
        // 动画速度 = 1 / 总时间倍率(配件 data 倍率 × 附魔倍率), 与动作时长同步
        // 枪械 data 的 allow_animation_zoom 为 false 时不加速动画
        boolean zoomAllowed = com.ssscript.taczfixes.common.data.TaczFixesDataManager.isAnimationZoomAllowed(gun);
        double divisor = 1.0d;
        if (animation.name.contains("reload")) {
            divisor = AttachmentTaczFixesManager.getReloadTimeFactor(gun);
            if (divisor > 0.0d && GunEnchantmentHelper.isEnabled()) {
                divisor *= GunEnchantmentHelper.getQuickChargeTimeFactor(player);
            }
        } else if (animation.name.contains("bolt")) {
            divisor = AttachmentTaczFixesManager.getManualActionTimeFactor(gun);
            if (divisor > 0.0d && GunEnchantmentHelper.isEnabled()) {
                divisor *= GunEnchantmentHelper.getEfficiencyBoltTimeFactor(gun);
            }
        }
        // 时长被减为 0 或以下: 动画一帧内播完, 视觉上不播放(不受 allow_animation_zoom 影响)
        // allow_animation_zoom=false 时仅禁用部分加速的同步播放
        float speed;
        if (divisor <= 0.0d) {
            speed = 65536.0f;
        } else if (!zoomAllowed) {
            return;
        } else {
            speed = (float) (1.0d / divisor);
        }
        if (Math.abs(speed - 1.0f) > 1.0e-4f) {
            this.taczfixes_speedFactor = speed;
        }
    }

    @ModifyVariable(method = "updateProgress", at = @At("HEAD"), argsOnly = true, index = 1, remap = false)
    private long taczfixes$applyAnimSpeed(long alphaProgress) {
        if (taczfixes_speedFactor != 1.0f) {
            return (long) (alphaProgress * taczfixes_speedFactor);
        }
        return alphaProgress;
    }
}
