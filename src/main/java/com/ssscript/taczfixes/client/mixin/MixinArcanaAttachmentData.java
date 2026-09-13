package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.item.ModernKineticGunItem;
import group.taczexpands.common.accessor.IAccessorAttachmentData;
import group.taczexpands.dist.YKThsud9;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "group/taczexpands/dist/WFkyOIm9", remap = false)
public class MixinArcanaAttachmentData {

    @Redirect(method = "*", at = @At(value = "INVOKE",
            target = "Lgroup/taczexpands/common/accessor/IAccessorAttachmentData;hnV6aO6D" +
                    "(Lnet/minecraft/world/item/ItemStack;" +
                    "Lcom/tacz/guns/api/item/attachment/AttachmentType;)" +
                    "Lgroup/taczexpands/dist/YKThsud9;",
            remap = false), remap = false)
    private YKThsud9 taczfixes$useActiveCustomScope(ItemStack stack, AttachmentType type) {
        YKThsud9 custom = taczfixes$activeCustomScopeData(stack);
        if (custom != null) {
            return custom;
        }
        return IAccessorAttachmentData.hnV6aO6D(stack, type);
    }

    @Unique
    private static YKThsud9 taczfixes$activeCustomScopeData(ItemStack stack) {
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
                .map(index -> IAccessorAttachmentData.RdZw8JA8(index.getData()))
                .orElse(null);
    }
}