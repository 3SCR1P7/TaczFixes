package com.ssscript.taczfixes.mixin;

import com.tacz.guns.network.message.ClientMessageRefitGun;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientMessageRefitGun.class)
public interface MixinClientMessageRefitGunAccessor {
    @Accessor(value = "attachmentSlotIndex", remap = false)
    int getAttachmentSlotIndex();
}
