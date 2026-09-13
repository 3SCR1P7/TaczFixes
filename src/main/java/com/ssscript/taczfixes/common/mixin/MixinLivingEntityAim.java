package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.register.Config;
import com.ssscript.taczfixes.common.register.TaczFixesMod;
import com.ssscript.taczfixes.common.util.AimingStaminaState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 服务端: 上肢耐力不足时禁止开镜。 */
@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntityAim", remap = false)
public class MixinLivingEntityAim {

    @Shadow(remap = false)
    private LivingEntity shooter;

    @Inject(method = "aim", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$blockLowStamina(boolean aiming, CallbackInfo ci) {
        if (!aiming) return;
        if (shooter.level().isClientSide) return;
        if (!Config.AIMING_STAMINA_ENABLED.get()) return;
        com.ssscript.taczfixes.common.data.GunTaczFixesData.AimingStaminaConfig aimCfg =
                com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager.resolveAimingStamina(shooter.getMainHandItem());
        float threshold = aimCfg.min_stamina_to_aim.floatValue();
        if (threshold <= 0f) return;
        float max = (float) shooter.getAttributeValue(TaczFixesMod.AIMING_STAMINA_ATTRIBUTE.get());
        if (AimingStaminaState.getStamina(shooter.getUUID(), max) < threshold) {
            ci.cancel();
        }
    }
}
