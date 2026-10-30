package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.EntityUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityKineticBullet.class)
public class MixinBulletPierceGlass {
    @Shadow(remap = false)
    private int pierce;

    @Shadow(remap = false)
    private boolean explosion;

    @Inject(method = "onBulletTick", at = @At("HEAD"), remap = false)
    private void taczfixes$preBreakGlass(CallbackInfo ci) {
        if (!Config.BULLET_PIERCE_GLASS.get() || !AmmoConfig.DESTROY_GLASS.get() || explosion) {
            return;
        }
        EntityKineticBullet bullet = (EntityKineticBullet) (Object) this;
        if (bullet.level().isClientSide()) {
            return;
        }

        Vec3 start = bullet.position();
        Vec3 end = start.add(bullet.getDeltaMovement());
        while (pierce > 0) {
            BlockHitResult hit = bullet.level().clip(
                    new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, bullet));
            if (hit.getType() == HitResult.Type.MISS) {
                return;
            }
            BlockState state = bullet.level().getBlockState(hit.getBlockPos());
            if (!isGlass(state)) {
                return;
            }
            if (!EntityUtil.findEntitiesOnPath(bullet, start, hit.getLocation()).isEmpty()) {
                return;
            }

            AmmoHitBlockEvent event = new AmmoHitBlockEvent(bullet.level(), hit, state, bullet);
            if (MinecraftForge.EVENT_BUS.post(event)) {
                return;
            }
            if (isGlass(bullet.level().getBlockState(hit.getBlockPos()))) {
                bullet.level().destroyBlock(hit.getBlockPos(), false, bullet.getOwner());
            }
            pierce = Math.max(0, pierce - 1);
            start = hit.getLocation();
        }
    }

    private static boolean isGlass(BlockState state) {
        return state.getBlock() instanceof AbstractGlassBlock
                || state.getBlock() instanceof StainedGlassPaneBlock
                || (state.getBlock() instanceof IronBarsBlock
                && NoteBlockInstrument.HAT.equals(state.instrument()));
    }
}
