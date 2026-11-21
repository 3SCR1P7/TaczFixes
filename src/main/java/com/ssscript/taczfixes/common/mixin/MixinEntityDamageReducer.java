package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(EntityKineticBullet.class)
public class MixinEntityDamageReducer {
    private Entity taczfixes_damageTargetEntity = null;

    @Inject(method = "onHitEntity", at = @At("HEAD"), remap = false)
    private void taczfixes$storeDamageTarget(TacHitResult result, Vec3 startVec, Vec3 endVec, CallbackInfo ci) {
        this.taczfixes_damageTargetEntity = result.getEntity();
    }

    @Inject(method = "getDamage", at = @At("RETURN"), cancellable = true, remap = false)
    private void taczfixes$applyDamageReduction(Vec3 hitPos, CallbackInfoReturnable<Float> cir) {
        if (this.taczfixes_damageTargetEntity == null) return;
        // 普通实体由 EntityHurtByGunEvent.Pre 统一缩放; 这里只兜底不走事件的自定义目标(ITargetEntity), 避免重复减伤
        if (!(this.taczfixes_damageTargetEntity instanceof com.tacz.guns.api.entity.ITargetEntity)) return;
        String entityId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(this.taczfixes_damageTargetEntity.getType()).toString();
        List<? extends String> entries = Config.DAMAGE_REDUCTION_ENTITIES.get();
        for (String entry : entries) {
            String[] parts = entry == null ? new String[0] : entry.split(",");
            if (parts.length != 2) {
                continue;
            }
            // 配置写错(非数字/越界)不能让服务端 tick 崩: 与 GunDamageReductionHandler 一样做完整防护。
            if (!parts[0].trim().equals(entityId)) {
                continue;
            }
            double reduction;
            try {
                reduction = Double.parseDouble(parts[1].trim());
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (Double.isNaN(reduction)) {
                continue;
            }
            reduction = Math.max(0.0D, Math.min(1.0D, reduction));
            cir.setReturnValue(cir.getReturnValue() * (float) (1.0D - reduction));
            return;
        }
    }

    @Inject(method = "onHitEntity", at = @At("RETURN"), remap = false)
    private void taczfixes$clearDamageTarget(CallbackInfo ci) {
        this.taczfixes_damageTargetEntity = null;
    }
}
