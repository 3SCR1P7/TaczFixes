package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import com.tacz.guns.util.HitboxHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * 枪械散布混合: 把持有枪械玩家当前同时满足的多个状态(瞄准/移动/潜行/趴下)按配置规则混合成一份散布。
 * <p>配置为 false 时保持 TaCZ 原生逻辑(按优先级只取一个状态)。
 */
public final class SpreadBlend {
    public enum Mode {
        FALSE,
        ADDEND,
        MULTIPLIER,
        PERCENT
    }

    private static final String DEFAULT_MODE = "false";

    private SpreadBlend() {
    }

    /** 配置项文本转模式; 无法识别时按 false 处理。 */
    public static Mode mode() {
        return parse(Config.SPREAD_BLEND_MODE.get());
    }

    public static Mode parse(String raw) {
        if (raw == null) {
            return Mode.FALSE;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "addend" -> Mode.ADDEND;
            case "multiplier" -> Mode.MULTIPLIER;
            case "percent" -> Mode.PERCENT;
            default -> Mode.FALSE;
        };
    }

    public static String defaultMode() {
        return DEFAULT_MODE;
    }

    /** 归一化为配置/GUI 使用的规范小写取值; 无法识别时返回 false。 */
    public static String normalized(String raw) {
        return switch (parse(raw)) {
            case ADDEND -> "addend";
            case MULTIPLIER -> "multiplier";
            case PERCENT -> "percent";
            default -> DEFAULT_MODE;
        };
    }

    /**
     * 计算本次开火使用的散布。
     *
     * @param shooter 射手
     * @param values  枪械当前各状态的散布值(已含配件修正)
     * @return 混合后的散布
     */
    public static float select(LivingEntity shooter, Map<InaccuracyType, Float> values) {
        if (shooter == null || values == null) {
            return value(values, InaccuracyType.STAND);
        }
        Mode mode = mode();
        if (mode == Mode.FALSE) {
            // 保持原逻辑: 按优先级只取一个状态, 且不做额外截断
            Float raw = values.get(InaccuracyType.getInaccuracyType(shooter));
            return raw == null ? 0.0f : raw;
        }
        List<InaccuracyType> active = activeStates(shooter);
        float stand = value(values, InaccuracyType.STAND);
        if (active.isEmpty()) {
            return stand;
        }
        if (mode == Mode.ADDEND) {
            // ∑(所有非 STAND 状态散布) − (N−1)×STAND
            float total = 0.0f;
            for (InaccuracyType type : active) {
                total += value(values, type);
            }
            return Math.max(0.0f, total - (active.size() - 1) * stand);
        }
        // percent / multiplier 需要以 STAND 为基准做除法
        if (stand <= 1.0e-6f) {
            return value(values, InaccuracyType.getInaccuracyType(shooter));
        }
        InaccuracyType minType = active.get(0);
        float min = value(values, minType);
        for (InaccuracyType type : active) {
            float candidate = value(values, type);
            if (candidate < min) {
                min = candidate;
                minType = type;
            }
        }
        if (min < 0.0f) {
            min = 0.0f;
        }
        if (active.size() <= 1) {
            return min;
        }
        float accumulated = mode == Mode.PERCENT ? 0.0f : 1.0f;
        for (InaccuracyType type : active) {
            if (type == minType) {
                continue;
            }
            float ratio = value(values, type) / stand;
            accumulated = mode == Mode.PERCENT ? accumulated + ratio : accumulated * ratio;
        }
        return Math.max(0.0f, min * accumulated);
    }

    /** 当前同时满足的非 STAND 状态(不含 STAND)。 */
    private static List<InaccuracyType> activeStates(LivingEntity shooter) {
        List<InaccuracyType> states = new ArrayList<>(4);
        IGunOperator operator = IGunOperator.fromLivingEntity(shooter);
        if (operator != null && operator.getSynAimingProgress() == 1.0f) {
            states.add(InaccuracyType.AIM);
        }
        // MOJANG 的奇妙设计: 趴下的姿态名是 SWIMMING
        if (!shooter.isSwimming() && shooter.getPose() == Pose.SWIMMING) {
            states.add(InaccuracyType.LIE);
        }
        if (shooter.getPose() == Pose.CROUCHING) {
            states.add(InaccuracyType.SNEAK);
        }
        if (isMove(shooter)) {
            states.add(InaccuracyType.MOVE);
        }
        return states;
    }

    /** 与 TaCZ InaccuracyType#isMove 保持一致。 */
    private static boolean isMove(LivingEntity shooter) {
        double distance = Math.abs(shooter.walkDist - shooter.walkDistO);
        if (shooter instanceof Player player) {
            distance = HitboxHelper.getPlayerVelocity(player).length();
        }
        return distance > 0.05f;
    }

    private static float value(Map<InaccuracyType, Float> values, InaccuracyType type) {
        if (values == null || type == null) {
            return 0.0f;
        }
        Float value = values.get(type);
        return value == null ? 0.0f : Math.max(0.0f, value);
    }
}
