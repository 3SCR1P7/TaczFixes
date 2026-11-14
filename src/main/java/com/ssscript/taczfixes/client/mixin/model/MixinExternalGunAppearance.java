package com.ssscript.taczfixes.client.mixin.model;

import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.renderer.external.ExternalGunAppearance;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.index.ClientAttachmentVariantIndex;
import com.tacz.guns.client.resource.index.ClientSlotAdapterIndex;
import com.tacz.guns.util.SlotAdapterHelper;
import com.tacz.guns.util.VariantHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

/**
 * 把自定义槽配件加入 TaCZ 的外部外观附件列表:
 * 该列表参与 SBM 静态网格的 GeometryKey(决定缓存是否重建)与外部模型预加载,
 * 否则装/卸自定义槽配件不会触发网格重建, 第三人称时隐时现。
 * 这里只加描述条目用于 key/预加载, 实际绘制由 MixinDisplayGunGeometry 负责。
 */
@Mixin(value = ExternalGunAppearance.class, remap = false)
public abstract class MixinExternalGunAppearance {

    @Inject(method = "attachmentModels", at = @At("RETURN"), remap = false)
    private static void taczfixes$addCustomSlotKeys(IGun gun, ItemStack gunStack,
                                                    CallbackInfoReturnable<List<ExternalGunAppearance.AttachmentModels>> cir) {
        List<ExternalGunAppearance.AttachmentModels> list = cir.getReturnValue();
        if (list == null || gun == null || gunStack == null || gunStack.isEmpty()) return;
        Map<String, CustomSlotManager.SlotEntry> slots = CustomSlotManager.getEntries(gunStack);
        if (slots.isEmpty()) return;
        for (Map.Entry<String, CustomSlotManager.SlotEntry> entry : slots.entrySet()) {
            String slotId = entry.getKey();
            CustomSlotManager.SlotEntry slot = entry.getValue();
            if (!slot.source().isEmpty()) continue;
            ItemStack item = CustomSlotStorage.get(gunStack, slotId);
            if (item.isEmpty()) continue;
            IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
            if (attachment == null) continue;
            ResourceLocation id = attachment.getAttachmentId(item);
            if (id == null) continue;
            ClientAttachmentIndex index = TimelessAPI.getClientAttachmentIndex(id).orElse(null);
            if (index == null || index.getExternalModels() == null) continue;
            ClientAttachmentVariantIndex variant = TimelessAPI.getClientAttachmentVariantIndex(item).orElse(null);
            ResourceLocation variantId = variant == null ? null : VariantHelper.getVariant(item);
            AttachmentType type = attachment.getType(item);
            if (type == null || type == AttachmentType.NONE) {
                type = slot.mountType() != null && slot.mountType() != AttachmentType.NONE
                        ? slot.mountType() : AttachmentType.SCOPE;
            }
            ResourceLocation adapterId = SlotAdapterHelper.getEffectiveSlotAdapter(gunStack, type, id);
            ClientSlotAdapterIndex adapter = adapterId == null ? null
                    : TimelessAPI.getClientSlotAdapterIndex(adapterId).orElse(null);
            ExternalGunAppearance.AttachmentKey key =
                    new ExternalGunAppearance.AttachmentKey(type, id, variantId, adapterId);
            list.add(new ExternalGunAppearance.AttachmentModels(key, index, variant, adapter));
        }
    }
}
