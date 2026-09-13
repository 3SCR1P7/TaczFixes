package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.util.DamageByDistanceHelper;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AttachmentCacheProperty.class, remap = false)
public class MixinAttachmentCachePropertyEffectiveRange {
    @Inject(method = "eval", at = @At("RETURN"), remap = false)
    private void taczfixes$overrideEffectiveRange(ItemStack gunItem, com.tacz.guns.resource.pojo.data.gun.GunData gunData,
                                                  CallbackInfo ci) {
        try {
            IGun gun = gunItem == null ? null : IGun.getIGunOrNull(gunItem);
            if (gun == null) return;
            ResourceLocation gunId = gun.getGunId(gunItem);
            if (gunId == null) return;
            ResourceLocation dataId = TaczFixesDataManager.resolveDataId(gunId);
            GunTaczFixesData data = dataId == null ? null : TaczFixesDataManager.get(dataId);
            if (data == null || data.damage_by_distance == null) return;
            double effectiveRange = DamageByDistanceHelper.resolveEffectiveRange(
                    gunItem, gunId, data.damage_by_distance.effective_range);
            AttachmentCacheProperty self = (AttachmentCacheProperty) (Object) this;
            self.setCache(GunProperties.EFFECTIVE_RANGE, (float) effectiveRange);
        } catch (Exception ignored) {
        }
    }
}
