package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.util.CrawlHitboxHelper;
import com.ssscript.taczfixes.common.util.LimbDamageHelper;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.EntityUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 爬行玩家使用独立受击箱(1.8x0.6x0.6, 随朝向旋转)进行子弹命中判定。
 * 前端为爆头, 尾端为四肢, 中间为普通命中。
 */
@Mixin(value = EntityUtil.class, remap = false)
public class MixinEntityUtilCrawlHitbox {
    @Inject(method = "getHitResult", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$crawlHitbox(Projectile bulletEntity, Entity entity, Vec3 startVec, Vec3 endVec, CallbackInfoReturnable<EntityKineticBullet.EntityResult> cir) {
        if (!Config.CRAWL_HITBOX_ENABLED.get() || !CrawlHitboxHelper.isCrawling(entity)) {
            return;
        }
        Vec3 hitPos = CrawlHitboxHelper.clip(entity, startVec, endVec);
        if (hitPos == null) {
            return;
        }
        CrawlHitboxHelper.Zone zone = CrawlHitboxHelper.zoneOf(entity, hitPos);
        if (zone == CrawlHitboxHelper.Zone.LIMB) {
            LimbDamageHelper.storeCrawlZone(bulletEntity.getId(), entity.getId(), zone);
        }
        cir.setReturnValue(new EntityKineticBullet.EntityResult(entity, hitPos, zone == CrawlHitboxHelper.Zone.HEAD));
    }
}
