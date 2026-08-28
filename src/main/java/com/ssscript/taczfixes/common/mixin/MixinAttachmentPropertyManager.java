package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AttachmentPropertyManager.class)
public abstract class MixinAttachmentPropertyManager {

    @Inject(method = "postChangeEvent", at = @At("HEAD"), remap = false)
    private static void taczfixes$cascadeDependents(LivingEntity shooter, ItemStack gunItem, CallbackInfo ci) {
        if (!(shooter instanceof net.minecraft.server.level.ServerPlayer player)) return;
        CustomSlotManager.cascadeUnloadDependents(player, gunItem);
        CustomSlotManager.cascadeUnloadConflicts(player, gunItem);
    }
}