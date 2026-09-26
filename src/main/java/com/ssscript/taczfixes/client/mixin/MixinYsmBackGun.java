package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.ssscript.taczfixes.common.compat.YsmCompatibilityDiagnostics;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {"com.elfmcys.yesstevemodel.OOO0O0O0oo0ooooo00oOOOO0"}, remap = false)
public abstract class MixinYsmBackGun {
    @Inject(method = {"Oo0Oo0o00O00Oo0OOoOOoooo(Lnet/minecraft/world/item/ItemStack;Lcom/elfmcys/yesstevemodel/OOOO0O0O000O000000oOOO0o;Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;IF)V"}, at = {@At("HEAD")}, cancellable = true, require = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = false)
    private static void taczDualWield$hideYsmBackGun(ItemStack stack, @Coerce Object model, LivingEntity entity, PoseStack poseStack, int packedLight, float partialTick, CallbackInfo callbackInfo) {
        YsmCompatibilityDiagnostics.markBackGunHook();
        if (entity != null && DualWieldEligibility.isDualWielding(entity)) {
            callbackInfo.cancel();
        }
    }
}
