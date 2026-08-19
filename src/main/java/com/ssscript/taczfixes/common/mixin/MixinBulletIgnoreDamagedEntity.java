package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.Config;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(EntityKineticBullet.class)
public class MixinBulletIgnoreDamagedEntity {
    @Unique
    private final Map<Integer, Long> taczfixes$damagedAt = new HashMap<>();

    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$skipRecentlyDamaged(TacHitResult result, Vec3 startVec, Vec3 endVec, CallbackInfo ci) {
        if (!Config.BULLET_IGNORE_ENTITY_ENABLE.get()) {
            return;
        }
        Entity entity = result.getEntity();
        if (entity == null || taczfixes$isOnCooldown(entity.getId())) {
            ci.cancel();
        }
    }

    @Inject(method = "onHitEntity", at = @At("TAIL"), remap = false)
    private void taczfixes$recordDamage(TacHitResult result, Vec3 startVec, Vec3 endVec, CallbackInfo ci) {
        Entity entity = result.getEntity();
        if (entity != null) {
            taczfixes$damagedAt.put(entity.getId(), System.currentTimeMillis());
        }
    }

    @Unique
    private boolean taczfixes$isOnCooldown(int entityId) {
        Long last = taczfixes$damagedAt.get(entityId);
        return last != null && System.currentTimeMillis() - last < Config.BULLET_IGNORE_ENTITY_COOLDOWN_MS.get();
    }
}
