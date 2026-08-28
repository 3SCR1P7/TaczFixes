package com.ssscript.taczfixes.common.mixin;

import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.network.message.ClientMessageRefitGun;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientMessageRefitGun.class)
public interface MixinClientMessageRefitGunAccessor {
    @Accessor(value = "attachmentSlotIndex", remap = false)
    int getAttachmentSlotIndex();

    @Accessor(value = "gunSlotIndex", remap = false)
    int getGunSlotIndex();

    @Accessor(value = "attachmentType", remap = false)
    AttachmentType getAttachmentType();
}
