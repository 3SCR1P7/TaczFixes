package com.ssscript.taczfixes.common.mixin;

import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CommonGunIndex.class, remap = false)
public interface MixinCommonGunIndexAccessor {
    @Accessor("gunData")
    void taczfixes$setGunData(GunData gunData);
}
