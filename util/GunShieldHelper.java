package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.data.AttachmentTaczFixesData;
import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GunShieldHelper {
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private static class State {
        int counter;
        long cooldownUntil;
        long lastBlock;
        boolean suppressed;
    }

    private GunShieldHelper() {
    }

    /** 解析枪械佩戴的枪盾 taczfixes 配置(枪械优先, 其次按配件槽位)。无配置返回 null。 */
    public static GunTaczFixesData.ShieldConfig resolveShieldConfig(ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) return null;
        IGun gun = IGun.getIGunOrNull(weapon);
        if (gun == null) return null;
        ResourceLocation gunId = gun.getGunId(weapon);
        if (gunId == null) return null;
        ResourceLocation dataId = TaczFixesDataManager.resolveDataId(gunId);
        GunTaczFixesData gunData = TaczFixesDataManager.get(dataId);
        if (gunData != null && gunData.shield != null) {
            return withDefaults(gunData.shield);
        }
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            ResourceLocation id = gun.getAttachmentId(weapon, type);
            if (DefaultAssets.isEmptyAttachmentId(id)) continue;
            AttachmentTaczFixesData attachmentData = AttachmentTaczFixesManager.resolveData(id);
            if (attachmentData != null && attachmentData.shield != null) {
                return withDefaults(attachmentData.shield);
            }
        }
        return null;
    }

    private static GunTaczFixesData.ShieldConfig withDefaults(GunTaczFixesData.ShieldConfig cfg) {
        GunTaczFixesData.ShieldConfig result = new GunTaczFixesData.ShieldConfig();
        result.resistance = cfg.resistance != null ? cfg.resistance : 1.0;
        result.durability = cfg.durability;
        result.cooldown = cfg.cooldown != null ? cfg.cooldown : 5.0;
        return result;
    }

    /** 盾是否当前不可用(冷却中或泄漏伤害压制中)。用于阻断 gsm 的格挡判定。 */
    public static boolean isShieldUnavailable(LivingEntity user) {
        if (user == null) return false;
        State s = STATES.get(user.getUUID());
        return s != null && (s.suppressed || s.cooldownUntil > System.currentTimeMillis());
    }

    public static void setSuppressed(LivingEntity user, boolean value) {
        if (user == null) return;
        state(user).suppressed = value;
    }

    /** 记录一次成功格挡：吸收点累加计数器、超durability进入冷却、冷却时长无格挡则重置计数器。 */
    public static void onBlocked(LivingEntity user, ItemStack weapon, GunTaczFixesData.ShieldConfig cfg, double absorbed) {
        if (user == null || cfg == null || absorbed <= 0) return;
        State s = state(user);
        long now = System.currentTimeMillis();
        long cooldownMs = (long) (cfg.cooldown * 1000.0);
        if (now - s.lastBlock >= cooldownMs) {
            s.counter = 0;
        }
        s.lastBlock = now;
        s.counter += (int) Math.max(1.0, Math.floor(absorbed));
        if (cfg.durability != null && cfg.durability > 0
                && s.counter >= cfg.durability && now >= s.cooldownUntil) {
            s.cooldownUntil = now + cooldownMs;
            s.counter = 0;
            if (user instanceof net.minecraft.world.entity.player.Player player && weapon != null && !weapon.isEmpty()) {
                player.getCooldowns().addCooldown(weapon.getItem(), (int) Math.max(1, Math.round(cfg.cooldown * 20.0)));
            }
        }
    }

    private static State state(LivingEntity user) {
        return STATES.computeIfAbsent(user.getUUID(), k -> new State());
    }
}
