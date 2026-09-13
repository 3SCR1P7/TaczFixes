package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.util.DamageByDistanceHelper;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityKineticBullet.class, remap = false)
public class MixinEntityKineticBulletDamageByDistance {
    @Shadow
    @Final
    private Vec3 startPos;

    @Shadow
    @Final
    private float shotDamageMultiplier;

    @Shadow
    @Final
    private float damageModifier;

    /** 有 damage_by_distance 配置时整体接管距离伤害, 弃用 TACZ 的 damage_adjust 与"超射程归零"。 */
    @Inject(method = "getDamage", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$damageByDistance(Vec3 pos, CallbackInfoReturnable<Float> cir) {
        EntityKineticBullet bullet = (EntityKineticBullet) (Object) this;
        ResourceLocation gunId = bullet.getGunId();
        if (gunId == null) return;
        ResourceLocation dataId = TaczFixesDataManager.resolveDataId(gunId);
        GunTaczFixesData data = dataId == null ? null : TaczFixesDataManager.get(dataId);
        if (data == null || data.damage_by_distance == null || data.damage_by_distance.function == null) return;
        GunTaczFixesData.DamageByDistanceConfig cfg = data.damage_by_distance;
        double flightDistance = pos.distanceTo(startPos);
        LivingEntity shooter = bullet.getOwner() instanceof LivingEntity living ? living : null;
        ItemStack held = shooter == null ? null : shooter.getMainHandItem();
        double effectiveRange = DamageByDistanceHelper.resolveEffectiveRange(held, gunId, cfg.effective_range);
        double x = Math.max(0.0, flightDistance - effectiveRange);
        double y = AttachmentPropertyManager.functionEval(x, 0.0, cfg.function);
        if (!Double.isFinite(y)) return;
        float base = baseDamage(bullet, gunId);
        if (base <= 0.0f) return;
        cir.setReturnValue(base * (float) Math.max(0.0, y));
    }

    /** 基础伤害 = 子弹伤害 × 霰弹分割系数(damageModifier) × 每丸倍率(不含 damage_adjust 衰减)。 */
    private float baseDamage(EntityKineticBullet bullet, ResourceLocation gunId) {
        try {
            com.tacz.guns.resource.pojo.data.gun.BulletData bulletData = com.tacz.guns.api.TimelessAPI
                    .getCommonGunIndex(gunId).map(index -> index.getBulletData()).orElse(null);
            if (bulletData == null) return 0.0f;
            return bulletData.getDamageAmount() * damageModifier * shotDamageMultiplier;
        } catch (Exception e) {
            return 0.0f;
        }
    }
}
