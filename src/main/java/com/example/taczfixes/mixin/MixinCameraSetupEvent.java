package com.example.taczfixes.mixin;

import com.example.taczfixes.Config;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.tacz.guns.client.event.CameraSetupEvent", remap = false)
public class MixinCameraSetupEvent {
    @Shadow(remap = false)
    private static long shootTimeStamp;

    @Unique
    private static float taczfixes$recoilMultiplierPitch = 1.0f;
    @Unique
    private static float taczfixes$recoilMultiplierYaw = 1.0f;

    @Inject(method = "initialCameraRecoil", at = @At("HEAD"), remap = false)
    private static void taczfixes$preRecoil(GunFireEvent event, CallbackInfo ci) {
        taczfixes$recoilMultiplierPitch = 1.0f;
        taczfixes$recoilMultiplierYaw = 1.0f;
        if (!Config.RECOIL_FIRE_RATE_REDUCTION_ENABLED.get()) return;
        LivingEntity shooter = event.getShooter();
        if (!(shooter instanceof LocalPlayer player)) return;
        FireMode mode = IGun.getMainHandFireMode(player);
        if (mode != FireMode.AUTO && mode != FireMode.BURST) return;

        IGun gun = IGun.getIGunOrNull(player.getMainHandItem());
        if (gun == null || gun.getRPM(player.getMainHandItem()) < Config.RECOIL_FIRE_RATE_MIN_RPM.get()) return;

        long elapsed = System.currentTimeMillis() - shootTimeStamp;
        if (elapsed >= 0 && elapsed < Config.RECOIL_FIRE_RATE_WINDOW.get()) {
            taczfixes$recoilMultiplierPitch = Config.RECOIL_FIRE_RATE_FACTOR.get().floatValue();
            taczfixes$recoilMultiplierYaw = Config.RECOIL_FIRE_RATE_FACTOR.get().floatValue();
        } else {
            taczfixes$recoilMultiplierPitch = Config.RECOIL_FIRE_RATE_PAUSE_FACTOR_PITCH.get().floatValue();
            taczfixes$recoilMultiplierYaw = Config.RECOIL_FIRE_RATE_PAUSE_FACTOR_YAW.get().floatValue();
        }
    }

    @ModifyArg(method = "initialCameraRecoil", at = @At(value = "INVOKE", target = "Lcom/tacz/guns/resource/pojo/data/gun/GunRecoil;genPitchSplineFunction(F)Lorg/apache/commons/math3/analysis/polynomials/PolynomialSplineFunction;"), index = 0, remap = false)
    private static float taczfixes$modifyPitchScale(float scale) {
        return scale * taczfixes$recoilMultiplierPitch;
    }

    @ModifyArg(method = "initialCameraRecoil", at = @At(value = "INVOKE", target = "Lcom/tacz/guns/resource/pojo/data/gun/GunRecoil;genYawSplineFunction(F)Lorg/apache/commons/math3/analysis/polynomials/PolynomialSplineFunction;"), index = 0, remap = false)
    private static float taczfixes$modifyYawScale(float scale) {
        return scale * taczfixes$recoilMultiplierYaw;
    }
}
