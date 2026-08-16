package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.data.CustomSlotDefinition;
import com.ssscript.taczfixes.data.CustomSlotManager;
import com.ssscript.taczfixes.util.CustomSlotStorage;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Consumer;

@Mixin(AttachmentDataUtils.class)
public abstract class MixinAttachmentDataUtils {

    @Inject(method = "getAllAttachmentData", at = @At("TAIL"), remap = false)
    private static void taczfixes$addCustomSlotAttachmentData(ItemStack gunItem, GunData gunData,
                                                              Consumer<AttachmentData> consumer, CallbackInfo ci) {
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null) return;
        ResourceLocation gunId = gun.getGunId(gunItem);
        Map<String, CustomSlotDefinition> slots = CustomSlotManager.getSlots(gunId);
        if (slots.isEmpty()) return;
        for (Map.Entry<String, CustomSlotDefinition> entry : slots.entrySet()) {
            ItemStack item = CustomSlotStorage.get(gunItem, entry.getKey());
            if (item.isEmpty()) continue;
            IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
            if (attachment == null) continue;
            ResourceLocation attachmentId = attachment.getAttachmentId(item);
            AttachmentData data = gunData.getExclusiveAttachments().get(attachmentId);
            if (data == null) {
                data = com.tacz.guns.resource.CommonAssetsManager.getInstance().getAttachmentData(attachmentId);
            }
            if (data == null) {
                data = com.tacz.guns.api.TimelessAPI.getCommonAttachmentIndex(attachmentId)
                        .map(com.tacz.guns.resource.index.CommonAttachmentIndex::getData)
                        .orElse(null);
            }
            if (data != null) {
                consumer.accept(data);
            }
        }
    }
}
