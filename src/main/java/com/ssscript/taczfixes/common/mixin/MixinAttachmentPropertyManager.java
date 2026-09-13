package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.CustomFireModeManager;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 附件缓存属性构建时激活自定义开火模式:
 * postChangeEvent(举枪/切模式/每 tick 刷新)期间会调用 gunData.getFireModeAdjustData(fireMode) 计算
 * DamageModifier/RpmModifier 等缓存, 该时机不在射击栈内, 需要在此窗口激活。
 */
@Mixin(value = AttachmentPropertyManager.class, remap = false)
public class MixinAttachmentPropertyManager {

    @Inject(method = "postChangeEvent", at = @At("HEAD"), remap = false)
    private static void taczfixes$activateForCache(LivingEntity shooter, ItemStack gunItem, CallbackInfo ci) {
        CustomFireModeManager.activateFor(gunItem);
    }

    @Inject(method = "postChangeEvent", at = @At("RETURN"), remap = false)
    private static void taczfixes$deactivateForCache(LivingEntity shooter, ItemStack gunItem, CallbackInfo ci) {
        CustomFireModeManager.resetActive();
    }
}
