package com.ssscript.taczfixes.client.mixin.compat.ysm;

import com.ssscript.taczfixes.common.compat.YsmDualWieldCompat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * YSM 的 hold_mainhand 动画事件只读取主手物品。副手为枪、主手不是枪时替换成副手枪械,
 * 让 YSM 继续选取 TaCZ 的 tac:hold/aim/run 动画。
 */
@Pseudo
@Mixin(targets = {"com.elfmcys.yesstevemodel.oOO0000O0oO0o0OooOOO0oO0"}, remap = false)
public abstract class MixinYsmOffhandHoldAnimation {
    @Redirect(method = {"Oo0Oo0o00O00Oo0OOoOOoooo(Lcom/elfmcys/yesstevemodel/OO00O0o0OooOOOo00OO00o00;Lcom/elfmcys/yesstevemodel/O0Oooo00oOo00O00OoOOOooO;)Lcom/elfmcys/yesstevemodel/O0oOo0OoO0O0o0000o0O00o0;"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;m_21120_(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;", remap = false), require = 0, remap = false)
    private ItemStack taczDualWield$offhandHoldSource(LivingEntity entity, InteractionHand hand) {
        return YsmDualWieldCompat.offhandAnimationSource(entity, entity.getItemInHand(hand));
    }
}
