package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.register.Config;
import com.ssscript.taczfixes.common.register.TaczFixesMod;
import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import com.ssscript.taczfixes.common.util.RecoilMultiplierResolver;
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

import java.util.ArrayList;
import java.util.List;

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
        List<GunTaczFixesData.RecoilConfig> recoilConfigs = new ArrayList<>();
        GunTaczFixesData.RecoilConfig perGun = TaczFixesDataManager.resolveRecoil(dataId, mode);
        if (RecoilMultiplierResolver.isActive(perGun)) {
            recoilConfigs.add(perGun);
        }
        List<GunTaczFixesData.RecoilConfig> attachmentConfigs =
                AttachmentTaczFixesManager.resolveRecoilList(gunItem, mode);
        if (attachmentConfigs != null) {
            recoilConfigs.addAll(attachmentConfigs);
        }
        if (!recoilConfigs.isEmpty()) {
            float[] multipliers = RecoilMultiplierResolver.advance(recoilConfigs, System.currentTimeMillis());
            taczfixes$recoilMultiplierPitch = multipliers[0];
            taczfixes$recoilMultiplierYaw = multipliers[1];
        } else {
            RecoilMultiplierResolver.advance(null, System.currentTimeMillis());

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
        }
        taczfixes$recoilMultiplierPitch *= stabilityFactor;
        taczfixes$recoilMultiplierYaw *= stabilityFactor;
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
