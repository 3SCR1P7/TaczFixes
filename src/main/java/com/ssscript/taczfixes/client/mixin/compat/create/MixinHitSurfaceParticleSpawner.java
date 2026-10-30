package com.ssscript.taczfixes.client.mixin.compat.create;

import com.ssscript.taczfixes.client.compat.create.ContraptionHitEffects;
import com.tacz.guns.client.particle.HitSurfaceParticleSpawner;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = HitSurfaceParticleSpawner.class, remap = false)
public abstract class MixinHitSurfaceParticleSpawner {
    @Redirect(method = "spawn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"),
            remap = true)
    private static BlockState taczfixes$contraptionSurfaceState(ClientLevel level, BlockPos pos) {
        return ContraptionHitEffects.overrideState(level, pos);
    }
}
