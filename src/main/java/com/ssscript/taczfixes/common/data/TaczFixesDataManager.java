package com.ssscript.taczfixes.common.data;

import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TaczFixesDataManager {
    private static final Map<ResourceLocation, GunTaczFixesData> DATA = new ConcurrentHashMap<>();

    private TaczFixesDataManager() {
    }

    public static void putAll(Map<ResourceLocation, GunTaczFixesData> map) {
        DATA.putAll(map);
    }

    public static GunTaczFixesData get(ResourceLocation dataId) {
        return DATA.get(dataId);
    }

    public static void put(ResourceLocation dataId, GunTaczFixesData data) {
        if (dataId != null && data != null) {
            DATA.put(dataId, data);
        }
    }

    public static Map<ResourceLocation, GunTaczFixesData> getAll() {
        return DATA;
    }

    public static ResourceLocation resolveDataId(ResourceLocation gunId) {
        if (gunId == null) return null;
        return TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> index.getPojo().getData())
                .orElse(gunId);
    }

    public static Double getLimbFactor(ResourceLocation dataId) {
        GunTaczFixesData data = dataId == null ? null : DATA.get(dataId);
        return data == null ? null : data.limb_factor;
    }

    public static Integer getGunRefitPoint(ItemStack gunStack) {
        if (gunStack == null || gunStack.isEmpty()) return null;
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return null;
        ResourceLocation dataId = resolveDataId(gun.getGunId(gunStack));
        GunTaczFixesData data = dataId == null ? null : DATA.get(dataId);
        return data == null ? null : data.refit_point;
    }

    /** 枪械 data 中的上肢耐力覆盖配置, 未配置返回 null。 */
    @Nullable
    public static GunTaczFixesData.AimingStaminaConfig resolveAimingStamina(ItemStack gunStack) {
        if (gunStack == null || gunStack.isEmpty()) return null;
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return null;
        ResourceLocation dataId = resolveDataId(gun.getGunId(gunStack));
        GunTaczFixesData data = dataId == null ? null : DATA.get(dataId);
        return data == null ? null : data.aiming_stamina;
    }

    /** 枪械 data 中的耐力倍率配置, 未配置返回 null。 */
    @Nullable
    public static GunTaczFixesData.StaminaConfig resolveStamina(ItemStack gunStack) {
        if (gunStack == null || gunStack.isEmpty()) return null;
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return null;
        ResourceLocation dataId = resolveDataId(gun.getGunId(gunStack));
        GunTaczFixesData data = dataId == null ? null : DATA.get(dataId);
        return data == null ? null : data.stamina;
    }

    public static GunTaczFixesData.RecoilConfig resolveRecoil(ResourceLocation dataId, FireMode mode) {
        GunTaczFixesData data = dataId == null ? null : DATA.get(dataId);
        if (data == null || data.recoil_multiplier == null || data.recoil_multiplier.isEmpty()) {
            return null;
        }
        String key = switch (mode) {
            case AUTO -> "auto";
            case SEMI -> "semi";
            case BURST -> "burst";
            case UNKNOWN -> null;
        };
        return key == null ? null : data.recoil_multiplier.get(key);
    }

    /**
     * 枪械是否允许加速拉栓/加速换弹时同步加速动画播放。
     * 未配置或无数据时默认允许(保持既有行为)。
     */
    public static boolean isAnimationZoomAllowed(@Nullable ItemStack gunStack) {
        if (gunStack == null) return true;
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return true;
        ResourceLocation dataId = resolveDataId(gun.getGunId(gunStack));
        GunTaczFixesData data = dataId == null ? null : DATA.get(dataId);
        return data == null || data.allow_animation_zoom == null || data.allow_animation_zoom;
    }

    /** 枪械 data 字段 bullet_in_barrel: 换弹时额外装填 min(x, n) 发。未配置返回 null。 */
    @Nullable
    public static Integer getBulletInBarrel(ItemStack gunStack) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return null;
        ResourceLocation dataId = resolveDataId(gun.getGunId(gunStack));
        GunTaczFixesData data = dataId == null ? null : DATA.get(dataId);
        return data == null ? null : data.bullet_in_barrel;
    }

    /** 枪械 data 中的双持覆盖配置, 未配置返回 null。 */
    @Nullable
    public static GunTaczFixesData.DualWieldConfig resolveDualWield(ResourceLocation gunId) {
        ResourceLocation dataId = resolveDataId(gunId);
        GunTaczFixesData data = dataId == null ? null : DATA.get(dataId);
        return data == null ? null : data.dual_wield;
    }

    public static GunTaczFixesData.FireKnockbackConfig resolveFireKnockback(ResourceLocation dataId) {
        GunTaczFixesData data = dataId == null ? null : DATA.get(dataId);
        return data == null ? null : data.fire_knockback;
    }

    /**
     * 枪械 data 字段 bullet_ricochet: 字段存在则覆盖配置文件(含 enable), 未填写的字段回退配置文件值。
     * data 缺省时完全使用配置文件值。仅以 gunId 为 key, 与禁用名单(disabled_guns)共同生效。
     */
    public static GunTaczFixesData.RicochetConfig resolveRicochet(ResourceLocation gunId) {
        ResourceLocation dataId = resolveDataId(gunId);
        GunTaczFixesData data = dataId == null ? null : DATA.get(dataId);
        GunTaczFixesData.RicochetConfig gun = data == null ? null : data.bullet_ricochet;

        GunTaczFixesData.RicochetConfig result = new GunTaczFixesData.RicochetConfig();
        result.enable = gun != null && gun.enable != null ? gun.enable : Config.BULLET_RICOCHET_ENABLE.get();
        result.min_angle = gun != null && gun.min_angle != null ? gun.min_angle : Config.BULLET_RICOCHET_MIN_ANGLE.get();
        result.max_angle = gun != null && gun.max_angle != null ? gun.max_angle : Config.BULLET_RICOCHET_MAX_ANGLE.get();
        result.chance_min = gun != null && gun.chance_min != null ? gun.chance_min : Config.BULLET_RICOCHET_CHANCE_MIN.get();
        result.chance_max = gun != null && gun.chance_max != null ? gun.chance_max : Config.BULLET_RICOCHET_CHANCE_MAX.get();
        result.block_tags = java.util.List.copyOf(gun != null && gun.block_tags != null && !gun.block_tags.isEmpty()
                ? gun.block_tags : Config.BULLET_RICOCHET_BLOCK_TAGS.get());
        result.damage_multiplier = gun != null && gun.damage_multiplier != null ? gun.damage_multiplier
                : Config.BULLET_RICOCHET_DAMAGE_MULTIPLIER.get();
        result.reflect_angle_ratio_min = gun != null && gun.reflect_angle_ratio_min != null ? gun.reflect_angle_ratio_min
                : Config.BULLET_RICOCHET_REFLECT_ANGLE_RATIO_MIN.get();
        result.reflect_angle_ratio_max = gun != null && gun.reflect_angle_ratio_max != null ? gun.reflect_angle_ratio_max
                : Config.BULLET_RICOCHET_REFLECT_ANGLE_RATIO_MAX.get();
        result.top_bottom_enable = gun != null && gun.top_bottom_enable != null ? gun.top_bottom_enable
                : Config.BULLET_RICOCHET_TOP_BOTTOM_ENABLE.get();
        return result;
    }

    /** 枪械 data 字段 jump_inaccuracy: 滞空散布倍率及其涨落速度。
     * 缺失时回退到配置文件默认值(分别默认 1.0 / 0.1)。 */
    public static GunTaczFixesData.JumpInaccuracyConfig getJumpInaccuracyConfig(ResourceLocation dataId) {
        GunTaczFixesData data = dataId == null ? null : DATA.get(dataId);
        GunTaczFixesData.JumpInaccuracyConfig cfg = data == null ? null : data.jump_inaccuracy;
        GunTaczFixesData.JumpInaccuracyConfig result = new GunTaczFixesData.JumpInaccuracyConfig();
        result.multiplier = cfg != null && cfg.multiplier != null ? cfg.multiplier
                : Config.JUMP_INACCURACY_DEFAULT_MULTIPLIER.get();
        result.speed = cfg != null && cfg.speed != null ? cfg.speed
                : Config.JUMP_INACCURACY_DEFAULT_SPEED.get();
        return result;
    }

    public static InaccuracyParams resolveInaccuracyParams(ResourceLocation dataId, InaccuracyType state) {
        GunTaczFixesData data = dataId == null ? null : DATA.get(dataId);
        if (data == null || data.inaccuracy_multiplier == null || data.inaccuracy_multiplier.isEmpty()) {
            return fromConfig();
        }
        String key = state == null ? "stand" : state.name().toLowerCase(Locale.ROOT);
        GunTaczFixesData.InaccuracyConfig cfg = data.inaccuracy_multiplier.get(key);
        GunTaczFixesData.InaccuracyConfig stand = data.inaccuracy_multiplier.get("stand");

        boolean cfgEnabled = Config.SPREAD_RAMP_ENABLED.get();
        int maxStack = pick(cfg == null ? null : cfg.max_stack,
                stand == null ? null : stand.max_stack, cfgEnabled ? Config.SPREAD_RAMP_MAX_STACKS.get() : 0);
        long cooldownDelay = pick(cfg == null ? null : cfg.cooldown_delay,
                stand == null ? null : stand.cooldown_delay, Config.SPREAD_RAMP_DECAY_DELAY_MS.get());
        double cooldownSpeed = pick(cfg == null ? null : cfg.cooldown_speed,
                stand == null ? null : stand.cooldown_speed, Config.SPREAD_RAMP_DECAY.get());
        double shotPercent = pick(cfg == null ? null : cfg.shot_percent,
                stand == null ? null : stand.shot_percent, cfgEnabled ? Config.SPREAD_RAMP_INCREMENT.get() : 0.0);
        double shotAddend = pick(cfg == null ? null : cfg.shot_addend,
                stand == null ? null : stand.shot_addend, cfgEnabled ? Config.SPREAD_RAMP_FLAT_INCREMENT.get() : 0.0);
        return new InaccuracyParams(maxStack, cooldownDelay, cooldownSpeed, shotPercent, shotAddend);
    }

    public static InaccuracyParams resolveInaccuracyParams(ResourceLocation dataId, InaccuracyType state, ItemStack gunItem) {
        InaccuracyParams base = resolveInaccuracyParams(dataId, state);
        if (gunItem == null) return base;
        return AttachmentTaczFixesManager.adjustInaccuracy(gunItem, base);
    }

    private static InaccuracyParams fromConfig() {
        boolean enabled = Config.SPREAD_RAMP_ENABLED.get();
        return new InaccuracyParams(
                enabled ? Config.SPREAD_RAMP_MAX_STACKS.get() : 0,
                Config.SPREAD_RAMP_DECAY_DELAY_MS.get(),
                Config.SPREAD_RAMP_DECAY.get(),
                enabled ? Config.SPREAD_RAMP_INCREMENT.get() : 0.0,
                enabled ? Config.SPREAD_RAMP_FLAT_INCREMENT.get() : 0.0);
    }

    private static <T> T pick(T primary, T stand, T def) {
        if (primary != null) return primary;
        if (stand != null) return stand;
        return def;
    }
}
