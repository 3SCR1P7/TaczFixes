package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.Config;
import com.ssscript.taczfixes.data.GunTaczFixesData;
import com.ssscript.taczfixes.data.TaczFixesDataManager;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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
        LivingEntity shooter = event.getShooter();
        if (!(shooter instanceof LocalPlayer player)) return;
        IGun gun = IGun.getIGunOrNull(player.getMainHandItem());
        if (gun == null) return;
        ItemStack gunItem = player.getMainHandItem();
        ResourceLocation gunId = gun.getGunId(gunItem);
        if (gunId == null) return;
        ResourceLocation dataId = TaczFixesDataManager.resolveDataId(gunId);

        FireMode mode = IGun.getMainHandFireMode(player);
        GunTaczFixesData.RecoilConfig perGun = TaczFixesDataManager.resolveRecoil(dataId, mode);
        boolean perGunActive = perGun != null
                && (perGun.pitch_multiplier != null || perGun.yaw_multiplier != null);
        if (perGunActive) {
            long window = perGun.window != null ? perGun.window : 0;
            long elapsed = System.currentTimeMillis() - shootTimeStamp;
            if (elapsed >= 0 && elapsed < window) {
                taczfixes$recoilMultiplierPitch = 1.0f;
                taczfixes$recoilMultiplierYaw = 1.0f;
            } else {
                taczfixes$recoilMultiplierPitch = perGun.pitch_multiplier != null ? perGun.pitch_multiplier.floatValue() : 1.0f;
                taczfixes$recoilMultiplierYaw = perGun.yaw_multiplier != null ? perGun.yaw_multiplier.floatValue() : 1.0f;
            }
            return;
        }

        if (!Config.RECOIL_FIRE_RATE_REDUCTION_ENABLED.get()) return;
        if (mode != FireMode.AUTO && mode != FireMode.BURST) return;
        if (gun.getRPM(gunItem) < Config.RECOIL_FIRE_RATE_MIN_RPM.get()) return;
        if (Config.RECOIL_FIRE_RATE_DISABLED_GUNS.get().contains(gunId.toString())) return;

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
