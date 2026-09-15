package com.ssscript.taczfixes.common.mixin;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.ssscript.taczfixes.common.util.OffhandShooterManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = {GunData.class}, remap = false)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/common/mixin/MixinGunData.class */
public abstract class MixinGunData {
    @Redirect(method = {"getShootInterval"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/entity/IGunOperator;getCacheProperty()Lcom/tacz/guns/resource/modifier/AttachmentCacheProperty;"))
    private AttachmentCacheProperty dualWield$resolveRpmCache(IGunOperator operator, LivingEntity shooter, FireMode fireMode, ItemStack stack) {
        ShooterDataHolder activeData = OffhandShooterManager.findOffhandData(shooter, stack);
        if (activeData != null && activeData.cacheProperty != null) {
            return activeData.cacheProperty;
        }
        return operator.getCacheProperty();
    }
}
