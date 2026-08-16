package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.util.CustomSlotStorage;
import com.ssscript.taczfixes.util.ScopeSwitchState;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.item.ModernKineticGunItem;
import group.taczexpands.common.accessor.IAccessorAttachmentData;
import group.taczexpands.dist.cvk1FWQh;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "group/taczexpands/dist/binq9IpL", remap = false)
public class MixinArcanaAttachmentData {

    @Redirect(method = "*", at = @At(value = "INVOKE",
            target = "Lgroup/taczexpands/common/accessor/IAccessorAttachmentData;GYNU6f48" +
                    "(Lnet/minecraft/world/item/ItemStack;" +
                    "Lcom/tacz/guns/api/item/attachment/AttachmentType;)" +
                    "Lgroup/taczexpands/dist/cvk1FWQh;",
            remap = false), remap = false)
    private cvk1FWQh taczfixes$useActiveCustomScope(ItemStack stack, AttachmentType type) {
        cvk1FWQh custom = taczfixes$activeCustomScopeData(stack);
        if (custom != null) {
            return custom;
        }
        return IAccessorAttachmentData.GYNU6f48(stack, type);
    }

    @Unique
    private static cvk1FWQh taczfixes$activeCustomScopeData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (!(stack.getItem() instanceof ModernKineticGunItem)) return null;

        String active = ScopeSwitchState.getActiveSlot(stack);
        if (active == null) return null;

        ItemStack scope = CustomSlotStorage.get(stack, active);
        if (scope.isEmpty()) return null;

        IAttachment attachment = IAttachment.getIAttachmentOrNull(scope);
        if (attachment == null) return null;

        ResourceLocation id = attachment.getAttachmentId(scope);
        if (id == null || DefaultAssets.isEmptyAttachmentId(id)) return null;

        return TimelessAPI.getCommonAttachmentIndex(id)
                .map(index -> IAccessorAttachmentData.zTuz5Apz(index.getData()))
                .orElse(null);
    }
}