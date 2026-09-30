package com.ssscript.taczfixes.common.util;

/** Shared wire/render representation: -1 is vanilla, otherwise 24-bit RGB. */
public final class GunLightColor {
    private GunLightColor() {}

    public static int parse(String value) {
        if (value == null || !value.matches("#[0-9a-fA-F]{6}")) return -1;
        return Integer.parseInt(value.substring(1), 16);
    }
}
