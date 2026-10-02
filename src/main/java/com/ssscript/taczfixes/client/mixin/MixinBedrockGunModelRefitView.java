package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.client.util.CustomSlotGuiState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Mixin(BedrockGunModel.class)
public abstract class MixinBedrockGunModelRefitView {

    private static long taczfixes$viewCallCount;

    @Inject(method = "getRefitAttachmentViewPath", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$customSlotRefitView(AttachmentType type,
                                               CallbackInfoReturnable<List<BedrockPart>> cir) {
        String slotId = CustomSlotGuiState.get();
        if (slotId == null && CustomSlotGuiState.getViewFromSlot() == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack gunStack = mc.player.getMainHandItem();
        IGun igun = IGun.getIGunOrNull(gunStack);
        if (igun == null) return;
        CustomSlotManager.SlotEntry entry = slotId == null ? null : CustomSlotManager.getEntry(gunStack, slotId);
        if (slotId != null && entry == null) return;
        if (slotId == null) {
            // 界面内: 视图切换缓动结束即可清理; 退出界面: 需等收枪缓动(opening)结束再清理,
            // 否则收枪过程中路径回退到 tacz 原始(可能为 null), 枪械会从 0,0,0 缓动回。
            boolean screenOpen = Minecraft.getInstance().screen
                    instanceof com.tacz.guns.client.gui.GunRefitScreen;
            if (screenOpen ? RefitTransform.getTransformProgress() >= 1f
                    : RefitTransform.getOpeningProgress() <= 0f) {
                CustomSlotGuiState.clearViewTransition();
                return;
            }
        }

        boolean oldCall = ((++taczfixes$viewCallCount) & 1L) != 0L;
        List<BedrockPart> path = oldCall
                ? resolveViewFrom((BedrockModel) (Object) this, gunStack)
                : resolveToPath((BedrockModel) (Object) this, gunStack, slotId, entry, type);
        if (path != null) {
            cir.setReturnValue(path);
        }
    }

    private static List<BedrockPart> resolveToPath(BedrockModel self, ItemStack gunStack, String slotId,
                                                   CustomSlotManager.SlotEntry entry, AttachmentType type) {
        if (slotId != null) return resolveSlotView(self, gunStack, slotId, entry, pathOf(self, "refit_view"));
        if (type == null || type == AttachmentType.NONE) return pathOf(self, "refit_view");
        List<BedrockPart> path = pathOf(self, "refit_" + type.name().toLowerCase(Locale.US) + "_view");
        return path != null ? path : pathOf(self, "refit_view");
    }

    private static List<BedrockPart> resolveViewFrom(BedrockModel self, ItemStack gunStack) {
        List<BedrockPart> fallback = pathOf(self, "refit_view");
        AttachmentType fromType = CustomSlotGuiState.getViewFromType();
        if (fromType != null && fromType != AttachmentType.NONE) {
            List<BedrockPart> typePath = pathOf(self, "refit_" + fromType.name().toLowerCase(Locale.US) + "_view");
            if (typePath != null) {
                fallback = typePath;
            }
        }
        String fromSlot = CustomSlotGuiState.getViewFromSlot();
        if (fromSlot != null) {
            return resolveSlotView(self, gunStack, fromSlot,
                    CustomSlotManager.getEntry(gunStack, fromSlot), fallback);
        }
        return fallback;
    }

    /**
     * 解析槽位的改装视角: 由配件提供的槽位沿"提供者安装位置"逐级上溯,
     * 最终使用顶层槽位(或提供者所在标准槽类型)的改装视角。
     */
    private static List<BedrockPart> resolveSlotView(BedrockModel self, ItemStack gunStack, String slotId,
                                                     CustomSlotManager.SlotEntry entry, List<BedrockPart> fallback) {
        String viewSlot = slotId;
        CustomSlotManager.SlotEntry current = entry;
        Set<String> visited = new HashSet<>();
        while (current != null && current.source() != null && !current.source().isEmpty()
                && viewSlot != null && visited.add(viewSlot)) {
            if (current.mountSlotId() != null && !current.mountSlotId().isEmpty()) {
                viewSlot = current.mountSlotId();
                current = CustomSlotManager.getEntry(gunStack, viewSlot);
                continue;
            }
            if (current.mountType() != null && current.mountType() != AttachmentType.NONE) {
                List<BedrockPart> typePath = pathOf(self,
                        "refit_" + current.mountType().name().toLowerCase(Locale.US) + "_view");
                return typePath != null ? typePath : fallback;
            }
            break;
        }
        if (viewSlot != null) {
            List<BedrockPart> path = pathOf(self, "refit_" + viewSlot.toLowerCase(Locale.US) + "_view");
            if (path != null) return path;
            CustomSlotDefinition viewDef = current != null ? current.def() : null;
            if (viewDef == null) {
                viewDef = CustomSlotManager.getSlot(gunStack, viewSlot);
            }
            if (viewDef != null && viewDef.type != null && !viewDef.type.isEmpty()) {
                path = pathOf(self, "refit_" + viewDef.type.toLowerCase(Locale.US) + "_view");
                if (path != null) return path;
            }
        }
        return fallback;
    }

    private static List<BedrockPart> pathOf(BedrockModel model, String nodeName) {
        BedrockPart node = model.getNode(nodeName);
        if (node == null) return null;
        List<BedrockPart> path = new ArrayList<>();
        for (BedrockPart cur = node; cur != null; cur = cur.getParent()) {
            path.add(cur);
        }
        Collections.reverse(path);
        return path;
    }
}
