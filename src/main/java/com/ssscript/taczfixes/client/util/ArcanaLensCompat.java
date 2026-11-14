package com.ssscript.taczfixes.client.util;

/** Arcana(TaCZ Arcana/taczexpands) 存在性检测: 用于切换兼容渲染路径。 */
public final class ArcanaLensCompat {

    private static Boolean installed;

    private ArcanaLensCompat() {
    }

    public static boolean installed() {
        if (installed != null) return installed;
        boolean value = false;
        try {
            Class.forName("group.taczexpands.dist.piE2wCK6");
            value = true;
        } catch (Throwable ignored) {
        }
        if (!value) {
            try {
                Class<?> modListClass = Class.forName("net.minecraftforge.fml.ModList");
                Object modList = modListClass.getMethod("get").invoke(null);
                value = Boolean.TRUE.equals(modList.getClass()
                        .getMethod("isLoaded", String.class).invoke(modList, "taczexpands"));
            } catch (Throwable ignored) {
            }
        }
        if (!value) {
            try {
                Class<?> modListClass = Class.forName("net.neoforged.fml.ModList");
                Object modList = modListClass.getMethod("get").invoke(null);
                value = Boolean.TRUE.equals(modList.getClass()
                        .getMethod("isLoaded", String.class).invoke(modList, "taczexpands"));
            } catch (Throwable ignored) {
            }
        }
        installed = value;
        return value;
    }
}
