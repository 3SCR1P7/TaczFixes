package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.Config;
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.data.GunTaczFixesData;
import com.ssscript.taczfixes.data.TaczFixesDataManager;
import com.ssscript.taczfixes.util.GunEnchantmentHelper;
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
    @Unique
    private static int taczfixes$recoilShotCount = 0;

    @Inject(method = "initialCameraRecoil", at = @At("HEAD"), remap = false)
    private static void taczfixes$preRecoil(GunFireEvent event, CallbackInfo ci) {
        taczfixes$recoilMultiplierPitch = 1.0f;
        taczfixes$recoilMultiplierYaw = 1.0f;
        LivingEntity shooter = event.getShooter();
        if (!(shooter instanceof LocalPlayer player)) return;
        
        int stability = GunEnchantmentHelper.getLevel(player.getMainHandItem(), TaczFixesMod.STABILITY_ENCHANTMENT.get());
        float stabilityFactor = 1.0f;
        if (stability > 0) {
            stabilityFactor = Math.max(1.0f - (float) (Config.ENCH_STABILITY_RECOIL_REDUCTION_PER_LEVEL.get() / 100.0 * stability), 0.0f);
        }

        IGun gun = IGun.getIGunOrNull(player.getMainHandItem());
        if (gun == null) return;
        ItemStack gunItem = player.getMainHandItem();
        ResourceLocation gunId = gun.getGunId(gunItem);
        if (gunId == null) return;
        ResourceLocation dataId = TaczFixesDataManager.resolveDataId(gunId);

        FireMode mode = IGun.getMainHandFireMode(player);
        GunTaczFixesData.RecoilConfig perGun = TaczFixesDataManager.resolveRecoil(dataId, mode);
        boolean perGunActive = perGun != null
                && ((perGun.modifiers != null && !perGun.modifiers.isEmpty())
                || perGun.pitch_multiplier != null || perGun.yaw_multiplier != null);
        if (perGunActive) {
            long window = perGun.window != null ? perGun.window : 0;
            long elapsed = System.currentTimeMillis() - shootTimeStamp;
            if (elapsed < 0 || elapsed >= window) {
                taczfixes$recoilShotCount = 1;
            } else {
                taczfixes$recoilShotCount++;
            }
            float[] multipliers = taczfixes$resolveRecoilMultipliers(perGun, taczfixes$recoilShotCount);
            taczfixes$recoilMultiplierPitch = multipliers[0];
            taczfixes$recoilMultiplierYaw = multipliers[1];
            taczfixes$recoilMultiplierPitch *= stabilityFactor;
            taczfixes$recoilMultiplierYaw *= stabilityFactor;
        } else {
            taczfixes$recoilShotCount = 0;

            if (Config.RECOIL_FIRE_RATE_REDUCTION_ENABLED.get()
                    && (mode == FireMode.AUTO || mode == FireMode.BURST)
                    && gun.getRPM(gunItem) >= Config.RECOIL_FIRE_RATE_MIN_RPM.get()
                    && !Config.RECOIL_FIRE_RATE_DISABLED_GUNS.get().contains(gunId.toString())) {
                long elapsed = System.currentTimeMillis() - shootTimeStamp;
                if (elapsed >= 0 && elapsed < Config.RECOIL_FIRE_RATE_WINDOW.get()) {
                    taczfixes$recoilMultiplierPitch = Config.RECOIL_FIRE_RATE_FACTOR.get().floatValue();
                    taczfixes$recoilMultiplierYaw = Config.RECOIL_FIRE_RATE_FACTOR.get().floatValue();
                } else {
                    taczfixes$recoilMultiplierPitch = Config.RECOIL_FIRE_RATE_PAUSE_FACTOR_PITCH.get().floatValue();
                    taczfixes$recoilMultiplierYaw = Config.RECOIL_FIRE_RATE_PAUSE_FACTOR_YAW.get().floatValue();
                }
            }
            taczfixes$recoilMultiplierPitch *= stabilityFactor;
            taczfixes$recoilMultiplierYaw *= stabilityFactor;
        }
    }

    @Unique
    private static float[] taczfixes$resolveRecoilMultipliers(GunTaczFixesData.RecoilConfig config, int count) {
        Double pitch = null;
        Double yaw = null;
        if (config.modifiers != null && !config.modifiers.isEmpty()) {
            for (GunTaczFixesData.RecoilModifierConfig mod : config.modifiers.values()) {
                if (mod != null && mod.count != null && mod.count == count) {
                    pitch = mod.pitch_multiplier;
                    yaw = mod.yaw_multiplier;
                    break;
                }
            }
        } else if (count == 1) {
            pitch = config.pitch_multiplier;
            yaw = config.yaw_multiplier;
        }
        return new float[]{
                pitch != null ? pitch.floatValue() : 1.0f,
                yaw != null ? yaw.floatValue() : 1.0f
        };
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
