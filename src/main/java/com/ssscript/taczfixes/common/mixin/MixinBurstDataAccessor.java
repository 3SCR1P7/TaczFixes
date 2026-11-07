package com.ssscript.taczfixes.common.mixin;

import com.tacz.guns.resource.pojo.data.gun.BurstData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = BurstData.class, remap = false)
public interface MixinBurstDataAccessor {
    @Accessor(value = "continuousShoot", remap = false)
    void taczfixes$setContinuousShoot(boolean value);

    @Accessor(value = "count", remap = false)
    void taczfixes$setCount(int value);

    @Accessor(value = "bpm", remap = false)
    void taczfixes$setBpm(int value);

    @Accessor(value = "minInterval", remap = false)
    void taczfixes$setMinInterval(double value);
}
