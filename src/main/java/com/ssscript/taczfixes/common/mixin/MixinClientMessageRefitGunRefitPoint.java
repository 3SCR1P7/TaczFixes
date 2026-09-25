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
                return;
            }
        }
        if (com.ssscript.taczfixes.common.util.VirtualAttachments.isActive(sender)) {
            // 虚拟配件模式: 免费安装(不消耗背包), 旧配件直接消失(不返还背包)
            if (gun.hasAttachmentLock(gunStack)) return;
            if (!gun.allowAttachment(gunStack, attachmentStack)) return;
            AttachmentType realType = attachment.getType(attachmentStack);
            net.minecraft.resources.ResourceLocation previousId = gun.getAttachmentId(gunStack, realType);
            ItemStack copy = attachmentStack.copy();
            com.tacz.guns.util.VirtualOemAttachment.mark(copy);
            com.ssscript.taczfixes.common.compat.ArcanaSkillBridge.markGenerated(copy);
            gun.installAttachment(gunStack, copy);
            com.ssscript.taczfixes.common.compat.ArcanaSkillBridge.triggerChangeAttachment(sender,
                    com.tacz.guns.api.DefaultAssets.isEmptyAttachmentId(previousId) ? null : previousId.toString(),
                    attachment.getAttachmentId(attachmentStack).toString());
            com.tacz.guns.resource.modifier.AttachmentPropertyManager.postChangeEvent(sender, gunStack);
            if (realType == AttachmentType.EXTENDED_MAG) {
                gun.dropAllAmmo(sender, gunStack);
            }
            sender.inventoryMenu.broadcastChanges();
            com.tacz.guns.network.NetworkHandler.sendToClientPlayer(
                    new com.tacz.guns.network.message.ServerMessageRefreshRefitScreen(), sender);
            ci.cancel();
            return;
        }
    }
}