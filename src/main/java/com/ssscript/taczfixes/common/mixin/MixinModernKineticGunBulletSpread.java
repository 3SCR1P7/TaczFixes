package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.SpreadState;
import com.ssscript.taczfixes.common.data.InaccuracyParams;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "com.tacz.guns.item.ModernKineticGunItem", remap = false)
public class MixinModernKineticGunBulletSpread {
    @ModifyVariable(method = "doBulletSpread", at = @At("HEAD"), argsOnly = true, index = 7)
    private float taczfixes$modifyInaccuracy(float inaccuracy, ShooterDataHolder dataHolder, ItemStack gunItem,
                                             LivingEntity shooter, Projectile projectile, int bulletCnt,
                                             float processedSpeed, float originalInaccuracy, float pitch,
                                             float yaw) {
        if (shooter == null || gunItem == null) return inaccuracy;
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null) return inaccuracy;
        ResourceLocation gunId = gun.getGunId(gunItem);
        if (gunId == null) return inaccuracy;
        ResourceLocation dataId = TaczFixesDataManager.resolveDataId(gunId);
        InaccuracyType state = InaccuracyType.getInaccuracyType(shooter);
        InaccuracyParams params = TaczFixesDataManager.resolveInaccuracyParams(dataId, state, gunItem);
        float factor = SpreadState.modifyInaccuracy(dataId, params, inaccuracy);
        float coilFactor = GunEnchantmentHelper.getCoilInaccuracyFactor(gunItem);
        return coilFactor != 1.0f ? factor * coilFactor : factor;
    }
}
