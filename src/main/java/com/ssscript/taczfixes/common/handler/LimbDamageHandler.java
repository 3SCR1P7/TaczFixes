package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.util.CrawlHitboxHelper;
import com.ssscript.taczfixes.common.util.LimbDamageFactorHelper;
import com.ssscript.taczfixes.common.util.LimbDamageHelper;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public class LimbDamageHandler {

    @SubscribeEvent
    public void onGunHurt(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient()) return;
        if (!Config.LIMB_DAMAGE_ENABLED.get()) return;
        if (!(event.getHurtEntity() instanceof LivingEntity entity)) return;
        if (event.isHeadShot()) return;

        // 爬行玩家使用独立受击箱: 仅尾端(腿部)区域造成四肢伤害, 其余为普通命中
        if (CrawlHitboxHelper.isCrawling(entity)) {
            CrawlHitboxHelper.Zone zone = LimbDamageHelper.getCrawlZone(event.getBullet().getId(), entity.getId());
            if (zone == CrawlHitboxHelper.Zone.LIMB) {
                float limbFactor = getLimbFactorForGun(event);
                event.setBaseAmount(event.getBaseAmount() * limbFactor);
            }
            return;
        }

        Vec3 hitPos = LimbDamageHelper.getHitPosition(event.getBullet().getId());
        if (hitPos == null) return;

        double relativeY = hitPos.y - entity.getY();
        double limbThreshold;

        if (entity instanceof Player player) {
            if (player.getBoundingBox().getYsize() < 1.0) return;
            limbThreshold = player.isCrouching() ? Config.LIMB_THRESHOLD_SNEAKING.get() : Config.LIMB_THRESHOLD_STANDING.get();
        } else {
            if (!Config.LIVING_ENTITY_LIMB_ENABLED.get()) return;
            ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            if (typeId == null) return;
            List<? extends String> excluded = Config.LIVING_ENTITY_LIMB_EXCLUDED.get();
            if (excluded.contains(typeId.toString())) return;
            limbThreshold = entity.getBbHeight() * Config.LIVING_ENTITY_LIMB_THRESHOLD.get();
        }

        if (relativeY < limbThreshold) {
            float limbFactor = getLimbFactorForGun(event);
            float originalDamage = event.getBaseAmount();
            event.setBaseAmount(originalDamage * limbFactor);
        }
    }

    private float getLimbFactorForGun(EntityHurtByGunEvent.Pre event) {
        LivingEntity shooter = (LivingEntity) event.getAttacker();
        if (shooter == null) {
            return LimbDamageFactorHelper.getDefaultFactor();
        }
        return LimbDamageFactorHelper.getFinalFactor(shooter.getMainHandItem());
    }
}
