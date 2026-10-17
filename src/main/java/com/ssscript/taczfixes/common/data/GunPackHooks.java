package com.ssscript.taczfixes.common.data;

import java.nio.file.Path;

/** 反射访问 TaCZ 的私有 GunPack 记录(path/name)。 */
public final class GunPackHooks {
    /** 置位时 scanExtensions 返回未经本模组过滤/排序的原始列表(供枪包管理界面使用)。 */
    public static final ThreadLocal<Boolean> RAW_SCAN = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private GunPackHooks() {
    }

    public static String nameOf(Object gunPack) {
        if (gunPack == null) return null;
        try {
            Object value = gunPack.getClass().getMethod("name").invoke(gunPack);
            return value instanceof String name ? name : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Path pathOf(Object gunPack) {
        if (gunPack == null) return null;
        try {
            Object value = gunPack.getClass().getMethod("path").invoke(gunPack);
            return value instanceof Path path ? path : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
