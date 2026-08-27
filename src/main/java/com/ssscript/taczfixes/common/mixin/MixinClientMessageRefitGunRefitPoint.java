package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.network.message.ClientMessageRefitGun;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientMessageRefitGun.class)
public class MixinClientMessageRefitGunRefitPoint {

    @Inject(method = "lambda$handle$0", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$checkRefitPoint(NetworkEvent.Context context, ClientMessageRefitGun message,
                                                  CallbackInfo ci) {
        ServerPlayer sender = context.getSender();
        if (sender == null) return;
        MixinClientMessageRefitGunAccessor accessor = (MixinClientMessageRefitGunAccessor) message;
        int slotIndex = accessor.getAttachmentSlotIndex();
        int gunSlotIndex = accessor.getGunSlotIndex();
        AttachmentType type = accessor.getAttachmentType();
        ItemStack attachmentStack = sender.getInventory().getItem(slotIndex);
        ItemStack gunStack = sender.getInventory().getItem(gunSlotIndex);
        IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentStack);
        if (attachment == null) return;
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return;
        if (type == null) type = attachment.getType(attachmentStack);
        ItemStack installed = gun.getAttachment(gunStack, type);
        int oldConsume = installed.isEmpty() ? 0 : AttachmentTaczFixesManager.getRefitPointConsume(installed);
        Integer total = TaczFixesDataManager.getGunRefitPoint(gunStack);
        if (total != null) {
            int used = AttachmentTaczFixesManager.getRefitPointUsed(gunStack);
            int add = AttachmentTaczFixesManager.getRefitPointConsume(attachmentStack);
            if (used + add > total + oldConsume) {
                ci.cancel();
            }
        }
    }
}