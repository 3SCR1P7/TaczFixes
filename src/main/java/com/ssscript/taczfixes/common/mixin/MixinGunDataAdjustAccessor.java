package com.ssscript.taczfixes.common.mixin;

import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.pojo.data.gun.GunFireModeAdjustData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.EnumMap;

@Mixin(value = GunData.class, remap = false)
public interface MixinGunDataAdjustAccessor {
    @Accessor(value = "fireModeAdjust", remap = false)
    EnumMap<FireMode, GunFireModeAdjustData> taczfixes$getFireModeAdjust();

    @Accessor(value = "fireModeAdjust", remap = false)
    void taczfixes$setFireModeAdjust(EnumMap<FireMode, GunFireModeAdjustData> map);
}
