package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.handler.GunEnchantmentHandler;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 常凪冻结: 服务端不再广播被冻结实体发出的声音(环境音/受伤/死亡/脚步声等)。 */
@Mixin(Entity.class)
public class MixinEntityPlaySoundFreeze {
    @Inject(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at = @At("HEAD"), cancellable = true)
    private void taczfixes$muteFrozenEntity(SoundEvent sound, float volume, float pitch, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!self.level().isClientSide && GunEnchantmentHandler.isFrozen(self)) {
            ci.cancel();
        }
    }
}