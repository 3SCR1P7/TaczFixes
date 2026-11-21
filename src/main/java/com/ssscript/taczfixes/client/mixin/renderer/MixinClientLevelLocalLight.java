package com.ssscript.taczfixes.client.mixin.renderer;

import com.ssscript.taczfixes.client.render.PbrRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 客户端方块变化(放/拆光源、放/拆遮挡物)时, 让 PBR 局部光在下一帧立刻重扫,
 * 而不是等定时兜底或玩家移动。
 */
@Mixin(ClientLevel.class)
public abstract class MixinClientLevelLocalLight {

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"))
    private void taczfixes$invalidateLocalLights(BlockPos pos, BlockState state, int flags, int recursionLeft,
                                                 CallbackInfoReturnable<Boolean> cir) {
        PbrRenderer.invalidateLocalLights(pos);
    }
}
