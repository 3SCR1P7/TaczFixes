package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.util.ScopeSwitchState;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.custom.AdsModifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

@Mixin(AdsModifier.class)
public abstract class MixinAdsModifierCustomSlot {

    @Redirect(method = "getPropertyDiagramsData",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/resource/modifier/AttachmentCacheProperty;getCache(Ljava/lang/String;)Ljava/lang/Object;",
                    remap = false), remap = false)
    private Object taczfixes$customScopeAimTimeDiagrams(AttachmentCacheProperty cache, String id,
                                                        ItemStack stack, com.tacz.guns.resource.pojo.data.gun.GunData gunData,
                                                        AttachmentCacheProperty cacheProperty) {
        if (AdsModifier.ID.equals(id)) {
            Optional<Float> custom = ScopeSwitchState.customScopeAimTime(stack);
            if (custom.isPresent()) {
                return custom.get();
            }
        }
        return cache.getCache(id);
    }
}
