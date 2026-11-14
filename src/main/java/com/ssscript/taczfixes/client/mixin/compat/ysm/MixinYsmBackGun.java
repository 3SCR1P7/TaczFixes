package com.ssscript.taczfixes.client.mixin.compat.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.common.compat.YsmCompatibilityDiagnostics;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * YSM 的 TaCZ 后置枪械渲染桥 (1.21.1 NeoForge 构建: com.elfmcys.yesstevemodel.o0o0ooO0o00O0oOoooO00OOO)。
 * 双持时隐藏背部枪械, 避免与手中的两把枪重复。
 */
@Pseudo
@Mixin(targets = {"com.elfmcys.yesstevemodel.o0o0ooO0o00O0oOoooO00OOO"}, remap = false)
public abstract class MixinYsmBackGun {
    @Inject(
            method = {"oOo0OO0O0o000OO0O000oo0o(Lnet/minecraft/world/item/ItemStack;Lcom/elfmcys/yesstevemodel/o0ooO0ooO00oo0o00Oo00000;Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;IF)V"},
            at = {@At("HEAD")},
            cancellable = true,
            require = ServerMessageOffhandActionResult.ACTION_SHOOT,
            remap = false)
    private static void taczDualWield$hideYsmBackGun(ItemStack stack, @Coerce Object model, LivingEntity entity,
                                                     PoseStack poseStack, int packedLight, float partialTick,
                                                     CallbackInfo callbackInfo) {
        YsmCompatibilityDiagnostics.markBackGunHook();
        if (entity != null && DualWieldEligibility.isDualWielding(entity)) {
            callbackInfo.cancel();
        }
    }
}
