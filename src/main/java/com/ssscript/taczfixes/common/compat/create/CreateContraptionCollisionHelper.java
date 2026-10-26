package com.ssscript.taczfixes.common.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.ssscript.taczfixes.common.config.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

public final class CreateContraptionCollisionHelper {
    private static final int MAX_VOLUME_SCAN = 32768;
    private static final double BLOCK_MARGIN = 2.0D;

    private CreateContraptionCollisionHelper() {
    }

    public static ContraptionBlockHitResult findContraptionHit(Level level, Vec3 start, Vec3 end) {
        if (level.isClientSide || !Config.CREATE_CONTRAPTION_BULLET_COLLISION.get() || start.equals(end)) {
            return null;
        }

        double bestDistance = Double.MAX_VALUE;
        ContraptionBlockHitResult bestHit = null;
        AABB pathBounds = new AABB(start, end).inflate(1.0D);

        for (AbstractContraptionEntity contraptionEntity :
                level.getEntitiesOfClass(AbstractContraptionEntity.class, pathBounds)) {
            if (!contraptionEntity.isAlive()
                    || !contraptionEntity.collisionEnabled()
                    || !contraptionEntity.getBoundingBox().intersects(pathBounds)) {
                continue;
            }
            ContraptionBlockHitResult hit = clipContraption(level, contraptionEntity, start, end);
            if (hit == null) {
                continue;
            }
            double distance = start.distanceToSqr(hit.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                bestHit = hit;
            }
        }
        return bestHit;
    }

    private static ContraptionBlockHitResult clipContraption(Level level, AbstractContraptionEntity contraptionEntity,
                                                             Vec3 globalStart, Vec3 globalEnd) {
        Contraption contraption = contraptionEntity.getContraption();
        if (contraption == null) {
            return null;
        }
        Map<BlockPos, StructureBlockInfo> blocks = contraption.getBlocks();
        if (blocks.isEmpty()) {
            return null;
        }

        Vec3 localStart = contraptionEntity.toLocalVector(globalStart, 0.0F);
        Vec3 localEnd = contraptionEntity.toLocalVector(globalEnd, 0.0F);
        AABB localPath = new AABB(localStart, localEnd).inflate(1.0E-7D);
        BlockGetter localLevel = new ContraptionBlockGetter(level, blocks);
        HitAccumulator accumulator = new HitAccumulator(blocks, localLevel, localStart, localEnd, localPath);

        AABB query = localPath.inflate(BLOCK_MARGIN);
        int minX = Mth.floor(query.minX);
        int minY = Mth.floor(query.minY);
        int minZ = Mth.floor(query.minZ);
        int maxX = Mth.floor(query.maxX);
        int maxY = Mth.floor(query.maxY);
        int maxZ = Mth.floor(query.maxZ);
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);

        if (volume <= blocks.size() && volume <= MAX_VOLUME_SCAN) {
            for (BlockPos localPos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
                accumulator.test(localPos);
            }
        } else {
            for (BlockPos localPos : blocks.keySet()) {
                accumulator.test(localPos);
            }
        }

        if (accumulator.bestHit == null) {
            return null;
        }
        Vec3 globalHit = contraptionEntity.toGlobalVector(accumulator.bestHit.getLocation(), 0.0F);
        Vec3 localNormal = Vec3.atLowerCornerOf(accumulator.bestHit.getDirection().getNormal());
        Vec3 globalNormal = contraptionEntity.toGlobalVector(
                accumulator.bestHit.getLocation().add(localNormal), 0.0F).subtract(globalHit);
        Direction globalDirection = globalNormal.lengthSqr() < 1.0E-8D
                ? accumulator.bestHit.getDirection()
                : Direction.getNearest(globalNormal.x, globalNormal.y, globalNormal.z);
        return new ContraptionBlockHitResult(globalHit, globalDirection,
                BlockPos.containing(globalHit), accumulator.bestState);
    }

    private static final class HitAccumulator {
        private final Map<BlockPos, StructureBlockInfo> blocks;
        private final BlockGetter level;
        private final Vec3 localStart;
        private final Vec3 localEnd;
        private final AABB localPath;
        private BlockHitResult bestHit;
        private BlockState bestState;
        private double bestDistance = Double.MAX_VALUE;

        private HitAccumulator(Map<BlockPos, StructureBlockInfo> blocks, BlockGetter level,
                               Vec3 localStart, Vec3 localEnd, AABB localPath) {
            this.blocks = blocks;
            this.level = level;
            this.localStart = localStart;
            this.localEnd = localEnd;
            this.localPath = localPath;
        }

        private void test(BlockPos localPos) {
            if (localPos.getX() - BLOCK_MARGIN > this.localPath.maxX
                    || localPos.getX() + 1.0D + BLOCK_MARGIN < this.localPath.minX
                    || localPos.getY() - BLOCK_MARGIN > this.localPath.maxY
                    || localPos.getY() + 1.0D + BLOCK_MARGIN < this.localPath.minY
                    || localPos.getZ() - BLOCK_MARGIN > this.localPath.maxZ
                    || localPos.getZ() + 1.0D + BLOCK_MARGIN < this.localPath.minZ) {
                return;
            }
            StructureBlockInfo info = this.blocks.get(localPos);
            if (info == null) {
                return;
            }
            BlockState state = info.state();
            VoxelShape shape = state.getCollisionShape(this.level, localPos, CollisionContext.empty());
            if (shape.isEmpty() || !shape.bounds().move(localPos).intersects(this.localPath)) {
                return;
            }
            BlockHitResult hit = shape.clip(this.localStart, this.localEnd, localPos);
            if (hit == null) {
                return;
            }
            double distance = this.localStart.distanceToSqr(hit.getLocation());
            if (distance < this.bestDistance) {
                this.bestDistance = distance;
                this.bestHit = hit;
                this.bestState = state;
            }
        }
    }

    private static final class ContraptionBlockGetter implements BlockGetter {
        private final Level level;
        private final Map<BlockPos, StructureBlockInfo> blocks;

        private ContraptionBlockGetter(Level level, Map<BlockPos, StructureBlockInfo> blocks) {
            this.level = level;
            this.blocks = blocks;
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            StructureBlockInfo info = this.blocks.get(pos);
            return info == null ? Blocks.AIR.defaultBlockState() : info.state();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return this.level.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return this.level.getMinBuildHeight();
        }
    }
}
