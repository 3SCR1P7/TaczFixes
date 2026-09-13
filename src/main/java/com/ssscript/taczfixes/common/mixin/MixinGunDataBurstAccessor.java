package com.ssscript.taczfixes.common.mixin;

import com.tacz.guns.resource.pojo.data.gun.BurstData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GunData.class, remap = false)
public interface MixinGunDataBurstAccessor {
    @Accessor("burstData")
    void taczfixes$setBurstData(BurstData burstData);
}
