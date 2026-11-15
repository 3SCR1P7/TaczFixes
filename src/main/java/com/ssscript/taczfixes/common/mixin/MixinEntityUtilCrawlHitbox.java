package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.CrawlHitboxHelper;
import com.ssscript.taczfixes.common.util.LimbDamageHelper;
import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.EntityUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.scores.Team;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 用本模组定义的受击箱接管子弹射线判定:
 * - 游泳/爬行: 随朝向的长条箱(1.8x0.6x0.6), 前端爆头、尾端四肢;
 * - 站姿(配置开启时): 尺寸与原版一致但随朝向旋转, 爆头/腿伤判定沿用原版 TaCZ 规则。
 */
@Mixin(value = EntityUtil.class, remap = false)
public class MixinEntityUtilCrawlHitbox {
    @Inject(method = "getHitResult", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$customHitbox(Projectile bulletEntity, Entity entity, Vec3 startVec, Vec3 endVec,
                                               CallbackInfoReturnable<EntityKineticBullet.EntityResult> cir) {
        if (!CrawlHitboxHelper.shouldUseCustomHitbox(entity)) {
            return;
        }
        // 保持与 TaCZ 原逻辑一致的前置过滤, 避免绕过友伤/忽略列表。
        if (EntityUtil.isBulletDamageIgnored(entity)) {
            cir.setReturnValue(null);
            return;
        }
        Entity owner = bulletEntity.getOwner();
        if (AmmoConfig.IGNORE_TEAM_FRIENDLY_FIRE.get() && owner != null) {
            Team team = owner.getTeam();
            if (team != null && team.isAlliedTo(entity.getTeam()) && !team.isAllowFriendlyFire()) {
                cir.setReturnValue(null);
                return;
            }
        }
        Vec3 hitPos = CrawlHitboxHelper.clip(bulletEntity, entity, startVec, endVec);
        if (hitPos == null) {
            // 已由自定义判定箱接管: 没打中自定义箱就视为未命中, 不能回落到原版 AABB 再判定一次。
            cir.setReturnValue(null);
            return;
        }
        boolean headshot;
        if (CrawlHitboxHelper.isHorizontalPose(entity)) {
            CrawlHitboxHelper.Zone zone = CrawlHitboxHelper.zoneOf(entity, hitPos);
            if (zone == CrawlHitboxHelper.Zone.LIMB) {
                LimbDamageHelper.storeCrawlZone(bulletEntity.getId(), entity.getId(), zone);
            }
            headshot = zone == CrawlHitboxHelper.Zone.HEAD;
        } else {
            headshot = CrawlHitboxHelper.isStandingHeadshot(entity, hitPos);
        }
        cir.setReturnValue(new EntityKineticBullet.EntityResult(entity, hitPos, headshot));
    }
}
