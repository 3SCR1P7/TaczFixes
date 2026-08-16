package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.data.CustomSlotDefinition;
import com.ssscript.taczfixes.data.CustomSlotManager;
import com.ssscript.taczfixes.util.CustomSlotGuiState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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
        ResourceLocation gunId = igun.getGunId(gunStack);
        CustomSlotDefinition def = slotId == null ? null : CustomSlotManager.getSlot(gunId, slotId);
        if (slotId != null && def == null) return;
        if (slotId == null && RefitTransform.getTransformProgress() >= 1f) {
            CustomSlotGuiState.clearViewTransition();
            return;
        }

        boolean oldCall = ((++taczfixes$viewCallCount) & 1L) != 0L;
        List<BedrockPart> path = oldCall
                ? resolveViewFrom((BedrockModel) (Object) this, gunId)
                : resolveToPath((BedrockModel) (Object) this, slotId, def, type);
        if (path != null) {
            cir.setReturnValue(path);
        }
    }

    private static List<BedrockPart> resolveToPath(BedrockModel self, String slotId,
                                                   CustomSlotDefinition def, AttachmentType type) {
        if (slotId != null) return resolveRefitPath(self, slotId, def);
        if (type == null || type == AttachmentType.NONE) return pathOf(self, "refit_view");
        List<BedrockPart> path = pathOf(self, "refit_" + type.name().toLowerCase(Locale.US) + "_view");
        return path != null ? path : pathOf(self, "refit_view");
    }

    private static List<BedrockPart> resolveViewFrom(BedrockModel self, ResourceLocation gunId) {
        String fromSlot = CustomSlotGuiState.getViewFromSlot();
        if (fromSlot != null) {
            CustomSlotDefinition fromDef = CustomSlotManager.getSlot(gunId, fromSlot);
            List<BedrockPart> path = pathOf(self, "refit_" + fromSlot.toLowerCase(Locale.US) + "_view");
            if (path != null) return path;
            if (fromDef != null && fromDef.type != null && !fromDef.type.isEmpty()) {
                path = pathOf(self, "refit_" + fromDef.type.toLowerCase(Locale.US) + "_view");
                if (path != null) return path;
            }
        }
        AttachmentType fromType = CustomSlotGuiState.getViewFromType();
        if (fromType != null && fromType != AttachmentType.NONE) {
            List<BedrockPart> path = pathOf(self, "refit_" + fromType.name().toLowerCase(Locale.US) + "_view");
            if (path != null) return path;
        }
        return pathOf(self, "refit_view");
    }

    private static List<BedrockPart> resolveRefitPath(BedrockModel self, String slotId, CustomSlotDefinition def) {
        List<BedrockPart> path = pathOf(self, "refit_" + slotId.toLowerCase(Locale.US) + "_view");
        if (path != null) return path;
        if (def.type != null && !def.type.isEmpty()) {
            path = pathOf(self, "refit_" + def.type.toLowerCase(Locale.US) + "_view");
            if (path != null) return path;
        }
        return pathOf(self, "refit_view");
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