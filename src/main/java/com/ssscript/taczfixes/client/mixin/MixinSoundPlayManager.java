package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.ssscript.taczfixes.client.client.DualWieldClient;
import com.ssscript.taczfixes.client.client.OffhandDisplayManager;
import com.ssscript.taczfixes.common.util.DualWieldBalance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    /**
     * F 换手时主手新枪的 draw 被调度器压后, 对应的掏枪音效也一起压后,
     * 否则会出现"先听到掏枪声、过一会儿才看到掏枪动画"。
     */
    @Inject(method = {"playDrawSound"}, at = {@At("HEAD")}, cancellable = true, require = 0, remap = false)
    private static void dualWield$deferMainHandDrawSoundDuringHandSwap(LivingEntity shooter, GunDisplayInstance display, CallbackInfo callback) {
        if (OffhandDisplayManager.isHoldingMainHandDrawSound()) {
            callback.cancel();
        }
    }
}
