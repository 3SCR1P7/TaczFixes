package com.ssscript.taczfixes.client.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Arcana 镜内放大状态的权威判定桥: 含全局开关、每瞄具开关与 sight 视图禁用(本模组 mixin)。 */
public final class ArcanaMagnificationState {
    private static boolean resolved;
    private static Object scopeRenderer;
    private static Method magnificationMethod;
    private static Method capturePassMethod;

    private ArcanaMagnificationState() {
    }

    /** 当前视图是否真正启用镜内放大; 未安装 Arcana 或未启用时恒为 false。 */
    public static boolean active() {
        return invoke(magnification());
    }

    /** 是否处于 Arcana 镜内放大画面捕获渲染中(该次世界层 FOV 不应被本模组接管)。 */
    public static boolean capturePass() {
        return invoke(capturePassMethod());
    }

    private static boolean invoke(Method method) {
        if (method == null || scopeRenderer == null) {
            return false;
        }
        try {
            Object value = method.invoke(scopeRenderer);
            return value instanceof Boolean enabled && enabled;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static Method magnification() {
        resolve();
        return magnificationMethod;
    }

    private static Method capturePassMethod() {
        resolve();
        return capturePassMethod;
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        ClassLoader[] loaders = {ArcanaMagnificationState.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader()};
        for (ClassLoader loader : loaders) {
            if (loader == null) {
                continue;
            }
            try {
                Class<?> rendererClass = Class.forName("group.taczexpands.dist.WFkyOIm9", false, loader);
                Class<?> holderClass = Class.forName("group.taczexpands.dist.piE2wCK6", false, loader);
                Field instanceField = holderClass.getField("bFxAds5T");
                Object instance = instanceField.get(null);
                if (instance == null || !rendererClass.isInstance(instance)) {
                    continue;
                }
                scopeRenderer = instance;
                magnificationMethod = rendererClass.getMethod("VR4u5uBF");
                capturePassMethod = rendererClass.getMethod("ulmOWv7v");
                return;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            }
        }
    }
}
