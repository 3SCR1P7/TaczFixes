package com.ssscript.taczfixes.common.util;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.ssscript.taczfixes.common.config.Config;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.runtime.ObjectMethods;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/common/util/DualWieldEligibility.class */
public final class DualWieldEligibility {
    private static final String EMBEDDED_DUAL_SMG_ID = "eos:eos_m_57cw_t2x2";
    private static volatile Rules clientRules;

    private DualWieldEligibility() {
    }

    public static boolean isDualWielding(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        Rules rules = entity.level().isClientSide() ? clientRules : getServerRules();
        return rules != null && isEligibleGun(entity.getMainHandItem(), rules) && isEligibleGun(entity.getOffhandItem(), rules);
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
                Config.DUAL_WIELD_LEFT_X_OFFSET.get(), Config.DUAL_WIELD_RIGHT_X_OFFSET.get());
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

    /* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/common/util/DualWieldEligibility$Rules.class */
    public record Rules(boolean allowOtherGunTypes, List<String> allowedTypes, List<String> allowedGuns, List<String> deniedGuns, double dualWieldRecoilMultiplier, double leftGunXOffset, double rightGunXOffset) {

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

        public Rules(boolean allowOtherGunTypes, List<String> allowedTypes, List<String> allowedGuns, List<String> deniedGuns, double dualWieldRecoilMultiplier, double leftGunXOffset, double rightGunXOffset) {
            List<String> allowedTypes2 = List.copyOf(allowedTypes);
            List<String> allowedGuns2 = List.copyOf(allowedGuns);
            List<String> deniedGuns2 = List.copyOf(deniedGuns);
            DualWieldEligibility.validateRange("dualWieldRecoilMultiplier", dualWieldRecoilMultiplier, 0.0d, 10.0d);
            DualWieldEligibility.validateRange("leftGunXOffset", leftGunXOffset, -2.0d, 0.0d);
            DualWieldEligibility.validateRange("rightGunXOffset", rightGunXOffset, 0.0d, 2.0d);
            this.allowOtherGunTypes = allowOtherGunTypes;
            this.allowedTypes = allowedTypes2;
            this.allowedGuns = allowedGuns2;
            this.deniedGuns = deniedGuns2;
            this.dualWieldRecoilMultiplier = dualWieldRecoilMultiplier;
            this.leftGunXOffset = leftGunXOffset;
            this.rightGunXOffset = rightGunXOffset;
        }
    }
}
