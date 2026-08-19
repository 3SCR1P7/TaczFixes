package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.Config;
import com.ssscript.taczfixes.common.compat.PeekState;
import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.custom.AdsModifier;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(targets = "com.tacz.guns.client.gameplay.LocalPlayerAim", remap = false)
public class MixinLocalPlayerAim {
    @Shadow(remap = false)
    private LocalPlayer player;

    @Redirect(method = "getAlphaProgress",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/resource/modifier/AttachmentCacheProperty;getCache(Ljava/lang/String;)Ljava/lang/Object;",
                    remap = false), remap = false)
    private Object taczfixes$customScopeAimTime(AttachmentCacheProperty cache, String id) {
        if (AdsModifier.ID.equals(id)) {
            ItemStack gun = player.getMainHandItem();
            Optional<Float> custom = ScopeSwitchState.customScopeAimTime(gun);
            if (custom.isPresent()) {
                return custom.get();
            }
        }
        return cache.getCache(id);
    }

    @Inject(method = "aim", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$onAim(boolean aiming, CallbackInfo ci) {
        if (aiming) {
            if (Config.ADS_INTERRUPT_SPRINT.get() && player.isSprinting()) {
                player.setSprinting(false);
            }
            return;
        }

        if (!Config.AUTO_AIM_WHEN_PEEKING.get()) return;
        if (PeekState.isPeeking) {
            ci.cancel();
        }
    }
}
