package com.ssscript.taczfixes.common.mixin;

import com.tacz.guns.resource.pojo.data.gun.BurstData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = BurstData.class, remap = false)
public interface MixinBurstDataAccessor {
    @Accessor("continuousShoot")
    void taczfixes$setContinuousShoot(boolean value);

    @Accessor("count")
    void taczfixes$setCount(int value);

    @Accessor("bpm")
    void taczfixes$setBpm(int value);

    @Accessor("minInterval")
    void taczfixes$setMinInterval(double value);
}
