package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.util.GunEnchantmentHelper;
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
        float speed = 1.0f;
        if (animation.name.contains("reload")) {
            speed = GunEnchantmentHelper.getQuickChargeAnimationSpeed(player);
        } else if (animation.name.contains("bolt")) {
            speed = GunEnchantmentHelper.getEfficiencyBoltAnimationSpeed(player);
        }
        if (speed > 1.0f) {
            this.taczfixes_speedFactor = speed;
        }
    }

    @ModifyVariable(method = "updateProgress", at = @At("HEAD"), argsOnly = true, index = 1, remap = false)
    private long taczfixes$applyAnimSpeed(long alphaProgress) {
        if (taczfixes_speedFactor > 1.0f) {
            return (long) (alphaProgress * taczfixes_speedFactor);
        }
        return alphaProgress;
    }
}
