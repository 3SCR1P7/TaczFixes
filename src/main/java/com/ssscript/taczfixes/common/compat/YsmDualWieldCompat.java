package com.ssscript.taczfixes.common.compat;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.client.client.DualFocusAimState;
import com.ssscript.taczfixes.client.client.OffhandDisplayManager;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.tacz.guns.api.item.IGun;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3f;

/**
 * YSM 2.6.5 (NeoForge 1.21.1 构建) 双持兼容的反射桥。
 * <p>YSM 的类名/成员名在每个构建中都会重新混淆, 这里同时接受英文成员名 (其他 YSM 版本) 与
 * 1.21.1 NeoForge 构建的混淆成员名; 找不到时只输出一次性警告并放弃可选兼容行为, 不影响主流程。
 */
@OnlyIn(Dist.CLIENT)
@SuppressWarnings({"unchecked", "deprecation", "removal", "rawtypes"})
public final class YsmDualWieldCompat {
    private static final String[] ENTITY_GETTERS = {"getEntity", "ooo00OoO00OOOO0oOooOo0Oo"};
    private static final String[] MODEL_GETTERS = {"getCurrentModel", "O00OOOo00Oo0OO0000oOo0oo"};
    private static final String[] BONES_GETTERS = {"bones", "OO000o0ooOooooOOOOO0Ooo0"};
    private static final String[] EVENT_ANIMATABLE_GETTERS = {"getAnimatable", "oOoo00O0o0oO0o0oO00OO0O0"};
    private static final String[] CONTEXT_ENTITY_GETTERS = {"getEntity", "oOo0OO0O0o000OO0O000oo0o"};
    private static final String[][] MIRROR_PAIRS = {
            {"RightArm", "LeftArm"},
            {"RightForeArm", "LeftForeArm"},
            {"RightHand", "LeftHand"},
            {"RightHandLocator", "LeftHandLocator"},
            {"RightHandLocator2", "LeftHandLocator2"},
            {"RightHandLocator3", "LeftHandLocator3"},
            {"RightHandLocator4", "LeftHandLocator4"},
            {"RightHandLocator5", "LeftHandLocator5"},
            {"RightHandLocator6", "LeftHandLocator6"},
            {"RightHandLocator7", "LeftHandLocator7"},
            {"RightHandLocator8", "LeftHandLocator8"}};
    private static final AtomicBoolean REFLECTION_WARNING_LOGGED = new AtomicBoolean();
    private static final Map<Object, Map<String, Object>> MODEL_BONE_CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ClassValue<AnimatableAccess> ANIMATABLE_ACCESS = new ClassValue<>() {
        @Override
        protected AnimatableAccess computeValue(Class<?> type) {
            return new AnimatableAccess(findPublicNoArg(type, ENTITY_GETTERS), findPublicNoArg(type, MODEL_GETTERS));
        }
    };
    private static final ClassValue<Method> MODEL_BONES_ACCESS = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            return findPublicNoArg(type, BONES_GETTERS);
        }
    };
    private static final ClassValue<Method> EVENT_ANIMATABLE_ACCESS = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            return findPublicNoArg(type, EVENT_ANIMATABLE_GETTERS);
        }
    };
    private static final ClassValue<Method> CONTEXT_ENTITY_ACCESS = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            return findPublicNoArg(type, CONTEXT_ENTITY_GETTERS);
        }
    };
    private static final ClassValue<BoneAccess> BONE_ACCESS = new ClassValue<>() {
        @Override
        protected BoneAccess computeValue(Class<?> type) {
            return new BoneAccess(
                    findPublicNoArg(type, "getName", "OOO0oooOOo00OOooo0OooOOo"),
                    findPublicNoArg(type, "getRotationX", "oOo0OO0O0o000OO0O000oo0o"),
                    findPublicFloatArg(type, "setRotationX", "oOo0OO0O0o000OO0O000oo0o"),
                    findPublicNoArg(type, "getRotationY", "oOoo00O0o0oO0o0oO00OO0O0"),
                    findPublicFloatArg(type, "setRotationY", "oOoo00O0o0oO0o0oO00OO0O0"),
                    findPublicNoArg(type, "getRotationZ", "OO000o0ooOooooOOOOO0Ooo0"),
                    findPublicFloatArg(type, "setRotationZ", "OO000o0ooOooooOOOOO0Ooo0"),
                    findPublicNoArg(type, "getPositionX", "OOo0o0000Ooo0o00OO0oOOoO"),
                    findPublicFloatArg(type, "setPositionX", "OOo0o0000Ooo0o00OO0oOOoO"),
                    findPublicNoArg(type, "getPositionY", "Oo0O0OoOo0O0oOoo0000O0oO"),
                    findPublicFloatArg(type, "setPositionY", "Oo0O0OoOo0O0oOoo0000O0oO"),
                    findPublicNoArg(type, "getPositionZ", "O0o0OoOOooOo0O0OOoo0Oo00"),
                    findPublicFloatArg(type, "setPositionZ", "O0o0OoOOooOo0O0OOoo0Oo00"),
                    findPublicNoArg(type, "getScaleX", "OoooO0OO0000O00oo0Oo00OO"),
                    findPublicFloatArg(type, "setScaleX", "OoooO0OO0000O00oo0Oo00OO"),
                    findPublicNoArg(type, "getScaleY", "oOOO00ooO0oOOoOOo0OoOOOo"),
                    findPublicFloatArg(type, "setScaleY", "oOOO00ooO0oOOoOOo0OoOOOo"),
                    findPublicNoArg(type, "getScaleZ", "ooOooOO0oO00o00o0o0oOOoO"),
                    findPublicFloatArg(type, "setScaleZ", "ooOooOO0oO00o00o0o0oOOoO"),
                    findPublicNoArg(type, "isHidden", "OoOO0o0O00o00OoOO0OO0OOo"),
                    findPublicNoArg(type, "childBonesAreHiddenToo", "O0oo0O0O0O0oO0oooOOo00o0"),
                    findPublic(type, new Class[]{Boolean.TYPE, Boolean.TYPE}, "setHidden", "oOo0OO0O0o000OO0O000oo0o"),
                    findPublicNoArg(type, "getInitialRotation", "O0OO0O0o00o0o00oOoO0o0oO"));
        }
    };

    private YsmDualWieldCompat() {
    }

    /**
     * 把 YSM 已经算好的最终骨骼姿态镜像到左臂/左手 (右臂姿态由 TaCZ 主导), 并叠加聚焦低准备姿态。
     *
     * @return 实际应用了镜像的实体; 非第三人称双持或反射失败时返回 {@code null}
     */
    public static LivingEntity mirrorFinalPose(Object animatable) throws ReflectiveOperationException, IllegalArgumentException {
        if (animatable == null) {
            return null;
        }
        try {
            AnimatableAccess animatableAccess = ANIMATABLE_ACCESS.get(animatable.getClass());
            Object rawEntity = animatableAccess.entityGetter().invoke(animatable);
            if (!(rawEntity instanceof LivingEntity entity)) {
                return null;
            }
            Object model = animatableAccess.modelGetter().invoke(animatable);
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
            Object animatable = EVENT_ANIMATABLE_ACCESS.get(animationEvent.getClass()).invoke(animationEvent);
            if (animatable == null) {
                return false;
            }
            Object rawEntity = ANIMATABLE_ACCESS.get(animatable.getClass()).entityGetter().invoke(animatable);
            if (rawEntity instanceof LivingEntity entity) {
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
            Object animatable = EVENT_ANIMATABLE_ACCESS.get(animationEvent.getClass()).invoke(animationEvent);
            if (animatable == null) {
                return 0L;
            }
            Object rawEntity = ANIMATABLE_ACCESS.get(animatable.getClass()).entityGetter().invoke(animatable);
            if (rawEntity instanceof LivingEntity entity) {
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
            Object rawEntity = CONTEXT_ENTITY_ACCESS.get(context.getClass()).invoke(context);
            if (rawEntity instanceof LivingEntity entity) {
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
            Object rawEntity = CONTEXT_ENTITY_ACCESS.get(context.getClass()).invoke(context);
            if (rawEntity instanceof LivingEntity entity) {
                return getLocalOffhandShootCoolDown(entity);
            }
            return 0L;
        } catch (LinkageError | ReflectiveOperationException | RuntimeException exception) {
            logReflectionWarning("offhand fire Molang context", exception);
            return 0L;
        }
    }

    /**
     * YSM 的持枪/开火动画事件只读取主手物品。副手为枪、主手不是枪时用副手枪械顶替主手物品,
     * 让 YSM 继续选取 TaCZ 的 tac:* 持枪动画与开火动画。
     */
    public static ItemStack offhandAnimationSource(LivingEntity entity, ItemStack mainHandStack) {
        if (entity == null || mainHandStack == null || IGun.getIGunOrNull(mainHandStack) != null) {
            return mainHandStack;
        }
        if (!isThirdPersonDualWielding(entity)) {
            return mainHandStack;
        }
        ItemStack offhandStack = entity.getOffhandItem();
        return IGun.getIGunOrNull(offhandStack) != null ? offhandStack : mainHandStack;
    }

    private static long getLocalOffhandShootCoolDown(LivingEntity entity) {
        if (!(entity instanceof LocalPlayer player)) {
            return 0L;
        }
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
            Object rawBones = MODEL_BONES_ACCESS.get(model.getClass()).invoke(model);
            if (!(rawBones instanceof Map boneMap)) {
                throw new ReflectiveOperationException("YSM AnimatedGeoModel#bones did not return a Map");
            }
            Map<String, Object> resolved = new HashMap<>();
            for (Object bone : boneMap.values()) {
                if (bone != null) {
                    Object rawName = BONE_ACCESS.get(bone.getClass()).name().invoke(bone);
                    if (rawName instanceof String name) {
                        resolved.put(name, bone);
                    }
                }
            }
            MODEL_BONE_CACHE.put(model, resolved);
            return resolved;
        }
    }

    private static void mirrorBone(Object right, Object left) throws ReflectiveOperationException, IllegalArgumentException {
        if (right == null || left == null) {
            return;
        }
        BoneAccess source = BONE_ACCESS.get(right.getClass());
        BoneAccess target = BONE_ACCESS.get(left.getClass());
        target.rotationXSetter().invoke(left, readFloat(source.rotationXGetter(), right));
        target.rotationYSetter().invoke(left, -readFloat(source.rotationYGetter(), right));
        target.rotationZSetter().invoke(left, -readFloat(source.rotationZGetter(), right));
        target.positionXSetter().invoke(left, -readFloat(source.positionXGetter(), right));
        target.positionYSetter().invoke(left, readFloat(source.positionYGetter(), right));
        target.positionZSetter().invoke(left, readFloat(source.positionZGetter(), right));
        target.scaleXSetter().invoke(left, readFloat(source.scaleXGetter(), right));
        target.scaleYSetter().invoke(left, readFloat(source.scaleYGetter(), right));
        target.scaleZSetter().invoke(left, readFloat(source.scaleZGetter(), right));
        target.hiddenSetter().invoke(left, readBoolean(source.hiddenGetter(), right), readBoolean(source.childrenHiddenGetter(), right));
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
        access.rotationXSetter().invoke(bone, Mth.lerp(progress, readFloat(access.rotationXGetter(), bone), rest.rotationX()));
        access.rotationYSetter().invoke(bone, Mth.lerp(progress, readFloat(access.rotationYGetter(), bone), rest.rotationY()));
        access.rotationZSetter().invoke(bone, Mth.lerp(progress, readFloat(access.rotationZGetter(), bone), rest.rotationZ()));
        access.positionXSetter().invoke(bone, Mth.lerp(progress, readFloat(access.positionXGetter(), bone), rest.positionX()));
        access.positionYSetter().invoke(bone, Mth.lerp(progress, readFloat(access.positionYGetter(), bone), rest.positionY()));
        access.positionZSetter().invoke(bone, Mth.lerp(progress, readFloat(access.positionZGetter(), bone), rest.positionZ()));
    }

    private static RestTransform readInitialTransform(BoneAccess access, Object bone) throws ReflectiveOperationException, IllegalArgumentException {
        Object rawInitialRotation = access.initialRotationGetter().invoke(bone);
        if (!(rawInitialRotation instanceof Vector3f rotation)) {
            return RestTransform.ZERO;
        }
        return new RestTransform(0.0f, 0.0f, 0.0f, rotation.x(), rotation.y(), rotation.z());
    }

    private static float readFloat(Method method, Object owner) throws ReflectiveOperationException {
        return ((Number) method.invoke(owner)).floatValue();
    }

    private static boolean readBoolean(Method method, Object owner) throws ReflectiveOperationException {
        return ((Boolean) method.invoke(owner)).booleanValue();
    }

    private static boolean isThirdPersonDualWielding(LivingEntity entity) {
        if (!DualWieldEligibility.isDualWielding(entity)) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return entity != minecraft.player || !minecraft.options.getCameraType().isFirstPerson();
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
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new IllegalStateException("Unsupported YSM 2.6.5 member on " + type.getName());
    }

    private record AnimatableAccess(Method entityGetter, Method modelGetter) {
    }

    private record BoneAccess(Method name, Method rotationXGetter, Method rotationXSetter, Method rotationYGetter,
                              Method rotationYSetter, Method rotationZGetter, Method rotationZSetter, Method positionXGetter,
                              Method positionXSetter, Method positionYGetter, Method positionYSetter, Method positionZGetter,
                              Method positionZSetter, Method scaleXGetter, Method scaleXSetter, Method scaleYGetter,
                              Method scaleYSetter, Method scaleZGetter, Method scaleZSetter, Method hiddenGetter,
                              Method childrenHiddenGetter, Method hiddenSetter, Method initialRotationGetter) {
    }

    private record RestTransform(float positionX, float positionY, float positionZ, float rotationX, float rotationY, float rotationZ) {
        private static final RestTransform ZERO = new RestTransform(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
    }
}
