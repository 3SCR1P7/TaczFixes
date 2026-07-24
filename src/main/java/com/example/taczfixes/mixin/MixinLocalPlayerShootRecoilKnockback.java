package com.example.taczfixes.mixin;

import com.example.taczfixes.Config;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gameplay.LocalPlayerShoot;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunRecoil;
import com.tacz.guns.resource.pojo.data.gun.GunRecoilKeyFrame;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.tacz.guns.client.gameplay.LocalPlayerShoot", remap = false)
public class MixinLocalPlayerShootRecoilKnockback {
    @Shadow(remap = false)
    private LocalPlayer player;

    @Inject(method = "doShoot", at = @At("HEAD"), remap = false)
    private void onDoShoot(GunDisplayInstance display, IGun gun, ItemStack gunItem, GunData gunData, long coolDown, float chargeProgress, CallbackInfo ci) {
        if (!Config.RECOIL_KNOCKBACK_ENABLED.get()) return;

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

        float totalRecoil = maxPitch + maxYaw;
        if (totalRecoil < 0.01f) return;

        float force = totalRecoil * Config.RECOIL_KNOCKBACK_MULTIPLIER.get().floatValue();
        if (player.isCrouching()) {
            force *= Config.RECOIL_KNOCKBACK_SNEAK_MULTIPLIER.get().floatValue();
        }
        Vec3 lookAngle = player.getLookAngle();
        player.push(-lookAngle.x * force, -lookAngle.y * force, -lookAngle.z * force);
    }
}
