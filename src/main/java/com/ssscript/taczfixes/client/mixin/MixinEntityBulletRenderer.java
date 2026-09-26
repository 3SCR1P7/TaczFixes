package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.renderer.entity.EntityBulletRenderer;
import com.tacz.guns.entity.EntityKineticBullet;
import com.ssscript.taczfixes.client.render.DualMuzzleFlashState;
import com.ssscript.taczfixes.client.render.DualWieldClient;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.ssscript.taczfixes.common.util.DualWieldOverrides;
import com.ssscript.taczfixes.common.util.OffhandBulletSource;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.Entity;

@Mixin(value = {EntityBulletRenderer.class}, remap = false)
public abstract class MixinEntityBulletRenderer {
    @Inject(method = {"renderTracerAmmo(Lcom/tacz/guns/entity/EntityKineticBullet;[FFLcom/mojang/blaze3d/vertex/PoseStack;I)V"}, at = {@At("HEAD")})
    private void dualWield$selectMuzzleForBullet(EntityKineticBullet bullet, float[] tracerColor, float partialTicks, PoseStack poseStack, int packedLight, CallbackInfo callback) {
        if (bullet.getFirstPersonRenderOffset() == null) {
            Entity localPlayerM_19749_ = bullet.getOwner();
            if (localPlayerM_19749_ instanceof LocalPlayer) {
                LocalPlayer player = (LocalPlayer) localPlayerM_19749_;
                if (!Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
                    return;
                }
                boolean offhand = ((OffhandBulletSource) bullet).dualWield$isOffhandSource();
                if (!offhand && !DualWieldClient.isDualMode(player)) {
                    return;
                }
                Vector3f muzzle = DualMuzzleFlashState.copyMuzzle(offhand);
                if (muzzle == null) {
                    muzzle = DualMuzzleFlashState.copyMuzzle(!offhand);
                    double leftOffset = DualWieldOverrides.leftOffset(player.getOffhandItem(), DualWieldEligibility.getClientLeftGunXOffset());
                    double rightOffset = DualWieldOverrides.rightOffset(player.getMainHandItem(), DualWieldEligibility.getClientRightGunXOffset());
                    double sourceOffset = offhand ? rightOffset : leftOffset;
                    double targetOffset = offhand ? leftOffset : rightOffset;
                    if (muzzle != null) {
                        muzzle.add((float) (targetOffset - sourceOffset), 0.0f, 0.0f);
                    } else {
                        muzzle = new Vector3f((float) targetOffset, 0.0f, 0.0f);
                    }
                }
                Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
                bullet.setCameraXRot(camera.getXRot());
                bullet.setCameraYRot(camera.getYRot());
                bullet.setFirstPersonRenderOffset(muzzle);
            }
        }
    }
}
