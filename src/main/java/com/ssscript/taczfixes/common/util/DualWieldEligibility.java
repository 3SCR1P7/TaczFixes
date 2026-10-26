package com.ssscript.taczfixes.common.util;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.ssscript.taczfixes.common.config.Config;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

public final class DualWieldEligibility {
    private static final String EMBEDDED_DUAL_SMG_ID = "eos:eos_m_57cw_t2x2";
    private static volatile Rules clientRules;

    /**
     * lrtactical 兼容钩子。模组未安装或 API 不匹配时为 null。
     * 全部通过反射访问, 避免本类常量池里出现 lrtactical 的类型引用,
     * 否则未安装模组时首次调用 {@link #isForbiddenDualItem(ItemStack)} 会抛
     * {@link NoClassDefFoundError} 导致进世界崩溃。
     */
    private static final LrTacticalCompat LR_TACTICAL = LrTacticalCompat.load();

    private DualWieldEligibility() {
    }

    public static boolean isDualWielding(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        boolean clientSide = entity.level().isClientSide();
        Rules rules = clientSide && clientRules != null ? clientRules : getServerRules();
        if (rules == null) {
            return false;
        }
        ItemStack main = entity.getMainHandItem();
        ItemStack off = entity.getOffhandItem();
        if (isForbiddenDualItem(main) || isForbiddenDualItem(off)) {
            return false;
        }
        boolean mainGun = IGun.getIGunOrNull(main) != null;
        boolean offGun = IGun.getIGunOrNull(off) != null;
        boolean mainEligible = mainGun && isEligibleGun(main, rules);
        boolean offEligible = offGun && isEligibleGun(off, rules);
        if (mainEligible && offEligible) {
            return true;
        }
        if (offEligible) {
            // 副手枪械：主手可为空或任意非枪械物品
            return IGun.getIGunOrNull(main) == null;
        }
        if (mainEligible) {
            // 主手枪械：副手必须是非空非枪械物品；空手仍视为普通单枪
            return !off.isEmpty() && IGun.getIGunOrNull(off) == null;
        }
        return false;
    }

    public static boolean isForbiddenDualItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        LrTacticalCompat lr = LR_TACTICAL;
        return lr != null && lr.isForbidden(stack);
    }

    public static boolean isEligibleGun(ItemStack stack) {
        return isEligibleGun(stack, getServerRules());
    }

    private static boolean isEligibleGun(ItemStack stack, Rules rules) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return false;
        }
        ResourceLocation gunId = gun.getGunId(stack);
        String normalizedId = normalize(gunId.toString());
        if (EMBEDDED_DUAL_SMG_ID.equals(normalizedId) || rules.deniedGuns().contains(normalizedId)) {
            return false;
        }
        if (rules.allowedGuns().contains(normalizedId)) {
            return true;
        }
        return ((Boolean) TimelessAPI.getCommonGunIndex(gunId).map(index -> {
            return Boolean.valueOf(isAllowedType(normalize(index.getType()), rules));
        }).orElse(false)).booleanValue();
    }

    private static boolean isAllowedType(String normalizedType, Rules rules) {
        if ("smg".equals(normalizedType) || "pistol".equals(normalizedType)) {
            return rules.allowedTypes().contains(normalizedType);
        }
        if (rules.allowedTypes().contains(normalizedType)) {
            return true;
        }
        return rules.allowOtherGunTypes();
    }

    public static Rules getServerRules() {
        return new Rules(Config.DUAL_WIELD_ALLOW_OTHER_GUN_TYPES.get(),
                normalizeAll(Config.DUAL_WIELD_ALLOWED_TYPES.get()), normalizeAll(Config.DUAL_WIELD_ALLOWED_GUNS.get()),
                normalizeAll(Config.DUAL_WIELD_DENIED_GUNS.get()), Config.DUAL_WIELD_RECOIL_MULTIPLIER.get(),
                Config.DUAL_WIELD_LEFT_X_OFFSET.get(), Config.DUAL_WIELD_RIGHT_X_OFFSET.get(),
                Config.DUAL_WIELD_INACCURACY_MULTIPLIER.get(),
                Config.DUAL_WIELD_FOCUS_AIM_RECOIL_MULTIPLIER.get(),
                Config.DUAL_WIELD_FOCUS_AIM_INACCURACY_MULTIPLIER.get());
    }

    public static double getClientFocusAimRecoilMultiplier() {
        Rules rules = clientRules;
        return rules == null ? 1.5d : rules.focusAimRecoilMultiplier();
    }

    public static double getServerInaccuracyMultiplier() {
        return getServerRules().dualWieldInaccuracyMultiplier();
    }

    public static double getServerFocusAimInaccuracyMultiplier() {
        return getServerRules().focusAimInaccuracyMultiplier();
    }

    public static double getClientRecoilMultiplier() {
        Rules rules = clientRules;
        if (rules == null) {
            return 1.0d;
        }
        return rules.dualWieldRecoilMultiplier();
    }

    public static double getClientLeftGunXOffset() {
        Rules rules = clientRules;
        if (rules == null) {
            return 0.0d;
        }
        return rules.leftGunXOffset();
    }

    public static double getClientRightGunXOffset() {
        Rules rules = clientRules;
        if (rules == null) {
            return 0.0d;
        }
        return rules.rightGunXOffset();
    }

    public static void installClientRules(Rules rules) {
        clientRules = (Rules) Objects.requireNonNull(rules);
    }

    public static void clearClientRules() {
        clientRules = null;
    }

    private static List<String> normalizeAll(List<? extends String> values) {
        ArrayList<String> normalized = new ArrayList<>(values.size());
        for (String value : values) {
            String normalizedValue = normalize(value);
            if (!normalizedValue.isEmpty() && !normalized.contains(normalizedValue)) {
                normalized.add(normalizedValue);
            }
        }
        return List.copyOf(normalized);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static void validateRange(String name, double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be finite and in [" + minimum + ", " + maximum + "]: " + value);

        }
    }

    /**
     * lrtactical 可选兼容层: 通过反射检测"消耗品 / 投掷物 / 近战武器"三类物品。
     * 使用静态工厂 {@link #load()} 在首次访问时确定可用性, 之后 {@link #isForbidden(ItemStack)}
     * 是普通 java 反射调用, 开销仅在双持判定路径上。
     */
    private static final class LrTacticalCompat {
        private static final String MOD_ID = "lrtactical";
        private static final String CLS_CONSUMABLE = "me.xjqsh.lrtactical.api.item.IConsumable";
        private static final String CLS_THROWABLE = "me.xjqsh.lrtactical.api.item.IThrowable";
        private static final String CLS_MELEE = "me.xjqsh.lrtactical.api.item.IMeleeWeapon";
        private static final String MELEE_FACTORY_METHOD = "of";

        private final Class<?> consumableClass;
        private final Class<?> throwableClass;
        private final Method meleeOfMethod;

        private LrTacticalCompat(Class<?> consumableClass, Class<?> throwableClass, Method meleeOfMethod) {
            this.consumableClass = consumableClass;
            this.throwableClass = throwableClass;
            this.meleeOfMethod = meleeOfMethod;
        }

        private static LrTacticalCompat load() {
            if (!ModList.get().isLoaded(MOD_ID)) {
                return null;
            }
            try {
                Class<?> consumableClass = Class.forName(CLS_CONSUMABLE);
                Class<?> throwableClass = Class.forName(CLS_THROWABLE);
                Class<?> meleeClass = Class.forName(CLS_MELEE);
                Method meleeOfMethod = meleeClass.getMethod(MELEE_FACTORY_METHOD, ItemStack.class);
                return new LrTacticalCompat(consumableClass, throwableClass, meleeOfMethod);
            } catch (Throwable t) {
                // 模组已安装但 API 类加载/签名不匹配: 降级为不检测, 不影响双持系统的其余功能
                return null;
            }
        }

        private boolean isForbidden(ItemStack stack) {
            Object item = stack.getItem();
            if (consumableClass.isInstance(item) || throwableClass.isInstance(item)) {
                return true;
            }
            try {
                return meleeOfMethod.invoke(null, stack) != null;
            } catch (ReflectiveOperationException e) {
                return false;
            }
        }
    }

    public record Rules(boolean allowOtherGunTypes, List<String> allowedTypes, List<String> allowedGuns, List<String> deniedGuns, double dualWieldRecoilMultiplier, double leftGunXOffset, double rightGunXOffset, double dualWieldInaccuracyMultiplier, double focusAimRecoilMultiplier, double focusAimInaccuracyMultiplier) {

        public boolean allowOtherGunTypes() {
            return this.allowOtherGunTypes;
        }

        public List<String> allowedTypes() {
            return this.allowedTypes;
        }

        public List<String> allowedGuns() {
            return this.allowedGuns;
        }

        public List<String> deniedGuns() {
            return this.deniedGuns;
        }

        public double dualWieldRecoilMultiplier() {
            return this.dualWieldRecoilMultiplier;
        }

        public double leftGunXOffset() {
            return this.leftGunXOffset;
        }

        public double rightGunXOffset() {
            return this.rightGunXOffset;
        }

        public Rules(boolean allowOtherGunTypes, List<String> allowedTypes, List<String> allowedGuns, List<String> deniedGuns, double dualWieldRecoilMultiplier, double leftGunXOffset, double rightGunXOffset, double dualWieldInaccuracyMultiplier, double focusAimRecoilMultiplier, double focusAimInaccuracyMultiplier) {
            List<String> allowedTypes2 = List.copyOf(allowedTypes);
            List<String> allowedGuns2 = List.copyOf(allowedGuns);
            List<String> deniedGuns2 = List.copyOf(deniedGuns);
            DualWieldEligibility.validateRange("dualWieldRecoilMultiplier", dualWieldRecoilMultiplier, 0.0d, 10.0d);
            DualWieldEligibility.validateRange("leftGunXOffset", leftGunXOffset, -2.0d, 0.0d);
            DualWieldEligibility.validateRange("rightGunXOffset", rightGunXOffset, 0.0d, 2.0d);
            DualWieldEligibility.validateRange("dualWieldInaccuracyMultiplier", dualWieldInaccuracyMultiplier, 0.0d, 10.0d);
            DualWieldEligibility.validateRange("focusAimRecoilMultiplier", focusAimRecoilMultiplier, 0.0d, 10.0d);
            DualWieldEligibility.validateRange("focusAimInaccuracyMultiplier", focusAimInaccuracyMultiplier, 0.0d, 10.0d);
            this.allowOtherGunTypes = allowOtherGunTypes;
            this.allowedTypes = allowedTypes2;
            this.allowedGuns = allowedGuns2;
            this.deniedGuns = deniedGuns2;
            this.dualWieldRecoilMultiplier = dualWieldRecoilMultiplier;
            this.leftGunXOffset = leftGunXOffset;
            this.rightGunXOffset = rightGunXOffset;
            this.dualWieldInaccuracyMultiplier = dualWieldInaccuracyMultiplier;
            this.focusAimRecoilMultiplier = focusAimRecoilMultiplier;
            this.focusAimInaccuracyMultiplier = focusAimInaccuracyMultiplier;
        }
    }
}