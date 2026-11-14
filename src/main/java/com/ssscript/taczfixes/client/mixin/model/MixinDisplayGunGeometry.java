package com.ssscript.taczfixes.client.mixin.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.common.data.GunPosAlterManager;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.ssscript.taczfixes.common.util.PosAlterStorage;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.DisplayGunModelInstance;
import com.tacz.guns.client.model.ExternalGunModel;
import com.tacz.guns.client.renderer.block.DisplayGunGeometry;
import com.tacz.guns.client.renderer.item.SbmGunTransforms;
import com.tacz.guns.client.resource.ExternalModelAssets;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * 第三人称/SBM: 让自定义槽配件走 TaCZ 本体同一条配件遍历管线。
 * TaCZ 的 forEachAttachmentModel 只遍历标准 AttachmentType 槽位(捕获与实时绘制都经过它);
 * 这里在 RETURN 追加自定义槽配件, 用相同的 bone pivot + ExternalGunModel 交给 visitor,
 * 因此会和原生配件一样跟随 SBM 骨骼动画。
 */
@Mixin(value = DisplayGunGeometry.class, remap = false)
public abstract class MixinDisplayGunGeometry {

    @Inject(method = "forEachAttachmentModel", at = @At("RETURN"), remap = false)
    private static void taczfixes$visitCustomSlotAttachments(DisplayGunModelInstance instance, PoseStack pose,
                                                             boolean useLod, IGun gun, ItemStack gunStack,
                                                             EnumMap<AttachmentType, ClientAttachmentIndex> attachments,
                                                             DisplayGunGeometry.AttachmentModelVisitor visitor,
                                                             CallbackInfo ci) {
        if (gun == null || gunStack == null || gunStack.isEmpty() || visitor == null) return;
        Map<String, CustomSlotManager.SlotEntry> slots = CustomSlotManager.getEntries(gunStack);
        if (slots.isEmpty()) return;
        for (Map.Entry<String, CustomSlotManager.SlotEntry> entry : slots.entrySet()) {
            String slotId = entry.getKey();
            CustomSlotManager.SlotEntry slot = entry.getValue();
            // 依赖其它配件模型内定位组的挂载链暂不走 SBM(其骨骼不在枪械模型里)。
            if (!slot.source().isEmpty()) continue;
            ItemStack item = CustomSlotStorage.get(gunStack, slotId);
            if (item.isEmpty()) continue;
            IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
            if (attachment == null) continue;
            ResourceLocation id = attachment.getAttachmentId(item);
            if (id == null) continue;
            ClientAttachmentIndex index = TimelessAPI.getClientAttachmentIndex(id).orElse(null);
            if (index == null) continue;
            // 自定义槽配件不在 TaCZ 的 attachmentModels 里, 外部模型从未 prepare, 这里先准备。
            ExternalModelAssets assets = index.getExternalModels();
            if (assets != null) assets.prepare();
            ExternalGunModel external = index.getExternalModel();
            ResourceLocation texture = index.getModelTexture();
            if (external == null || texture == null) continue;
            String nodeName = taczfixes$mountNodeName(slot, slotId);
            int bone = instance.getIndex(nodeName);
            if (bone < 0) continue;
            PoseStack local = new PoseStack();
            local.last().pose().mul(pose.last().pose());
            local.last().normal().mul(pose.last().normal());
            SbmGunTransforms.applyBonePivot(local, instance, bone);
            taczfixes$applySlotOffsets(local, gunStack, slotId);
            visitor.accept(external, texture, item, local);
        }
    }

    @Unique
    private static String taczfixes$mountNodeName(CustomSlotManager.SlotEntry slot, String slotId) {
        AttachmentType mountType = slot.mountType();
        if (mountType != null && mountType != AttachmentType.NONE) {
            return mountType.name().toLowerCase(Locale.ROOT) + "_pos";
        }
        String modelSlotId = slot.modelSlotId() != null && !slot.modelSlotId().isEmpty()
                ? slot.modelSlotId() : slotId;
        return modelSlotId + "_pos";
    }

    @Unique
    private static void taczfixes$applySlotOffsets(PoseStack pose, ItemStack gunStack, String slotId) {
        if (slotId == null || slotId.isEmpty()) return;
        if (GunPosAlterManager.isEnabled(gunStack, slotId)) {
            float z = PosAlterStorage.get(gunStack, slotId);
            if (z != 0.0F) {
                pose.translate(0.0F, 0.0F, z / 16.0F);
            }
        }
    }
}
