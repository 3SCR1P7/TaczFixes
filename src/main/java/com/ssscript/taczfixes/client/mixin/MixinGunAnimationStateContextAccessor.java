package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取枪械状态机当前绑定的枪械物品，用于把输入转发给同一把枪上的配件状态机。 */
@SuppressWarnings({"target", "mapping", "public-target", "unresolvable-target", "visibility", "constraints", "overwrite", "default-package", "raw-types"})
@Mixin(value = {GunAnimationStateContext.class}, remap = false)
public interface MixinGunAnimationStateContextAccessor {
    @Accessor(value = "currentGunItem", remap = false)
    ItemStack taczfixes$getCurrentGunItem();
}
