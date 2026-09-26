package com.ssscript.taczfixes.common.compat;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.client.render.DualFocusAimState;
import com.ssscript.taczfixes.client.render.OffhandDisplayManager;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.runtime.ObjectMethods;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public final class YsmDualWieldCompat {
    private static final String[] ENTITY_GETTERS = {"getEntity", "OO00OOOOo0Ooo0oo0o0Oo0OO"};
    private static final String[] MODEL_GETTERS = {"getCurrentModel", "OOOoOO000000o0o0oOooo0o0"};
    private static final String[] BONES_GETTERS = {"bones", "O00OOOooOoooOoo0o0o0oO0O"};
    private static final String[] EVENT_ANIMATABLE_GETTERS = {"getAnimatable", "o0OOooo0o0OO00OoOOOo0o0O"};
    private static final String[] CONTEXT_ENTITY_GETTERS = {"getEntity", "Oo0Oo0o00O00Oo0OOoOOoooo"};
    private static final String[][] MIRROR_PAIRS = {new String[]{"RightArm", "LeftArm"}, new String[]{"RightForeArm", "LeftForeArm"}, new String[]{"RightHand", "LeftHand"}, new String[]{"RightHandLocator", "LeftHandLocator"}, new String[]{"RightHandLocator2", "LeftHandLocator2"}, new String[]{"RightHandLocator3", "LeftHandLocator3"}, new String[]{"RightHandLocator4", "LeftHandLocator4"}, new String[]{"RightHandLocator5", "LeftHandLocator5"}, new String[]{"RightHandLocator6", "LeftHandLocator6"}, new String[]{"RightHandLocator7", "LeftHandLocator7"}, new String[]{"RightHandLocator8", "LeftHandLocator8"}};
    private static final AtomicBoolean REFLECTION_WARNING_LOGGED = new AtomicBoolean();
    private static final Map<Object, Map<String, Object>> MODEL_BONE_CACHE = Collections.synchronizedMap(new WeakHashMap());
    private static final ClassValue<AnimatableAccess> ANIMATABLE_ACCESS = new ClassValue<AnimatableAccess>() { // from class: com.ssscript.taczfixes.common.compat.YsmDualWieldCompat.1
        @Override // java.lang.ClassValue
        protected AnimatableAccess computeValue(Class<?> type) {
            return new AnimatableAccess(YsmDualWieldCompat.findPublicNoArg(type, YsmDualWieldCompat.ENTITY_GETTERS), YsmDualWieldCompat.findPublicNoArg(type, YsmDualWieldCompat.MODEL_GETTERS));
        }
    };
    private static final ClassValue<Method> MODEL_BONES_ACCESS = new ClassValue<Method>() { // from class: com.ssscript.taczfixes.common.compat.YsmDualWieldCompat.2
        @Override // java.lang.ClassValue
        protected Method computeValue(Class<?> type) {
            return YsmDualWieldCompat.findPublicNoArg(type, YsmDualWieldCompat.BONES_GETTERS);
        }
    };
    private static final ClassValue<Method> EVENT_ANIMATABLE_ACCESS = new ClassValue<Method>() { // from class: com.ssscript.taczfixes.common.compat.YsmDualWieldCompat.4
        @Override // java.lang.ClassValue
        protected Method computeValue(Class<?> type) {
            return YsmDualWieldCompat.findPublicNoArg(type, YsmDualWieldCompat.EVENT_ANIMATABLE_GETTERS);
        }
    };
    private static final ClassValue<Method> CONTEXT_ENTITY_ACCESS = new ClassValue<Method>() { // from class: com.ssscript.taczfixes.common.compat.YsmDualWieldCompat.5
        @Override // java.lang.ClassValue
        protected Method computeValue(Class<?> type) {
            return YsmDualWieldCompat.findPublicNoArg(type, YsmDualWieldCompat.CONTEXT_ENTITY_GETTERS);
        }
    };
    private static final ClassValue<BoneAccess> BONE_ACCESS = new ClassValue<BoneAccess>() { // from class: com.ssscript.taczfixes.common.compat.YsmDualWieldCompat.6
        @Override // java.lang.ClassValue
        protected BoneAccess computeValue(Class<?> type) {
            return new BoneAccess(YsmDualWieldCompat.findPublicNoArg(type, "getName", "oOOo0Ooo0oOoo0O0OOOOo0oo"), YsmDualWieldCompat.findPublicNoArg(type, "getRotationX", "Oo0Oo0o00O00Oo0OOoOOoooo"), YsmDualWieldCompat.findPublicFloatArg(type, "setRotationX", "Oo0Oo0o00O00Oo0OOoOOoooo"), YsmDualWieldCompat.findPublicNoArg(type, "getRotationY", "o0OOooo0o0OO00OoOOOo0o0O"), YsmDualWieldCompat.findPublicFloatArg(type, "setRotationY", "o0OOooo0o0OO00OoOOOo0o0O"), YsmDualWieldCompat.findPublicNoArg(type, "getRotationZ", "O00OOOooOoooOoo0o0o0oO0O"), YsmDualWieldCompat.findPublicFloatArg(type, "setRotationZ", "O00OOOooOoooOoo0o0o0oO0O"), YsmDualWieldCompat.findPublicNoArg(type, "getPositionX", "oOOOo0OOO0ooooo0O00OO0o0"), YsmDualWieldCompat.findPublicFloatArg(type, "setPositionX", "oOOOo0OOO0ooooo0O00OO0o0"), YsmDualWieldCompat.findPublicNoArg(type, "getPositionY", "OOOOo0O0oO0OOo0O0O0Oo0O0"), YsmDualWieldCompat.findPublicFloatArg(type, "setPositionY", "OOOOo0O0oO0OOo0O0O0Oo0O0"), YsmDualWieldCompat.findPublicNoArg(type, "getPositionZ", "Ooooo0oooO0oooOOOoO0000O"), YsmDualWieldCompat.findPublicFloatArg(type, "setPositionZ", "Ooooo0oooO0oooOOOoO0000O"), YsmDualWieldCompat.findPublicNoArg(type, "getScaleX", "oo0OoO00oOoo000O0000o0oo"), YsmDualWieldCompat.findPublicFloatArg(type, "setScaleX", "oo0OoO00oOoo000O0000o0oo"), YsmDualWieldCompat.findPublicNoArg(type, "getScaleY", "oooooooOOoOOoO00OooOo00O"), YsmDualWieldCompat.findPublicFloatArg(type, "setScaleY", "oooooooOOoOOoO00OooOo00O"), YsmDualWieldCompat.findPublicNoArg(type, "getScaleZ", "Oo00o0OooOOo0ooOoo0oO0o0"), YsmDualWieldCompat.findPublicFloatArg(type, "setScaleZ", "Oo00o0OooOOo0ooOoo0oO0o0"), YsmDualWieldCompat.findPublicNoArg(type, "isHidden", "OOOo0OOOoOO0O00Oo00ooOOO"), YsmDualWieldCompat.findPublicNoArg(type, "childBonesAreHiddenToo", "oOo0o0000OOOO0OooooO00oo"), YsmDualWieldCompat.findPublic(type, new Class[]{Boolean.TYPE, Boolean.TYPE}, "setHidden", "Oo0Oo0o00O00Oo0OOoOOoooo"), YsmDualWieldCompat.findPublicNoArg(type, "getInitialRotation", "OO0ooO00OoO00o0OO0OOooO0"));
        }
    };

    private YsmDualWieldCompat() {
    }

    public static LivingEntity mirrorFinalPose(Object animatable) throws ReflectiveOperationException, IllegalArgumentException {
        if (animatable == null) {
            return null;
        }
        try {
            AnimatableAccess animatableAccess = ANIMATABLE_ACCESS.get(animatable.getClass());
            Object rawEntity = animatableAccess.entityGetter().invoke(animatable, new Object[0]);
            if (!(rawEntity instanceof LivingEntity)) {
                return null;
            }
            LivingEntity entity = (LivingEntity) rawEntity;
            Object model = animatableAccess.modelGetter().invoke(animatable, new Object[0]);
            if (!isThirdPersonDualWielding(entity) || model == null) {
                return null;
            }
            Map<String, Object> bones = getBonesByName(model);
            for (String[] pair : MIRROR_PAIRS) {
                mirrorBone(bones.get(pair[0]), bones.get(pair[1]));
            }
            applyFocusLowReady(entity, bones);
            YsmCompatibilityDiagnostics.markFinalPoseApplied();
            return entity;
        } catch (LinkageError | ReflectiveOperationException | RuntimeException exception) {
            if (REFLECTION_WARNING_LOGGED.compareAndSet(false, true)) {
                TaczFixesMod.LOGGER.warn("Failed to apply optional YSM 2.6.5 dual-wield pose compatibility", exception);
            }
            return null;
        }
    }

    public static boolean isDualWieldAnimationEvent(Object animationEvent) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        if (animationEvent == null) {
            return false;
        }
        try {
            Object animatable = EVENT_ANIMATABLE_ACCESS.get(animationEvent.getClass()).invoke(animationEvent, new Object[0]);
            if (animatable == null) {
                return false;
            }
            Object rawEntity = ANIMATABLE_ACCESS.get(animatable.getClass()).entityGetter().invoke(animatable, new Object[0]);
            if (rawEntity instanceof LivingEntity) {
                LivingEntity entity = (LivingEntity) rawEntity;
                if (isThirdPersonDualWielding(entity)) {
                    return true;
                }
            }
            return false;
        } catch (LinkageError | ReflectiveOperationException | RuntimeException exception) {
            logReflectionWarning("animation event", exception);
            return false;
        }
    }

    public static long getOffhandShootCoolDownForAnimationEvent(Object animationEvent) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        if (animationEvent == null) {
            return 0L;
        }
        try {
            Object animatable = EVENT_ANIMATABLE_ACCESS.get(animationEvent.getClass()).invoke(animationEvent, new Object[0]);
            if (animatable == null) {
                return 0L;
            }
            Object rawEntity = ANIMATABLE_ACCESS.get(animatable.getClass()).entityGetter().invoke(animatable, new Object[0]);
            if (rawEntity instanceof LivingEntity) {
                LivingEntity entity = (LivingEntity) rawEntity;
                return getLocalOffhandShootCoolDown(entity);
            }
            return 0L;
        } catch (LinkageError | ReflectiveOperationException | RuntimeException exception) {
            logReflectionWarning("offhand fire animation event", exception);
            return 0L;
        }
    }

    public static boolean isDualWieldMolangContext(Object context) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        if (context == null) {
            return false;
        }
        try {
            Object rawEntity = CONTEXT_ENTITY_ACCESS.get(context.getClass()).invoke(context, new Object[0]);
            if (rawEntity instanceof LivingEntity) {
                LivingEntity entity = (LivingEntity) rawEntity;
                if (isThirdPersonDualWielding(entity)) {
                    return true;
                }
            }
            return false;
        } catch (LinkageError | ReflectiveOperationException | RuntimeException exception) {
            logReflectionWarning("Molang context", exception);
            return false;
        }
    }

    public static long getOffhandShootCoolDownForMolangContext(Object context) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        if (context == null) {
            return 0L;
        }
        try {
            Object rawEntity = CONTEXT_ENTITY_ACCESS.get(context.getClass()).invoke(context, new Object[0]);
            if (rawEntity instanceof LivingEntity) {
                LivingEntity entity = (LivingEntity) rawEntity;
                return getLocalOffhandShootCoolDown(entity);
            }
            return 0L;
        } catch (LinkageError | ReflectiveOperationException | RuntimeException exception) {
            logReflectionWarning("offhand fire Molang context", exception);
            return 0L;
        }
    }

    private static long getLocalOffhandShootCoolDown(LivingEntity entity) {
        if (!(entity instanceof LocalPlayer)) {
            return 0L;
        }
        LocalPlayer player = (LocalPlayer) entity;
        if (!isThirdPersonDualWielding(entity)) {
            return 0L;
        }
        ItemStack stack = player.getOffhandItem();
        if (stack.isEmpty()) {
            return 0L;
        }
        return Math.max(OffhandDisplayManager.getClientState().getShootCoolDown(player, stack), 0L);
    }

    private static Map<String, Object> getBonesByName(Object model) throws ReflectiveOperationException, IllegalArgumentException {
        synchronized (MODEL_BONE_CACHE) {
            Map<String, Object> cached = MODEL_BONE_CACHE.get(model);
            if (cached != null) {
                return cached;
            }
            Object rawBones = MODEL_BONES_ACCESS.get(model.getClass()).invoke(model, new Object[0]);
            if (!(rawBones instanceof Map)) {
                throw new ReflectiveOperationException("YSM AnimatedGeoModel#bones did not return a Map");
            }
            Map<?, ?> boneMap = (Map) rawBones;
            Map<String, Object> resolved = new HashMap<>();
            for (Object bone : boneMap.values()) {
                if (bone != null) {
                    Object rawName = BONE_ACCESS.get(bone.getClass()).name().invoke(bone, new Object[0]);
                    if (rawName instanceof String) {
                        String name = (String) rawName;
                        resolved.put(name, bone);
                    }
                }
            }
            synchronized (MODEL_BONE_CACHE) {
                MODEL_BONE_CACHE.put(model, resolved);
            }
            return resolved;
        }
    }

    private static void mirrorBone(Object right, Object left) throws ReflectiveOperationException, IllegalArgumentException {
        if (right == null || left == null) {
            return;
        }
        BoneAccess source = BONE_ACCESS.get(right.getClass());
        BoneAccess target = BONE_ACCESS.get(left.getClass());
        target.rotationXSetter().invoke(left, Float.valueOf(readFloat(source.rotationXGetter(), right)));
        target.rotationYSetter().invoke(left, Float.valueOf(-readFloat(source.rotationYGetter(), right)));
        target.rotationZSetter().invoke(left, Float.valueOf(-readFloat(source.rotationZGetter(), right)));
        target.positionXSetter().invoke(left, Float.valueOf(-readFloat(source.positionXGetter(), right)));
        target.positionYSetter().invoke(left, Float.valueOf(readFloat(source.positionYGetter(), right)));
        target.positionZSetter().invoke(left, Float.valueOf(readFloat(source.positionZGetter(), right)));
        target.scaleXSetter().invoke(left, Float.valueOf(readFloat(source.scaleXGetter(), right)));
        target.scaleYSetter().invoke(left, Float.valueOf(readFloat(source.scaleYGetter(), right)));
        target.scaleZSetter().invoke(left, Float.valueOf(readFloat(source.scaleZGetter(), right)));
        target.hiddenSetter().invoke(left, Boolean.valueOf(readBoolean(source.hiddenGetter(), right)), Boolean.valueOf(readBoolean(source.childrenHiddenGetter(), right)));
    }

    private static void applyFocusLowReady(LivingEntity entity, Map<String, Object> bones) throws ReflectiveOperationException, IllegalArgumentException {
        float progress = DualFocusAimState.getEntityProgress(entity);
        if (progress <= 0.0f) {
            return;
        }
        blendBoneTransformToRest(bones.get("LeftArm"), progress);
        blendBoneTransformToRest(bones.get("LeftForeArm"), progress);
        blendBoneTransformToRest(bones.get("LeftHand"), progress);
        blendBoneTransformToRest(bones.get("LeftHandLocator"), progress);
    }

    private static void blendBoneTransformToRest(Object bone, float progress) throws ReflectiveOperationException, IllegalArgumentException {
        if (bone == null) {
            return;
        }
        BoneAccess access = BONE_ACCESS.get(bone.getClass());
        RestTransform rest = readInitialTransform(access, bone);
        access.rotationXSetter().invoke(bone, Float.valueOf(Mth.lerp(progress, readFloat(access.rotationXGetter(), bone), rest.rotationX())));
        access.rotationYSetter().invoke(bone, Float.valueOf(Mth.lerp(progress, readFloat(access.rotationYGetter(), bone), rest.rotationY())));
        access.rotationZSetter().invoke(bone, Float.valueOf(Mth.lerp(progress, readFloat(access.rotationZGetter(), bone), rest.rotationZ())));
        access.positionXSetter().invoke(bone, Float.valueOf(Mth.lerp(progress, readFloat(access.positionXGetter(), bone), rest.positionX())));
        access.positionYSetter().invoke(bone, Float.valueOf(Mth.lerp(progress, readFloat(access.positionYGetter(), bone), rest.positionY())));
        access.positionZSetter().invoke(bone, Float.valueOf(Mth.lerp(progress, readFloat(access.positionZGetter(), bone), rest.positionZ())));
    }

    private static RestTransform readInitialTransform(BoneAccess access, Object bone) throws ReflectiveOperationException, IllegalArgumentException {
        Object rawInitialRotation = access.initialRotationGetter().invoke(bone, new Object[0]);
        if (!(rawInitialRotation instanceof Vector3f)) {
            return RestTransform.ZERO;
        }
        Vector3f rotation = (Vector3f) rawInitialRotation;
        return new RestTransform(0.0f, 0.0f, 0.0f, rotation.x(), rotation.y(), rotation.z());
    }

    private static float readFloat(Method method, Object owner) throws ReflectiveOperationException {
        return ((Number) method.invoke(owner, new Object[0])).floatValue();
    }

    private static boolean readBoolean(Method method, Object owner) throws ReflectiveOperationException {
        return ((Boolean) method.invoke(owner, new Object[0])).booleanValue();
    }

    private static boolean isThirdPersonDualWielding(LivingEntity entity) {
        if (!DualWieldEligibility.isDualWielding(entity)) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return (entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) ? false : true;
    }

    private static void logReflectionWarning(String stage, Throwable exception) {
        if (REFLECTION_WARNING_LOGGED.compareAndSet(false, true)) {
            TaczFixesMod.LOGGER.warn("Failed to resolve optional YSM 2.6.5 {} compatibility path", stage, exception);
        }
    }

    private static Method findPublicNoArg(Class<?> type, String... names) {
        return findPublic(type, new Class[0], names);
    }

    private static Method findPublicFloatArg(Class<?> type, String... names) {
        return findPublic(type, new Class[]{Float.TYPE}, names);
    }

    private static Method findPublic(Class<?> type, Class<?>[] parameterTypes, String... names) {
        for (String name : names) {
            try {
                return type.getMethod(name, parameterTypes);
            } catch (NoSuchMethodException e) {
            }
        }
        throw new IllegalStateException("Unsupported YSM 2.6.5 member on " + type.getName());
    }

    private record AnimatableAccess(Method entityGetter, Method modelGetter) {

        private AnimatableAccess(Method entityGetter, Method modelGetter) {
            this.entityGetter = entityGetter;
            this.modelGetter = modelGetter;
        }
    }

    private record BoneAccess(Method name, Method rotationXGetter, Method rotationXSetter, Method rotationYGetter, Method rotationYSetter, Method rotationZGetter, Method rotationZSetter, Method positionXGetter, Method positionXSetter, Method positionYGetter, Method positionYSetter, Method positionZGetter, Method positionZSetter, Method scaleXGetter, Method scaleXSetter, Method scaleYGetter, Method scaleYSetter, Method scaleZGetter, Method scaleZSetter, Method hiddenGetter, Method childrenHiddenGetter, Method hiddenSetter, Method initialRotationGetter) {

        private BoneAccess(Method name, Method rotationXGetter, Method rotationXSetter, Method rotationYGetter, Method rotationYSetter, Method rotationZGetter, Method rotationZSetter, Method positionXGetter, Method positionXSetter, Method positionYGetter, Method positionYSetter, Method positionZGetter, Method positionZSetter, Method scaleXGetter, Method scaleXSetter, Method scaleYGetter, Method scaleYSetter, Method scaleZGetter, Method scaleZSetter, Method hiddenGetter, Method childrenHiddenGetter, Method hiddenSetter, Method initialRotationGetter) {
            this.name = name;
            this.rotationXGetter = rotationXGetter;
            this.rotationXSetter = rotationXSetter;
            this.rotationYGetter = rotationYGetter;
            this.rotationYSetter = rotationYSetter;
            this.rotationZGetter = rotationZGetter;
            this.rotationZSetter = rotationZSetter;
            this.positionXGetter = positionXGetter;
            this.positionXSetter = positionXSetter;
            this.positionYGetter = positionYGetter;
            this.positionYSetter = positionYSetter;
            this.positionZGetter = positionZGetter;
            this.positionZSetter = positionZSetter;
            this.scaleXGetter = scaleXGetter;
            this.scaleXSetter = scaleXSetter;
            this.scaleYGetter = scaleYGetter;
            this.scaleYSetter = scaleYSetter;
            this.scaleZGetter = scaleZGetter;
            this.scaleZSetter = scaleZSetter;
            this.hiddenGetter = hiddenGetter;
            this.childrenHiddenGetter = childrenHiddenGetter;
            this.hiddenSetter = hiddenSetter;
            this.initialRotationGetter = initialRotationGetter;
        }

        public Method name() {
            return this.name;
        }

        public Method rotationXGetter() {
            return this.rotationXGetter;
        }

        public Method rotationXSetter() {
            return this.rotationXSetter;
        }

        public Method rotationYGetter() {
            return this.rotationYGetter;
        }

        public Method rotationYSetter() {
            return this.rotationYSetter;
        }

        public Method rotationZGetter() {
            return this.rotationZGetter;
        }

        public Method rotationZSetter() {
            return this.rotationZSetter;
        }

        public Method positionXGetter() {
            return this.positionXGetter;
        }

        public Method positionXSetter() {
            return this.positionXSetter;
        }

        public Method positionYGetter() {
            return this.positionYGetter;
        }

        public Method positionYSetter() {
            return this.positionYSetter;
        }

        public Method positionZGetter() {
            return this.positionZGetter;
        }

        public Method positionZSetter() {
            return this.positionZSetter;
        }

        public Method scaleXGetter() {
            return this.scaleXGetter;
        }

        public Method scaleXSetter() {
            return this.scaleXSetter;
        }

        public Method scaleYGetter() {
            return this.scaleYGetter;
        }

        public Method scaleYSetter() {
            return this.scaleYSetter;
        }

        public Method scaleZGetter() {
            return this.scaleZGetter;
        }

        public Method scaleZSetter() {
            return this.scaleZSetter;
        }

        public Method hiddenGetter() {
            return this.hiddenGetter;
        }

        public Method childrenHiddenGetter() {
            return this.childrenHiddenGetter;
        }

        public Method hiddenSetter() {
            return this.hiddenSetter;
        }

        public Method initialRotationGetter() {
            return this.initialRotationGetter;
        }
    }

    private record RestTransform(float positionX, float positionY, float positionZ, float rotationX, float rotationY, float rotationZ) {
        private static final RestTransform ZERO = new RestTransform(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);

        private RestTransform(float positionX, float positionY, float positionZ, float rotationX, float rotationY, float rotationZ) {
            this.positionX = positionX;
            this.positionY = positionY;
            this.positionZ = positionZ;
            this.rotationX = rotationX;
            this.rotationY = rotationY;
            this.rotationZ = rotationZ;
        }

        public float positionX() {
            return this.positionX;
        }

        public float positionY() {
            return this.positionY;
        }

        public float positionZ() {
            return this.positionZ;
        }

        public float rotationX() {
            return this.rotationX;
        }

        public float rotationY() {
            return this.rotationY;
        }

        public float rotationZ() {
            return this.rotationZ;
        }
    }
}
