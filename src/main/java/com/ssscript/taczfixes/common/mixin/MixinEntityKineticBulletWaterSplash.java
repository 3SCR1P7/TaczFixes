package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.WaterSplashEffect;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.block.BlockRayTrace;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityKineticBullet.class)
public abstract class MixinEntityKineticBulletWaterSplash {

    @Inject(method = "onBulletTick", at = @At("HEAD"), remap = false)
    private void taczfixes$splashOnWaterEntry(CallbackInfo ci) {
        Entity bullet = (Entity) (Object) this;
        if (bullet.level().isClientSide || bullet.isInWater()
                || !(bullet.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 start = bullet.position();
        Vec3 end = start.add(bullet.getDeltaMovement());
        if (start.equals(end)) {
            return;
        }
        HitResult result = BlockRayTrace.rayTraceBlocks(serverLevel,
                new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, bullet));
        if (!(result instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) {
            return;
        }
        FluidState fluid = serverLevel.getFluidState(hit.getBlockPos());
        if (!fluid.is(FluidTags.WATER)) {
            return;
        }
        WaterSplashEffect.spawn(serverLevel, hit.getLocation(), bullet.getDeltaMovement().length());
    }
}
