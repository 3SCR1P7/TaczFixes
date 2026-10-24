package com.ssscript.taczfixes.common.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Arcana 的镜内放大开关优先读取原生 scope 槽的瞄具数据。自定义槽激活时临时把
 * 原生瞄具的开关置为 true, 使自定义槽瞄具可以独立开启镜内放大; 切换回原生槽后恢复。
 */
public final class ArcanaMagnificationOverride {
    private static final String ACCESSOR_CLASS = "group.taczexpands.common.accessor.IAccessorAttachmentData";
    private static final String BY_ID_METHOD = "Iauo30Fz";
    private static final String MAGNIFICATION_FIELD = "aInPX54D";

    private static volatile Method byId;
    private static volatile Field magnificationField;
    private static volatile boolean resolved;

    private static Object overriddenData;
    private static Boolean originalValue;
    private static ResourceLocation overriddenId;

    private ArcanaMagnificationOverride() {
    }

    public static void enable(ResourceLocation nativeScopeId) {
        if (nativeScopeId == null) {
            return;
        }
        resolve();
        if (byId == null || magnificationField == null) {
            return;
        }
        if (overriddenData != null && nativeScopeId.equals(overriddenId)) {
            set(overriddenData, Boolean.TRUE);
            return;
        }
        restore();
        try {
            Object data = byId.invoke(null, nativeScopeId);
            if (data == null) {
                return;
            }
            Boolean original = (Boolean) magnificationField.get(data);
            overriddenData = data;
            originalValue = original;
            overriddenId = nativeScopeId;
            set(data, Boolean.TRUE);
        } catch (ReflectiveOperationException | RuntimeException e) {
            restore();
        }
    }

    public static void restore() {
        if (overriddenData != null && magnificationField != null) {
            set(overriddenData, originalValue);
        }
        overriddenData = null;
        originalValue = null;
        overriddenId = null;
    }

    private static void set(Object data, Boolean value) {
        try {
            magnificationField.set(data, value);
        } catch (IllegalAccessException | RuntimeException ignored) {
        }
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        synchronized (ArcanaMagnificationOverride.class) {
            if (resolved) {
                return;
            }
            resolved = true;
            Set<ClassLoader> loaders = new LinkedHashSet<>();
            loaders.add(Thread.currentThread().getContextClassLoader());
            loaders.add(ArcanaMagnificationOverride.class.getClassLoader());
            loaders.add(ClassLoader.getSystemClassLoader());
            for (ClassLoader loader : loaders) {
                if (loader == null) {
                    continue;
                }
                try {
                    Class<?> accessor = Class.forName(ACCESSOR_CLASS, false, loader);
                    Method method = accessor.getMethod(BY_ID_METHOD, ResourceLocation.class);
                    Field field = method.getReturnType().getField(MAGNIFICATION_FIELD);
                    byId = method;
                    magnificationField = field;
                    return;
                } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                }
            }
        }
    }
}
