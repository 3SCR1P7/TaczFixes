package com.ssscript.taczfixes.common.compat;

import com.tacz.guns.client.model.BedrockAttachmentModel;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/common/compat/ArcanaScopeStateBridge.class */
public final class ArcanaScopeStateBridge {
    private static final String ARCANA_SCOPE_STATE_CLASS = "group.taczexpands.dist.DWmIaTdo";
    private static final String ARCANA_SCOPE_STATE_INSTANCE_FIELD = "aksH9vla";
    private static final String ARCANA_SCOPE_MODEL_GETTER = "z7oqApF2";
    private static final String ARCANA_SCOPE_MODEL_SETTER = "EZzz3Fj6";
    private static volatile Access access;
    private static volatile boolean resolutionAttempted;

    private ArcanaScopeStateBridge() {
    }

    public static Snapshot capture() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Access resolved = resolveAccess();
        if (resolved == null) {
            return Snapshot.NO_OP;
        }
        try {
            Object model = resolved.getter.invoke(resolved.instance, new Object[0]);
            return new Snapshot(resolved, model, true);
        } catch (IllegalAccessException | RuntimeException | InvocationTargetException e) {
            return Snapshot.NO_OP;
        }
    }

    public static void restore(Snapshot snapshot) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        if (snapshot == null || !snapshot.available || snapshot.access == null) {
            return;
        }
        try {
            snapshot.access.setter.invoke(snapshot.access.instance, snapshot.model);
        } catch (IllegalAccessException | RuntimeException | InvocationTargetException e) {
        }
    }

    private static Access resolveAccess() {
        Access resolved;
        Access cached = access;
        if (cached != null) {
            return cached;
        }
        if (resolutionAttempted) {
            return null;
        }
        synchronized (ArcanaScopeStateBridge.class) {
            if (access != null) {
                return access;
            }
            if (resolutionAttempted) {
                return null;
            }
            resolutionAttempted = true;
            Set<ClassLoader> classLoaders = new LinkedHashSet<>();
            classLoaders.add(Thread.currentThread().getContextClassLoader());
            classLoaders.add(ArcanaScopeStateBridge.class.getClassLoader());
            classLoaders.add(ClassLoader.getSystemClassLoader());
            for (ClassLoader classLoader : classLoaders) {
            try {
                if (classLoader != null && (resolved = tryResolve(classLoader)) != null) {
                    access = resolved;
                    return resolved;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
            return null;
        }
    }

    private static Access tryResolve(ClassLoader classLoader) throws IllegalAccessException, NoSuchFieldException, NoSuchMethodException, ClassNotFoundException, SecurityException, IllegalArgumentException {
        try {
            Class<?> stateClass = Class.forName(ARCANA_SCOPE_STATE_CLASS, false, classLoader);
            Field instanceField = stateClass.getField(ARCANA_SCOPE_STATE_INSTANCE_FIELD);
            Object instance = instanceField.get(null);
            Method getter = stateClass.getMethod(ARCANA_SCOPE_MODEL_GETTER, new Class[0]);
            Method setter = stateClass.getMethod(ARCANA_SCOPE_MODEL_SETTER, BedrockAttachmentModel.class);
            return new Access(instance, getter, setter);
        } catch (ClassNotFoundException | IllegalAccessException | LinkageError | NoSuchFieldException | NoSuchMethodException | RuntimeException e) {
            return null;
        }
    }

    /* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/common/compat/ArcanaScopeStateBridge$Access.class */
    private static final class Access {
        private final Object instance;
        private final Method getter;
        private final Method setter;

        private Access(Object instance, Method getter, Method setter) {
            this.instance = instance;
            this.getter = getter;
            this.setter = setter;
        }
    }

    /* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/common/compat/ArcanaScopeStateBridge$Snapshot.class */
    public static final class Snapshot {
        private static final Snapshot NO_OP = new Snapshot(null, null, false);
        private final Access access;
        private final Object model;
        private final boolean available;

        private Snapshot(Access access, Object model, boolean available) {
            this.access = access;
            this.model = model;
            this.available = available;
        }
    }
}
