package com.ssscript.taczfixes.client.mixin.compat.arcana;

import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Arcana 的镜内放大通过额外渲染一层世界画面实现, 但它的开关判断读取的是原生
 * SCOPE 槽的瞄具。自定义槽的 scope 激活时, 把该判断重定向到自定义瞄具, 使其
 * 也能绘制镜内画面。是否开启仍由 VR4u5uBF() 按当前瞄具视图决定。
 */
@Mixin(targets = "group/taczexpands/dist/cMBgIfn3", remap = false)
public class MixinArcanaLensLayerGate {

    @Redirect(method = "evt6ogQF", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/api/item/IGun;getAttachmentId" +
                    "(Lnet/minecraft/world/item/ItemStack;" +
                    "Lcom/tacz/guns/api/item/attachment/AttachmentType;)" +
                    "Lnet/minecraft/resources/ResourceLocation;",
            remap = false), remap = false)
    private static ResourceLocation taczfixes$customScopeId(IGun gun, ItemStack stack, AttachmentType type) {
        if (type == AttachmentType.SCOPE) {
            ResourceLocation custom = ScopeSwitchState.activeCustomScopeId(stack);
            if (custom != null) {
                return custom;
            }
        }
        return gun.getAttachmentId(stack, type);
    }
}
