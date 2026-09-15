package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.client.particle.MuzzleParticleManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.ssscript.taczfixes.client.render.DualMuzzleParticleContext;
import com.ssscript.taczfixes.client.render.DualWieldClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {DualWieldClient.class}, remap = false)
public abstract class MixinDualWieldClientMuzzleParticles {
    @Inject(method = {"playOffhandShotVisual"}, at = {@At(value = "INVOKE", target = "Lcom/tacz/guns/client/model/functional/MuzzleFlashRender;onShoot()V", shift = At.Shift.AFTER, remap = false)}, remap = false)
    private static void dualWield$enqueueOffhandMuzzleParticles(LocalPlayer player, ItemStack stack, GunDisplayInstance display, GunData gunData, CallbackInfo callback) {
        if (!DualWieldClient.isDualMode(player)) {
            return;
        }
        DualMuzzleParticleContext.runOffhand(() -> {
            MuzzleParticleManager.onShoot(stack);
        });
    }
}
