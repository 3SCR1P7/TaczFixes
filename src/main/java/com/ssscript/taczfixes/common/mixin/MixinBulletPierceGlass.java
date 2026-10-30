package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.EntityUtil;
import com.tacz.guns.util.block.BlockRayTrace;
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
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityKineticBullet.class)
public class MixinBulletPierceGlass {
    @Shadow(remap = false)
    private int pierce;

    @Shadow(remap = false)
    private boolean explosion;

    @ModifyVariable(method = "onBulletTick", at = @At("STORE"), ordinal = 0, remap = false)
    private BlockHitResult taczfixes$pierceGlass(BlockHitResult original) {
        if (!Config.BULLET_PIERCE_GLASS.get() || !AmmoConfig.DESTROY_GLASS.get()
                || explosion || original.getType() == HitResult.Type.MISS) {
            return original;
        }
        EntityKineticBullet bullet = (EntityKineticBullet) (Object) this;
        BlockHitResult result = original;
        Vec3 start = bullet.position();
        Vec3 end = start.add(bullet.getDeltaMovement());
        boolean passedGlass = false;
        while (isGlass(bullet.level().getBlockState(result.getBlockPos()))) {
            if (pierce <= 0) {
                return result;
            }
            BlockState state = bullet.level().getBlockState(result.getBlockPos());
            AmmoHitBlockEvent event = new AmmoHitBlockEvent(bullet.level(), result, state, bullet);
            if (MinecraftForge.EVENT_BUS.post(event)) {
                return result;
            }
            BlockState current = bullet.level().getBlockState(result.getBlockPos());
            if (isGlass(current)) {
                bullet.level().destroyBlock(result.getBlockPos(), false, bullet.getOwner());
            }
            pierce = Math.max(0, pierce - 1);
            passedGlass = true;

            result = BlockRayTrace.rayTraceBlocks(bullet.level(),
                    new ClipContext(result.getLocation(), end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, bullet));
            if (result.getType() == HitResult.Type.MISS) {
                return result;
            }
        }
        if (passedGlass && !EntityUtil.findEntitiesOnPath(bullet, start, result.getLocation()).isEmpty()) {
            return BlockHitResult.miss(result.getLocation(), result.getDirection(), result.getBlockPos());
        }
        return result;
    }

    private static boolean isGlass(BlockState state) {
        return state.getBlock() instanceof AbstractGlassBlock
                || state.getBlock() instanceof StainedGlassPaneBlock
                || (state.getBlock() instanceof IronBarsBlock
                && NoteBlockInstrument.HAT.equals(state.instrument()));
    }
}
