package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.client.sound.SoundPlayManager;
import com.ssscript.taczfixes.client.render.DualWieldClient;
import com.ssscript.taczfixes.common.util.DualWieldBalance;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = {SoundPlayManager.class}, remap = false)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/mixin/MixinSoundPlayManager.class */
public abstract class MixinSoundPlayManager {
    @ModifyArg(method = {"playReloadSound"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/client/sound/SoundPlayManager;playClientSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/resources/ResourceLocation;FFI)Lcom/tacz/guns/client/sound/GunSoundInstance;"), index = ServerMessageOffhandActionResult.ACTION_FIRE_SELECT, require = ServerMessageOffhandActionResult.ACTION_BOLT)
    private static float dualWield$slowLegacyReloadSound(float originalPitch) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (DualWieldClient.isDualMode(player)) {
            return originalPitch * ((float) DualWieldBalance.getReloadTimeScale(player.getMainHandItem()));
        }
        return originalPitch;
    }
}
