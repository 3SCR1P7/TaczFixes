package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.Config;
import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.util.RecoilMultiplierResolver;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.gameplay.LocalPlayerShoot;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunRecoil;
import com.tacz.guns.resource.pojo.data.gun.GunRecoilKeyFrame;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "com.tacz.guns.client.gameplay.LocalPlayerShoot", remap = false)
public class MixinLocalPlayerShootRecoilKnockback {
    @Shadow(remap = false)
    private LocalPlayer player;

    @Inject(method = "doShoot", at = @At("HEAD"), remap = false)
    private void onDoShoot(GunDisplayInstance display, IGun gun, ItemStack gunItem, GunData gunData, long coolDown, float chargeProgress, CallbackInfo ci) {
        if (player.getPose() == Pose.SWIMMING) return;

        GunRecoil recoil = gunData.getRecoil();
        if (recoil == null) return;

        float maxPitch = 0;
        float maxYaw = 0;
        for (GunRecoilKeyFrame frame : recoil.getPitch()) {
            if (frame != null) maxPitch = Math.max(maxPitch, frame.getValue()[1]);
        }
        for (GunRecoilKeyFrame frame : recoil.getYaw()) {
            if (frame != null) maxYaw = Math.max(maxYaw, frame.getValue()[1]);
        }
        if (maxPitch < 0.01f && maxYaw < 0.01f) return;

        ResourceLocation gunId = gun.getGunId(gunItem);
        if (gunId == null) return;
        ResourceLocation dataId = TaczFixesDataManager.resolveDataId(gunId);
        GunTaczFixesData.FireKnockbackConfig fireKnockback = TaczFixesDataManager.resolveFireKnockback(dataId);
        if (fireKnockback == null && !Config.RECOIL_KNOCKBACK_ENABLED.get()) return;

        FireMode mode = gun.getFireMode(gunItem);
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
        float[] multipliers = RecoilMultiplierResolver.peek(recoilConfigs, System.currentTimeMillis());
        float totalRecoil = maxPitch * multipliers[0] + maxYaw * multipliers[1];

        float force = totalRecoil * (player.isCrouching()
                ? fireKnockback != null && fireKnockback.power_sneak != null
                        ? fireKnockback.power_sneak.floatValue()
                        : Config.RECOIL_KNOCKBACK_SNEAK_MULTIPLIER.get().floatValue()
                : fireKnockback != null && fireKnockback.power != null
                        ? fireKnockback.power.floatValue()
                        : Config.RECOIL_KNOCKBACK_MULTIPLIER.get().floatValue());
        if (mode == FireMode.SEMI) {
            force *= fireKnockback != null && fireKnockback.multiplier_semi != null
                    ? fireKnockback.multiplier_semi.floatValue()
                    : Config.RECOIL_KNOCKBACK_SEMI_FACTOR.get().floatValue();
        } else if (mode == FireMode.BURST) {
            force *= fireKnockback != null && fireKnockback.multiplier_burst != null
                    ? fireKnockback.multiplier_burst.floatValue()
                    : 1.0f;
        }
        Vec3 lookAngle = player.getLookAngle();
        force = AttachmentTaczFixesManager.applyFireKnockback(gunItem, force);
        player.push(-lookAngle.x * force, -lookAngle.y * force, -lookAngle.z * force);
    }
}