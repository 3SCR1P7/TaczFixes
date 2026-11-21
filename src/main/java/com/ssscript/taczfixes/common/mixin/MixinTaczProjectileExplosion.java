package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings({"target", "mapping", "public-target", "unresolvable-target", "visibility", "constraints", "overwrite", "default-package", "raw-types"})
@Mixin(targets = "com.tacz.guns.util.block.ProjectileExplosion", remap = false)
public class MixinTaczProjectileExplosion {
    private static final ThreadLocal<Entity> taczfixes$currentEntity = new ThreadLocal<>();

    // index = 13 是对 TaCZ 局部变量表的硬编码: 加 require = 0, TaCZ 更新后最多是这条注入失效, 不会启动硬崩。
    @ModifyVariable(method = "m_46061_()V", at = @At(value = "STORE", ordinal = 0), index = 13, require = 0, remap = false)
    private Entity taczfixes$captureEntity(Entity entity) {
        if (entity != null) {
            taczfixes$currentEntity.set(entity);
        }
        return entity;
    }

    @Redirect(method = "m_46061_()V", at = @At(value = "FIELD", target = "Lcom/tacz/guns/util/block/ProjectileExplosion;knockback:Z", remap = false), require = 0, remap = false)
    private boolean taczfixes$redirectKnockback(com.tacz.guns.util.block.ProjectileExplosion instance) {
        if (Config.EXPLOSION_BULLET_ONLY.get()) {
            Entity entity = taczfixes$currentEntity.get();
            return entity != null && entity.getType() == EntityKineticBullet.TYPE;
        }
        return true;
    }

    /** 爆炸结束清掉 ThreadLocal, 避免服务端主线程长期强引用已经 discard 的实体。 */
    @Inject(method = "m_46061_()V", at = @At("RETURN"), require = 0, remap = false)
    private void taczfixes$clearEntity(CallbackInfo ci) {
        taczfixes$currentEntity.remove();
    }
}
