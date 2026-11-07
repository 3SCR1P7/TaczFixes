package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.resource.manager.AttachmentScriptManager;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 枪械等级经验公式。
 *
 * <p>升级所需经验由配置项 {@code gun_level.xp_formula} 决定, 语法与 TaCZ 配件数据里的
 * {@code function} 乘区完全一致(Lua 脚本): 变量 {@code x} 为等级, 把结果赋值给 {@code y},
 * 即 {@code y} = “从 0 级升到 x 级所需的累计枪械经验值”。</p>
 *
 * <p>直接复用 TaCZ 自带的 {@link AttachmentScriptManager} 编译/执行脚本(已做安全沙箱、缓存编译结果),
 * 不重复实现表达式解析器。默认公式: {@code y = 100 + 500 * x * (x - 1)}。</p>
 */
public final class GunLevelFormula {
    /** 默认表达式: y = 100 + 500x(x-1), 与 TaCZ 配件 function 乘区写法一致。 */
    public static final String DEFAULT_EXPRESSION = "y = 100 * x ^ 2 + 600 * x - 600";

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    /** level -> 累计经验; 公式变化时整体清空。 */
    private static final Map<Integer, Integer> CACHE = new ConcurrentHashMap<>();

    private static volatile String cachedScript;
    private static volatile AttachmentScriptManager.CompiledScript cachedCompiled;
    private static volatile String defaultScript;
    private static volatile AttachmentScriptManager.CompiledScript defaultCompiled;

    private GunLevelFormula() {
    }

    /** 当前配置使用的公式文本(配置缺失/为空时用默认值)。 */
    public static String expression() {
        try {
            String source = Config.GUN_LEVEL_XP_FORMULA.get();
            if (source != null && !source.isBlank()) {
                return normalize(source);
            }
        } catch (Throwable ignored) {
        }
        return DEFAULT_EXPRESSION;
    }

    /**
     * 从 0 级升到 {@code level} 级所需累计经验(level <= 0 时为 0)。
     *
     * <p>这里保证阈值严格递增(至少比上一级多 1 点): 玩家可能写出 y(1)=0 或非单调的公式,
     * 而 TaCZ 原版 tooltip 会用相邻等级阈值做除法, 零差/负差会导致除零崩溃。
     * 同时缓存每级结果, 避免二分查找时反复执行 Lua。</p>
     */
    public static int xpForLevel(int level) {
        if (level <= 0) {
            return 0;
        }
        Integer cached = CACHE.get(level);
        if (cached != null) {
            return cached;
        }
        int start = 1;
        int previous = 0;
        for (int lower = level - 1; lower >= 1; lower--) {
            Integer lowerValue = CACHE.get(lower);
            if (lowerValue != null) {
                start = lower + 1;
                previous = lowerValue;
                break;
            }
        }
        for (int current = start; current <= level; current++) {
            int raw = rawXp(current);
            int value;
            if (raw > previous) {
                value = raw;
            } else if (previous == Integer.MAX_VALUE) {
                value = Integer.MAX_VALUE;
            } else {
                value = previous + 1;
            }
            CACHE.put(current, value);
            previous = value;
        }
        Integer result = CACHE.get(level);
        return result != null ? result : rawXp(level);
    }

    /** 按公式计算原始阈值(未做单调修正)。 */
    private static int rawXp(int level) {
        if (level <= 0) {
            return 0;
        }
        double value = eval(level, false);
        if (!Double.isFinite(value) || value < 0.0D) {
            warnOnce(expression(), "结果为 " + value);
            value = eval(level, true);
        }
        if (!Double.isFinite(value) || value < 0.0D) {
            return fallback(level);
        }
        long rounded = Math.round(value);
        if (rounded <= 0L) {
            return 0;
        }
        return (int) Math.min(rounded, Integer.MAX_VALUE);
    }

    /** 根据累计经验反查等级(二分查找, 结果不超过 gun_level.max_level)。 */
    public static int levelForXp(int exp) {
        if (exp <= 0) {
            return 0;
        }
        int maxLevel = Math.max(0, Config.GUN_LEVEL_MAX_LEVEL.get());
        int low = 0;
        int high = maxLevel;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (xpForLevel(mid) <= exp) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    /** 用 TaCZ 的配件 function 求解器计算 y(x); useDefault=true 时强制使用默认公式。 */
    private static double eval(int level, boolean useDefault) {
        AttachmentScriptManager.CompiledScript compiled = useDefault ? defaultCompiled() : compiled();
        if (compiled == null) {
            if (!useDefault) {
                warnOnce(expression(), "脚本编译失败");
                return eval(level, true);
            }
            return fallbackDouble(level);
        }
        return compiled.eval(level, 0.0D);
    }

    private static AttachmentScriptManager.CompiledScript compiled() {
        String script = expression();
        if (cachedCompiled == null || !script.equals(cachedScript)) {
            synchronized (GunLevelFormula.class) {
                if (cachedCompiled == null || !script.equals(cachedScript)) {
                    cachedScript = script;
                    cachedCompiled = AttachmentScriptManager.compile(script);
                    CACHE.clear();
                }
            }
        }
        return cachedCompiled;
    }

    private static AttachmentScriptManager.CompiledScript defaultCompiled() {
        if (defaultCompiled == null || !DEFAULT_EXPRESSION.equals(defaultScript)) {
            synchronized (GunLevelFormula.class) {
                if (defaultCompiled == null || !DEFAULT_EXPRESSION.equals(defaultScript)) {
                    defaultScript = DEFAULT_EXPRESSION;
                    defaultCompiled = AttachmentScriptManager.compile(DEFAULT_EXPRESSION);
                }
            }
        }
        return defaultCompiled;
    }

    /**
     * 兼容玩家常见的两种写法:
     * <ul>
     *   <li>TaCZ 配件写法: {@code y = ...}</li>
     *   <li>Lua 函数写法: {@code return ...} → 改写为 {@code y = ...}</li>
     * </ul>
     */
    private static String normalize(String raw) {
        String text = raw.trim();
        if (text.regionMatches(true, 0, "return", 0, 6)) {
            text = text.substring(6).trim();
            if (text.startsWith("(") && text.endsWith(")")) {
                text = text.substring(1, text.length() - 1).trim();
            }
            text = "y = " + text;
        }
        return text;
    }

    private static double fallbackDouble(int level) {
        if (level <= 0) {
            return 0.0D;
        }
        return 100.0D + 500.0D * level * Math.max(0L, (long) level - 1L);
    }

    private static int fallback(int level) {
        double value = fallbackDouble(level);
        long rounded = Math.round(value);
        return (int) Math.min(Math.max(0L, rounded), Integer.MAX_VALUE);
    }

    private static void warnOnce(String source, String reason) {
        String key = source + "|" + reason;
        if (WARNED.add(key)) {
            TaczFixesMod.LOGGER.warn("Invalid gun level XP formula, falling back to default: '{}' ({})", source, reason);
        }
    }
}
