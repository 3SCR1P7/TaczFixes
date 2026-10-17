package com.ssscript.taczfixes.client.client;

import java.util.Locale;

/** 改装/检视动画的节点命名判定(纯函数)。 */
final class InspectModelNames {
    private static final String[] FORBIDDEN_TOKENS = {"magazine", "magzine", "mag", "clip", "loader", "bullet", "bullets", "round", "cartridge", "shell", "ammo"};

    private InspectModelNames() {
    }

    static boolean isClearlyDetachedPropName(String name) {
        String normalized = normalize(name);
        return normalized.contains("additionalmag") || normalized.contains("sparemag") || normalized.contains("newmag") || normalized.contains("magout") || normalized.contains("ammoout") || normalized.equals("loader") || normalized.equals("loaderandround") || normalized.startsWith("speedloader") || normalized.startsWith("reloader");
    }

    static boolean isGeometryExcludedBranchName(String name) {
        if (name == null || isClearlyDetachedPropName(name)) {
            return name != null;
        }
        String normalized = normalize(name);
        if (normalized.endsWith("leftmag") || normalized.endsWith("rightmag")) {
            return true;
        }
        String[] tokens = name.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        for (String token : tokens) {
            String base = token.replaceFirst("[0-9]+$", "");
            if (base.equals("magazine") || base.equals("magzine") || base.equals("mag") || base.equals("clip") || base.equals("loader") || base.equals("bullet") || base.equals("bullets") || base.equals("round") || base.equals("cartridge") || base.equals("shell") || base.equals("ammo") || base.equals("flash")) {
                return true;
            }
        }
        return false;
    }

    static boolean isForbiddenName(String name) {
        String normalized = normalize(name);
        if (isSupportHandName(name)) {
            return true;
        }
        if (name == null) {
            return false;
        }
        String[] tokens = name.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        for (String token : tokens) {
            for (String forbidden : FORBIDDEN_TOKENS) {
                if (token.equals(forbidden) || token.startsWith(forbidden)) {
                    return true;
                }
            }
        }
        return normalized.contains("magazine") || normalized.contains("magzine") || normalized.contains("lefthand") || normalized.contains("supporthand");
    }

    static boolean isAmmunitionOperationName(String name) {
        if (name == null) {
            return false;
        }
        String normalized = normalize(name);
        if (normalized.contains("magazine") || normalized.contains("magzine")) {
            return true;
        }
        String[] tokens = name.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        for (String token : tokens) {
            for (String ammunition : FORBIDDEN_TOKENS) {
                if (token.equals(ammunition) || token.startsWith(ammunition)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isSupportHandName(String name) {
        String normalized = normalize(name);
        return normalized.contains("lefthand") || normalized.contains("supporthand") || normalized.contains("handsupport") || normalized.equals("lhand");
    }

    static boolean isPureHandGeometryName(String name) {
        String normalized = normalize(name);
        return normalized.equals("righthand") || normalized.equals("righthandpos") || normalized.equals("handright") || normalized.equals("rhand") || normalized.equals("lefthand") || normalized.equals("lefthandpos") || normalized.equals("supporthand") || normalized.equals("handsupport") || normalized.equals("lhand");
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}