package com.ssscript.taczfixes.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 用原版降雨判定({@link Level#isRainingAt(BlockPos)}: 高度图 + 天空 + 群系)判断一个
 * 世界物体所在位置是否真的被雨淋到, 而不是靠天空光等级去估。
 */
public final class RainExposure {
    private RainExposure() {
    }

    /**
     * 方块实体: 从方块自身位置往上找第一格空气(多格方块会跳过同种方块的部分),
     * 再看那一格是否被雨淋到。上方先被别的方块挡住时视为遮挡。
     */
    public static boolean blockEntityRainedOn(BlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        if (level == null || !level.isRaining()) {
            return false;
        }
        BlockState own = blockEntity.getBlockState();
        BlockPos.MutableBlockPos cursor = blockEntity.getBlockPos().above().mutable();
        for (int i = 0; i < 8; i++) {
            BlockState state = level.getBlockState(cursor);
            if (state.isAir()) {
                return level.isRainingAt(cursor);
            }
            if (state.getBlock() != own.getBlock()) {
                return false;
            }
            cursor.move(Direction.UP);
        }
        return false;
    }

    /** 实体: 看实体包围盒顶部那一格是否被雨淋到。 */
    public static boolean entityRainedOn(Entity entity) {
        Level level = entity.level();
        if (level == null || !level.isRaining()) {
            return false;
        }
        BlockPos pos = BlockPos.containing(entity.getX(), entity.getBoundingBox().maxY + 0.05D, entity.getZ());
        return level.isRainingAt(pos);
    }
}
