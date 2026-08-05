package com.example.taczfixes.mixin;

import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.item.AttachmentItem;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

@Mixin(targets = "com.mafuyu404.taczaddon.common.LiberateAttachment", remap = false)
public class LiberateAttachmentMixin {
    @Inject(method = "getAttachmentItems", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void onGetAttachmentItems(CallbackInfoReturnable<ArrayList<ItemStack>> cir) {
        try {
            AttachmentType moduleType = AttachmentType.valueOf("MODULE");
            NonNullList<ItemStack> moduleItems = AttachmentItem.fillItemCategory(moduleType);
            ArrayList<ItemStack> items = cir.getReturnValue();
            items.addAll(moduleItems);
            cir.setReturnValue(items);
        } catch (IllegalArgumentException e) {
            // MODULE type doesn't exist (Arcana not loaded)
        }
    }
}
