package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * 降低指定实体所受的枪械伤害。
 *
 * <p>在 TaCZ 的 {@link EntityHurtByGunEvent.Pre} 上缩放基础伤害, 也就是最终用来扣血的数值。
 * 放在事件里才能覆盖所有走事件链的伤害: 原版子弹、配件/附魔加成、以及 Arcana 等附属改过的伤害管线。
 * 自定义 {@code ITargetEntity} 目标不走该事件(直接 onProjectileHit), 由
 * {@code MixinEntityDamageReducer} 在 {@code getDamage} 上兜底, 两边不会重复减伤。</p>
 */
public final class GunDamageReductionHandler {
    @SubscribeEvent
    public void onGunHurt(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        Entity hurt = event.getHurtEntity();
        if (hurt == null) {
            return;
        }
        float factor = keptDamageFactor(hurt);
        if (factor >= 1.0F) {
            return;
        }
        event.setBaseAmount(event.getBaseAmount() * factor);
    }

    /** 命中实体在 damage_reduction_entities 里的伤害保留倍率; 未配置返回 1。 */
    public static float keptDamageFactor(Entity entity) {
        String entityId;
        try {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            entityId = id == null ? null : id.toString();
        } catch (Throwable ignored) {
            entityId = null;
        }
        if (entityId == null) {
            return 1.0F;
        }
        List<? extends String> entries = Config.DAMAGE_REDUCTION_ENTITIES.get();
        for (String entry : entries) {
            double reduction = reductionOf(entry, entityId);
            if (reduction >= 0.0) {
                return (float) (1.0 - Math.min(reduction, 1.0));
            }
        }
        return 1.0F;
    }

    private static double reductionOf(String entry, String entityId) {
        if (entry == null) {
            return -1.0;
        }
        int comma = entry.indexOf(',');
        if (comma <= 0 || comma >= entry.length() - 1) {
            return -1.0;
        }
        if (!entry.substring(0, comma).trim().equals(entityId)) {
            return -1.0;
        }
        try {
            return Double.parseDouble(entry.substring(comma + 1).trim());
        } catch (NumberFormatException ignored) {
            return -1.0;
        }
    }
}
