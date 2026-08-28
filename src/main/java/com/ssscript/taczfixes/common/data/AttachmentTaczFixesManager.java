package com.ssscript.taczfixes.common.data;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.pojo.data.attachment.Modifier;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.ssscript.taczfixes.common.register.Config;
import com.ssscript.taczfixes.common.util.RecoilMultiplierResolver;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class AttachmentTaczFixesManager {
    private static final Map<ResourceLocation, AttachmentTaczFixesData> DATA = new ConcurrentHashMap<>();

    private AttachmentTaczFixesManager() {
    }

    public static void putAll(Map<ResourceLocation, AttachmentTaczFixesData> map) {
        DATA.clear();
        DATA.putAll(map);
    }

    public static AttachmentTaczFixesData get(ResourceLocation dataId) {
        return dataId == null ? null : DATA.get(dataId);
    }

    public static double applyLimbFactor(ItemStack gunItem, double baseLimbFactor) {
        List<Modifier> modifiers = collectValues(gunItem, data -> data.limb_factor);
        if (modifiers.isEmpty()) {
            return baseLimbFactor;
        }
        return AttachmentPropertyManager.eval(modifiers, baseLimbFactor);
    }

    public static List<GunTaczFixesData.RecoilConfig> resolveRecoilList(ItemStack gunItem, FireMode mode) {
        String key = switch (mode) {
            case AUTO -> "auto";
            case SEMI -> "semi";
            case BURST -> "burst";
            case UNKNOWN -> null;
        };
        if (key == null) return null;
        List<GunTaczFixesData.RecoilConfig> result = new ArrayList<>();
        for (AttachmentTaczFixesData data : collectData(gunItem)) {
            if (data.recoil_multiplier == null) continue;
            GunTaczFixesData.RecoilConfig config = data.recoil_multiplier.get(key);
            if (config != null && RecoilMultiplierResolver.isActive(config)) {
                result.add(config);
            }
        }
        return result.isEmpty() ? null : result;
    }

    public static float applyFireKnockback(ItemStack gunItem, float force) {
        List<Modifier> modifiers = collectValues(gunItem, data -> data.fire_knockback_power);
        if (modifiers.isEmpty()) {
            return force;
        }
        return (float) AttachmentPropertyManager.eval(modifiers, force);
    }

    public static float applyFriction(ItemStack gunItem, float base) {
        return applyModifier(gunItem, base, data -> data.friction);
    }

    public static int getRefitPointConsume(ItemStack attachmentStack) {
        if (attachmentStack == null || attachmentStack.isEmpty()) return 0;
        IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentStack);
        if (attachment == null) return 0;
        ResourceLocation id = attachment.getAttachmentId(attachmentStack);
        AttachmentTaczFixesData data = resolveData(id);
        return data == null || data.refit_point_consume == null
                ? Config.REFIT_POINT_DEFAULT_CONSUME.get()
                : data.refit_point_consume;
    }

    public static int getRefitPointUsed(ItemStack gunItem) {
        int used = 0;
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null) return 0;
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            ItemStack stack = gun.getAttachment(gunItem, type);
            if (stack.isEmpty()) continue;
            used += getRefitPointConsume(stack);
        }
        ResourceLocation gunId = gun.getGunId(gunItem);
        for (String slotId : CustomSlotManager.getSlots(gunId).keySet()) {
            used += getRefitPointConsume(CustomSlotStorage.get(gunItem, slotId));
        }
        return used;
    }

    public static float applyGravity(ItemStack gunItem, float base) {
        return applyModifier(gunItem, base, data -> data.gravity);
    }

    public static float applyBulletLife(ItemStack gunItem, float base) {
        return applyModifier(gunItem, base, data -> data.bullet_life);
    }

    /** 拉栓时间总倍率(所有已安装配件的 manual_action_time 修饰符对基准值 1 求值)。≤0 表示时长被减为 0 或以下(直接完成)。 */
    public static double getManualActionTimeFactor(ItemStack gunItem) {
        return getTimeFactor(gunItem, data -> data.manual_action_time);
    }

    /** 换弹时间总倍率(所有已安装配件的 reload_time 修饰符对基准值 1 求值)。≤0 表示时长被减为 0 或以下(直接完成)。 */
    public static double getReloadTimeFactor(ItemStack gunItem) {
        return getTimeFactor(gunItem, data -> data.reload_time);
    }

    /** 跑射延迟(冲刺开火冷却)总倍率(所有已安装配件的 sprint_time 修饰符对基准值 1 求值)。 */
    public static double getSprintTimeFactor(ItemStack gunItem) {
        return getTimeFactor(gunItem, data -> data.sprint_time);
    }

    /** 跑射延迟最终值 = 基准值 × 总倍率。 */
    public static float applySprintTime(ItemStack gunItem, float base) {
        double factor = getSprintTimeFactor(gunItem);
        if (factor == 1.0d) {
            return base;
        }
        return (float) Math.max(0.0d, base * factor);
    }

    /**
     * 弹匣容量修饰符: 在扩容弹匣(extended mag)计算结果之后生效。
     * y = (x + Σaddend) × (1 + Σpercent) × Πmultiplier, 结果至少为 1。
     */
    public static double applyAmmoAmount(ItemStack gunItem, int base) {
        List<Modifier> modifiers = collectValues(gunItem, data -> data.ammo_amount);
        if (modifiers.isEmpty()) {
            return base;
        }
        double value = AttachmentPropertyManager.eval(modifiers, base);
        return Math.max(1.0d, value);
    }

    /**
     * 开火模式解锁/禁用: fire_mode_enable 解锁(原生集合没有的模式追加到末尾),
     * fire_mode_disable 禁用(从集合中移除); 同时出现在两者中时以禁用优先。
     * 无任何调整时返回原集合引用(null 语义由调用方以 == 判断)。
     */
    @Nullable
    public static List<FireMode> adjustFireModeSet(ItemStack gunItem, List<FireMode> original) {
        Set<FireMode> enabled = collectFireModes(gunItem, data -> data.fire_mode_enable);
        Set<FireMode> disabled = collectFireModes(gunItem, data -> data.fire_mode_disable);
        if (enabled.isEmpty() && disabled.isEmpty()) {
            return null;
        }
        LinkedHashSet<FireMode> result = new LinkedHashSet<>();
        for (FireMode mode : original) {
            if (mode != null && mode != FireMode.UNKNOWN && !disabled.contains(mode)) {
                result.add(mode);
            }
        }
        for (FireMode mode : enabled) {
            if (!disabled.contains(mode)) {
                result.add(mode);
            }
        }
        if (result.isEmpty()) {
            return null;
        }
        return new ArrayList<>(result);
    }

    private static Set<FireMode> collectFireModes(ItemStack gunItem,
                                                  Function<AttachmentTaczFixesData, List<String>> getter) {
        Set<FireMode> modes = EnumSet.noneOf(FireMode.class);
        for (List<String> names : collectValues(gunItem, getter)) {
            if (names == null) continue;
            for (String name : names) {
                FireMode mode = parseFireMode(name);
                if (mode != null) {
                    modes.add(mode);
                }
            }
        }
        return modes;
    }

    @Nullable
    private static FireMode parseFireMode(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            FireMode mode = FireMode.valueOf(name.trim().toUpperCase(Locale.ROOT));
            return mode == FireMode.UNKNOWN ? null : mode;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static double getTimeFactor(ItemStack gunItem,
                                        Function<AttachmentTaczFixesData, Modifier> getter) {
        List<Modifier> modifiers = collectValues(gunItem, getter);
        if (modifiers.isEmpty()) {
            return 1.0;
        }
        return AttachmentPropertyManager.eval(modifiers, 1.0);
    }

    private static float applyModifier(ItemStack gunItem, float base,
                                       Function<AttachmentTaczFixesData, Modifier> getter) {
        List<Modifier> modifiers = collectValues(gunItem, getter);
        if (modifiers.isEmpty()) {
            return base;
        }
        return (float) AttachmentPropertyManager.eval(modifiers, base);
    }

    /** 滞空散布配置调整: 用已安装配件的 jump_inaccuracy 修饰符重写 multiplier 与 speed。无调整时返回原配置。 */
    @Nullable
    public static GunTaczFixesData.JumpInaccuracyConfig adjustJumpInaccuracy(ItemStack gunItem,
                                                                             @Nullable GunTaczFixesData.JumpInaccuracyConfig base) {
        if (gunItem == null) return base;
        List<AttachmentTaczFixesData.JumpInaccuracyAdjust> adjustments =
                collectValues(gunItem, data -> data.jump_inaccuracy);
        if (adjustments.isEmpty()) return base;
        List<Modifier> multiplierMods = new ArrayList<>();
        List<Modifier> speedMods = new ArrayList<>();
        for (AttachmentTaczFixesData.JumpInaccuracyAdjust adj : adjustments) {
            if (adj.multiplier != null) multiplierMods.add(adj.multiplier);
            if (adj.speed != null) speedMods.add(adj.speed);
        }
        if (multiplierMods.isEmpty() && speedMods.isEmpty()) return base;
        GunTaczFixesData.JumpInaccuracyConfig result = new GunTaczFixesData.JumpInaccuracyConfig();
        double baseMultiplier = base == null || base.multiplier == null ? 1.0 : base.multiplier;
        double baseSpeed = base == null || base.speed == null ? 1.0 : base.speed;
        result.multiplier = multiplierMods.isEmpty() ? (base == null ? null : base.multiplier)
                : AttachmentPropertyManager.eval(multiplierMods, baseMultiplier);
        result.speed = speedMods.isEmpty() ? (base == null ? null : base.speed)
                : AttachmentPropertyManager.eval(speedMods, baseSpeed);
        return result;
    }

    public static InaccuracyParams adjustInaccuracy(ItemStack gunItem, InaccuracyParams base) {
        if (gunItem == null || base == null) return base;
        List<AttachmentTaczFixesData.InaccuracyAdjust> adjustments =
                collectValues(gunItem, data -> data.inaccuracy_multiplier);
        if (adjustments.isEmpty()) {
            return base;
        }
        List<Modifier> maxStackMods = new ArrayList<>();
        List<Modifier> perShotMods = new ArrayList<>();
        List<Modifier> speedMods = new ArrayList<>();
        List<Modifier> delayMods = new ArrayList<>();
        for (AttachmentTaczFixesData.InaccuracyAdjust adj : adjustments) {
            if (adj.max_stack != null) maxStackMods.add(adj.max_stack);
            if (adj.per_shot != null) perShotMods.add(adj.per_shot);
            if (adj.cooldown_speed != null) speedMods.add(adj.cooldown_speed);
            if (adj.cooldown_delay != null) delayMods.add(adj.cooldown_delay);
        }
        double maxStack = maxStackMods.isEmpty() ? base.maxStack
                : AttachmentPropertyManager.eval(maxStackMods, base.maxStack);
        double delay = delayMods.isEmpty() ? base.cooldownDelay
                : AttachmentPropertyManager.eval(delayMods, base.cooldownDelay);
        double speed = speedMods.isEmpty() ? base.cooldownSpeed
                : AttachmentPropertyManager.eval(speedMods, base.cooldownSpeed);
        double shotPercent = perShotMods.isEmpty() ? base.shotPercent
                : AttachmentPropertyManager.eval(perShotMods, base.shotPercent);
        double shotAddend = perShotMods.isEmpty() ? base.shotAddend
                : AttachmentPropertyManager.eval(perShotMods, base.shotAddend);
        return new InaccuracyParams(
                Math.max(0, (int) Math.round(maxStack)),
                Math.max(0, (long) Math.round(delay)),
                Math.max(0.0, speed),
                Math.max(0.0, shotPercent),
                shotAddend);
    }

    private static <T> List<T> collectValues(ItemStack gunItem, Function<AttachmentTaczFixesData, T> getter) {
        List<T> result = new ArrayList<>();
        for (AttachmentTaczFixesData data : collectData(gunItem)) {
            T value = getter.apply(data);
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    private static List<AttachmentTaczFixesData> collectData(ItemStack gunItem) {
        List<AttachmentTaczFixesData> result = new ArrayList<>();
        if (gunItem == null) return result;
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null) return result;
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            ResourceLocation id = gun.getAttachmentId(gunItem, type);
            if (DefaultAssets.isEmptyAttachmentId(id)) continue;
            AttachmentTaczFixesData data = resolveData(id);
            if (data != null) {
                result.add(data);
            }
        }
        return result;
    }

    public static AttachmentTaczFixesData resolveData(ResourceLocation attachmentId) {
        AttachmentTaczFixesData data = get(attachmentId);
        if (data != null) return data;
        ResourceLocation dataId = TimelessAPI.getCommonAttachmentIndex(attachmentId)
                .map(index -> index.getPojo().getData())
                .orElse(attachmentId);
        return get(dataId);
    }
}
