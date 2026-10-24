package com.ssscript.taczfixes.common.compat;

import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 把当前自定义槽瞄具的镜内视图路径同步给 Arcana 的镜内放大状态。 */
public final class ArcanaScopeViewState {
    private static final String HOLDER_CLASS = "group.taczexpands.dist.pbCVTKzh";
    private static final String HOLDER_INSTANCE_FIELD = "Z7BbWItw";
    private static final String MODEL_SETTER = "jdXrgHsG";
    private static final String VIEW_SETTER = "kNAWtll8";

    private static volatile Access access;
    private static volatile boolean resolutionAttempted;

    private ArcanaScopeViewState() {
    }

    public static void set(BedrockAttachmentModel model, int zoomNumber) {
        if (model == null) {
            return;
        }
        Access resolved = resolveAccess();
        if (resolved == null) {
            return;
        }
        try {
            resolved.modelSetter.invoke(resolved.instance, model);
            List<BedrockPart> path = model.getScopeViewPath(zoomNumber);
            if (path != null) {
                resolved.viewSetter.invoke(resolved.instance, path);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Access resolveAccess() {
        Access cached = access;
        if (cached != null) {
            return cached;
        }
        if (resolutionAttempted) {
            return null;
        }
        synchronized (ArcanaScopeViewState.class) {
            if (access != null) {
                return access;
            }
            if (resolutionAttempted) {
                return null;
            }
            resolutionAttempted = true;
            Set<ClassLoader> loaders = new LinkedHashSet<>();
            loaders.add(Thread.currentThread().getContextClassLoader());
            loaders.add(ArcanaScopeViewState.class.getClassLoader());
            loaders.add(ClassLoader.getSystemClassLoader());
            for (ClassLoader loader : loaders) {
                if (loader == null) {
                    continue;
                }
                try {
                    Class<?> holderClass = Class.forName(HOLDER_CLASS, false, loader);
                    Field instanceField = holderClass.getField(HOLDER_INSTANCE_FIELD);
                    Object instance = instanceField.get(null);
                    if (instance == null) {
                        continue;
                    }
                    Method modelSetter = holderClass.getMethod(MODEL_SETTER, BedrockAttachmentModel.class);
                    Method viewSetter = holderClass.getMethod(VIEW_SETTER, List.class);
                    access = new Access(instance, modelSetter, viewSetter);
                    return access;
                } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                }
            }
            return null;
        }
    }

    private static final class Access {
        private final Object instance;
        private final Method modelSetter;
        private final Method viewSetter;

        private Access(Object instance, Method modelSetter, Method viewSetter) {
            this.instance = instance;
            this.modelSetter = modelSetter;
            this.viewSetter = viewSetter;
        }
    }
}
