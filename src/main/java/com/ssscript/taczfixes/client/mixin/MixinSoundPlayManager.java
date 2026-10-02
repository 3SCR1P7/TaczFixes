package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.client.sound.SoundPlayManager;
import com.ssscript.taczfixes.client.client.DualWieldClient;
import com.ssscript.taczfixes.common.util.DualWieldBalance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = {SoundPlayManager.class}, remap = false)
public abstract class MixinSoundPlayManager {
    @ModifyArg(method = {"playReloadSound"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/client/sound/SoundPlayManager;playClientSound(Lnet/minecraft/world/entity/Entity;Lcom/tacz/guns/client/resource/pojo/display/SoundData;FFI)Lcom/tacz/guns/client/sound/GunSoundInstance;"), index = 3, require = 2, allow = 2)
    private static float dualWield$slowLegacyReloadSound(float originalPitch) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (DualWieldClient.isDualMode(player)) {
            return originalPitch * ((float) DualWieldBalance.getReloadTimeScale(player.getMainHandItem()));
        }
        return originalPitch;
    }
}
