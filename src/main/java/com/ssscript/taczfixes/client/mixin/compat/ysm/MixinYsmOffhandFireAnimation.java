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
 * YSM 的 fire 动画事件同样只读取主手物品。副手为枪、主手不是枪时替换成副手枪械,
 * 让 YSM 播放 TaCZ 的 tac:*:fire 开火动画。
 * <p>1.21.1 NeoForge 构建的 provider: com.elfmcys.yesstevemodel.oO00OO00O0Ooo00O00oOoOoo
 */
@Pseudo
@Mixin(targets = {"com.elfmcys.yesstevemodel.oO00OO00O0Ooo00O00oOoOoo"}, remap = false)
public abstract class MixinYsmOffhandFireAnimation {
    @Redirect(
            method = {"oOo0OO0O0o000OO0O000oo0o(Lcom/elfmcys/yesstevemodel/Oo0Oo0O0OoOoO0oooO00O0o0;Lcom/elfmcys/yesstevemodel/O00o00OOOo00OOOo0O0oOooo;)Lcom/elfmcys/yesstevemodel/O0o0ooO0oOoOoo0O000O0000;"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;", remap = false),
            require = 0,
            remap = false)
    private ItemStack taczDualWield$offhandFireSource(LivingEntity entity, InteractionHand hand) {
        return YsmDualWieldCompat.offhandAnimationSource(entity, entity.getItemInHand(hand));
    }
}
