package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.util.GunEnchantmentHelper;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 子弹最终伤害加成：
 * - 枪械等级：每级 +GUN_LEVEL_DAMAGE_PER_LEVEL（不封顶）
 * - 激流：射手处于水中/雨中/气泡中时，每级提升子弹伤害
 * 作用于所有弹丸（含多重射击补发的弹丸）。
 */
@Mixin(EntityKineticBullet.class)
public class MixinEntityKineticBulletLevelDamage {
    @Inject(method = "getDamage", at = @At("RETURN"), cancellable = true, remap = false)
    private void taczfixes$applyLevelAndRiptide(Vec3 hitPos, CallbackInfoReturnable<Float> cir) {
        Entity owner = ((EntityKineticBullet) (Object) this).getOwner();
        if (!(owner instanceof LivingEntity shooter)) {
            return;
        }
        float factor = GunEnchantmentHelper.getGunLevelDamageFactor(shooter);
        if (GunEnchantmentHelper.isEnabled()) {
            factor *= GunEnchantmentHelper.getRiptideDamageFactor(shooter);
        }
        if (factor != 1.0f) {
            cir.setReturnValue(cir.getReturnValue() * factor);
        }
    }
}
