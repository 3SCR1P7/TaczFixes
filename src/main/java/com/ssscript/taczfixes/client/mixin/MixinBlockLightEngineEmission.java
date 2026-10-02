package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.render.ClientGunLightManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 方块光照引擎查询发光值时叠加枪械动态光照(纯客户端, 服务端列表恒为空)。 */
@Mixin(BlockLightEngine.class)
public abstract class MixinBlockLightEngineEmission {

    @Inject(method = "m_284436_", at = @At("RETURN"), cancellable = true, remap = false)
    private void taczfixes$gunLightEmission(long packedPos, BlockState state, CallbackInfoReturnable<Integer> callback) {
        int level = ClientGunLightManager.emissionAt(packedPos);
        if (level > callback.getReturnValueI()) {
            callback.setReturnValue(level);
        }
    }
}
