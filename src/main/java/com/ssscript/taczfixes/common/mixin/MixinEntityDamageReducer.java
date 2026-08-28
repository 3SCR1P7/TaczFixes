package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.register.Config;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
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
        String entityId = ForgeRegistries.ENTITY_TYPES.getKey(this.taczfixes_damageTargetEntity.getType()).toString();
        List<? extends String> entries = Config.DAMAGE_REDUCTION_ENTITIES.get();
        for (String entry : entries) {
            String[] parts = entry.split(",");
            if (parts.length == 2 && parts[0].equals(entityId)) {
                double reduction = Double.parseDouble(parts[1]);
                cir.setReturnValue(cir.getReturnValue() * (float) (1.0 - reduction));
                return;
            }
        }
    }

    @Inject(method = "onHitEntity", at = @At("RETURN"), remap = false)
    private void taczfixes$clearDamageTarget(CallbackInfo ci) {
        this.taczfixes_damageTargetEntity = null;
    }
}
