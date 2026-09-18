package com.ssscript.taczfixes.common.util;

import com.tacz.guns.api.item.IGun;
import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class DualWieldOverrides {

    public enum ArmAnchor {
        /** 未配置: 沿用双持默认手臂渲染。 */
        UNSET,
        /** 使用 lefthand_pos 定位组。 */
        LEFT,
        /** 使用 righthand_pos 定位组。 */
        RIGHT,
        /** 不渲染手臂。 */
        NONE;

        private static ArmAnchor parse(String value, ArmAnchor fallback) {
            if (value == null) {
                return fallback;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "left" -> LEFT;
                case "right" -> RIGHT;
                case "none" -> NONE;
                default -> fallback;
            };
        }
    }

    public record ArmOffset(double x, double y, double z) {
        public static final ArmOffset ZERO = new ArmOffset(0.0d, 0.0d, 0.0d);

        public boolean isZero() {
            return this.x == 0.0d && this.y == 0.0d && this.z == 0.0d;
        }
    }

    public record HandPos(ArmAnchor anchor, boolean mirror, ArmOffset offset) {
    }

    public static final HandPos DEFAULT_ON_LEFT = new HandPos(ArmAnchor.UNSET, true, ArmOffset.ZERO);
    public static final HandPos DEFAULT_ON_RIGHT = new HandPos(ArmAnchor.UNSET, true, ArmOffset.ZERO);

    public record Value(Boolean enable, Double recoilMultiplier, Double inaccuracyMultiplier, Double focusAimRecoilMultiplier, Double focusAimInaccuracyMultiplier, Double leftOffset, Double rightOffset, HandPos onLeft, HandPos onRight) {
    }

    public static HandPos parseHandPos(GunTaczFixesData.HandPosConfig.HandPosEntry entry, HandPos fallback) {
        if (entry == null) {
            return fallback;
        }
        return new HandPos(ArmAnchor.parse(entry.pos, fallback.anchor()), entry.mirror == null ? fallback.mirror() : entry.mirror.booleanValue(), parseOffset(entry.offset, fallback.offset()));
    }

    private static ArmOffset parseOffset(java.util.List<Double> values, ArmOffset fallback) {
        if (values == null || values.size() < 3) {
            return fallback;
        }
        double x = values.get(0) == null ? Double.NaN : values.get(0).doubleValue();
        double y = values.get(1) == null ? Double.NaN : values.get(1).doubleValue();
        double z = values.get(2) == null ? Double.NaN : values.get(2).doubleValue();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return fallback;
        }
        return new ArmOffset(x / 16.0d, y / 16.0d, z / 16.0d);
    }

    public interface Provider {
        Value resolve(ResourceLocation gunId);
    }

    private static volatile Provider provider;

    private DualWieldOverrides() {
    }

    public static void setProvider(Provider newProvider) {
        provider = newProvider;
    }

    public static Value resolve(ResourceLocation gunId) {
        Provider current = provider;
        if (current == null || gunId == null) {
            return null;
        }
        return current.resolve(gunId);
    }

    private static Value resolveStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return null;
        }
        return resolve(gun.getGunId(stack));
    }

    public static Boolean enabled(ResourceLocation gunId) {
        Value value = resolve(gunId);
        return value == null ? null : value.enable();
    }

    public static Double recoilMultiplier(ItemStack stack) {
        Value value = resolveStack(stack);
        return value == null ? null : value.recoilMultiplier();
    }

    public static Double inaccuracyMultiplier(ItemStack stack) {
        Value value = resolveStack(stack);
        return value == null ? null : value.inaccuracyMultiplier();
    }

    public static Double focusAimRecoilMultiplier(ItemStack stack) {
        Value value = resolveStack(stack);
        return value == null ? null : value.focusAimRecoilMultiplier();
    }

    public static Double focusAimInaccuracyMultiplier(ItemStack stack) {
        Value value = resolveStack(stack);
        return value == null ? null : value.focusAimInaccuracyMultiplier();
    }

    public static double withFallback(Double value, double fallback) {
        return value != null && Double.isFinite(value.doubleValue()) && value.doubleValue() >= 0.0d ? value.doubleValue() : fallback;
    }

    public static double leftOffset(ItemStack stack, double fallback) {
        Value value = resolveStack(stack);
        Double offset = value == null ? null : value.leftOffset();
        return offset != null && Double.isFinite(offset.doubleValue()) ? offset.doubleValue() : fallback;
    }

    public static double rightOffset(ItemStack stack, double fallback) {
        Value value = resolveStack(stack);
        Double offset = value == null ? null : value.rightOffset();
        return offset != null && Double.isFinite(offset.doubleValue()) ? offset.doubleValue() : fallback;
    }

    public static HandPos handPosLeft(ItemStack stack, HandPos fallback) {
        Value value = resolveStack(stack);
        HandPos handPos = value == null ? null : value.onLeft();
        return handPos != null ? handPos : fallback;
    }

    public static HandPos handPosRight(ItemStack stack, HandPos fallback) {
        Value value = resolveStack(stack);
        HandPos handPos = value == null ? null : value.onRight();
        return handPos != null ? handPos : fallback;
    }
}
