package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.compat.ArcanaFlashlightBridge;
import group.taczexpands.dist.DKdo8Awk;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Arcana 世界手电: 查找手电配置时兜底自定义槽位。 */
@Mixin(targets = "group/taczexpands/dist/B0UzWSV4", remap = false)
public class MixinArcanaFlashlightWorld {

    @Redirect(method = "*", at = @At(value = "INVOKE",
            target = "Lgroup/taczexpands/common/accessor/IAccessorAttachmentData;UyDOzO7w" +
                    "(Lnet/minecraft/world/item/ItemStack;)" +
                    "Lgroup/taczexpands/dist/DKdo8Awk;",
            remap = false), require = 0, remap = false)
    private DKdo8Awk taczfixes$customSlotFlashlight(ItemStack stack) {
        return ArcanaFlashlightBridge.resolveFlashlight(stack);
    }
}
