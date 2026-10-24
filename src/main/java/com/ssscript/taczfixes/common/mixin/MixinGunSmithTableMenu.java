package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.GunSmithTablePosAccess;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(GunSmithTableMenu.class)
public abstract class MixinGunSmithTableMenu implements GunSmithTablePosAccess {

    @Unique
    @Nullable
    private BlockPos taczfixes$tablePos;

    @Override
    public void taczfixes$setTablePos(BlockPos pos) {
        this.taczfixes$tablePos = pos;
    }

    @Override
    @Nullable
    public BlockPos taczfixes$getTablePos() {
        return this.taczfixes$tablePos;
    }
}
