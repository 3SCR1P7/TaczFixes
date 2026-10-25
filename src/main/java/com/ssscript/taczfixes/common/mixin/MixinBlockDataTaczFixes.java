package com.ssscript.taczfixes.common.mixin;

import com.google.gson.annotations.SerializedName;
import com.ssscript.taczfixes.common.data.GunSmithTableEnergyConfig;
import com.tacz.guns.resource.pojo.data.block.BlockData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockData.class)
public abstract class MixinBlockDataTaczFixes implements GunSmithTableEnergyConfig.BlockDataAccess {

    @Unique
    @SerializedName("taczfixes")
    private GunSmithTableEnergyConfig.Block taczfixes$energyConfig;

    @Override
    public GunSmithTableEnergyConfig.Block taczfixes$getEnergyConfig() {
        return this.taczfixes$energyConfig;
    }
}
