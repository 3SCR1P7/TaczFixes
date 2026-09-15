package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = {GunItemRendererWrapper.class}, remap = false)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/mixin/MixinGunItemRendererWrapperAccessor.class */
public interface MixinGunItemRendererWrapperAccessor {
    @Invoker("cacheMuzzlePosition")
    static void dualWield$cacheMuzzlePosition(PoseStack poseStack, BedrockGunModel model) {
        throw new AssertionError("Mixin invoker was not applied");
    }
}
