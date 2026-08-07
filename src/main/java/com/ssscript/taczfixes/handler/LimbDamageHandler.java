package com.ssscript.taczfixes.handler;

import com.ssscript.taczfixes.Config;
import com.ssscript.taczfixes.data.AttachmentLimbFactorManager;
import com.ssscript.taczfixes.data.TaczFixesDataManager;
import com.ssscript.taczfixes.util.LimbDamageHelper;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public class LimbDamageHandler {

    @SubscribeEvent
    public void onGunHurt(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient()) return;
        if (!(event.getHurtEntity() instanceof LivingEntity entity)) return;
        if (event.isHeadShot()) return;

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
            return clampLimbFactor(Config.LIMB_FACTOR_DEFAULT.get().doubleValue());
        }
        ItemStack gunItem = shooter.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) {
            return clampLimbFactor(Config.LIMB_FACTOR_DEFAULT.get().doubleValue());
        }
        ResourceLocation gunId = iGun.getGunId(gunItem);
        if (gunId == null) {
            return clampLimbFactor(Config.LIMB_FACTOR_DEFAULT.get().doubleValue());
        }
        ResourceLocation dataId = TaczFixesDataManager.resolveDataId(gunId);
        Double limbFactor = TaczFixesDataManager.getLimbFactor(dataId);
        double base;
        if (limbFactor != null) {
            base = limbFactor;
        } else {
            var gunIndex = TimelessAPI.getCommonGunIndex(gunId);
            if (gunIndex.isEmpty()) {
                return clampLimbFactor(Config.LIMB_FACTOR_DEFAULT.get().doubleValue());
            }
            String type = gunIndex.get().getType();
            if (type == null) {
                return clampLimbFactor(Config.LIMB_FACTOR_DEFAULT.get().doubleValue());
            }
            base = switch (type) {
                case "pistol" -> Config.GUN_TYPE_PISTOL.get();
                case "rifle" -> Config.GUN_TYPE_RIFLE.get();
                case "sniper" -> Config.GUN_TYPE_SNIPER.get();
                case "shotgun" -> Config.GUN_TYPE_SHOTGUN.get();
                case "smg" -> Config.GUN_TYPE_SMG.get();
                case "rpg" -> Config.GUN_TYPE_RPG.get();
                case "mg" -> Config.GUN_TYPE_MG.get();
                default -> Config.GUN_TYPE_OTHER.get();
            };
        }
        return clampLimbFactor(AttachmentLimbFactorManager.applyLimbFactor(gunItem, base));
    }

    private static float clampLimbFactor(double value) {
        return (float) Math.min(1.0, Math.max(0.0, value));
    }
}
