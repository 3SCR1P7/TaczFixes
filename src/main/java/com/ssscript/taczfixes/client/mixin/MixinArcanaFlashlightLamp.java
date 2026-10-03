package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.ArcanaFlashlightBridge;
import group.taczexpands.dist.DKdo8Awk;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Arcana 手电灯具: 查找手电配置时兜底自定义槽位。 */
@Mixin(targets = "group/taczexpands/dist/R131009", remap = false)
public class MixinArcanaFlashlightLamp {

    @Redirect(method = "*", at = @At(value = "INVOKE",
            target = "Lgroup/taczexpands/common/accessor/IAccessorAttachmentData;UyDOzO7w" +
                    "(Lnet/minecraft/world/item/ItemStack;)" +
                    "Lgroup/taczexpands/dist/DKdo8Awk;",
            remap = false), require = 0, remap = false)
    private static DKdo8Awk taczfixes$customSlotFlashlight(ItemStack stack) {
        return ArcanaFlashlightBridge.resolveFlashlight(stack);
    }
}
