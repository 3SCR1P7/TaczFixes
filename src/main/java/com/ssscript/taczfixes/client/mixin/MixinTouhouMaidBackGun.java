package com.ssscript.taczfixes.client.mixin;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {"com.github.tartaricacid.touhoulittlemaid.compat.gun.tacz.TacCompat"}, remap = false)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/mixin/compat/MixinTouhouMaidBackGun.class */
public abstract class MixinTouhouMaidBackGun {
    @Inject(method = {"renderBackGun(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ItemStack;Lcom/github/tartaricacid/touhoulittlemaid/api/entity/IMaid;)V"}, at = {@At("HEAD")}, cancellable = true, require = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = false)
    private static void taczDualWield$hideBedrockBackGun(PoseStack poseStack, MultiBufferSource buffer, int packedLight, ItemStack stack, IMaid maid, CallbackInfo callbackInfo) {
        if (isDualWielding(maid)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = {"renderBackGun(Lnet/minecraft/world/item/ItemStack;Lcom/github/tartaricacid/touhoulittlemaid/geckolib3/geo/animated/ILocationModel;Lcom/github/tartaricacid/touhoulittlemaid/api/entity/IMaid;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"}, at = {@At("HEAD")}, cancellable = true, require = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = false)
    private static void taczDualWield$hideGeckoBackGun(ItemStack stack, ILocationModel model, IMaid maid, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo callbackInfo) {
        if (isDualWielding(maid)) {
            callbackInfo.cancel();
        }
    }

    private static boolean isDualWielding(IMaid maid) {
        Mob entity = maid == null ? null : maid.asEntity();
        return entity != null && DualWieldEligibility.isDualWielding(entity);
    }
}
