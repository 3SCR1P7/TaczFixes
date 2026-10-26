package com.ssscript.taczfixes.common.compat.create;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ContraptionBlockHitResult extends BlockHitResult {
    private final BlockState state;

    public ContraptionBlockHitResult(Vec3 location, Direction direction, BlockPos blockPos, BlockState state) {
        super(location, direction, blockPos, false);
        this.state = state;
    }

    public BlockState getState() {
        return this.state;
    }
}
