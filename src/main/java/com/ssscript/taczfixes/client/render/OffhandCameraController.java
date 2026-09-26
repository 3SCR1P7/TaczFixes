package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.modifier.ParameterizedCachePair;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.compat.shouldersurfing.ShoulderSurfingCompat;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.custom.RecoilModifier;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunRecoil;
import com.tacz.guns.resource.pojo.data.gun.GunRecoilKeyFrame;
import com.tacz.guns.util.math.MathUtil;
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.ssscript.taczfixes.common.util.DualWieldStackId;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.joml.Quaternionf;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = {Dist.CLIENT})
public final class OffhandCameraController {
    private static final float MODEL_RECOIL_GAIN = 3.0f;
    private static final float MODEL_PITCH_ROTATION_SCALE = 0.65f;
    private static final float MODEL_YAW_ROTATION_SCALE = 0.45f;
    private static final float MODEL_ROLL_ROTATION_SCALE = 0.2f;
    private static final float MODEL_BACK_TRANSLATION_SCALE = 0.0125f;
    private static final float MODEL_SIDE_TRANSLATION_SCALE = 0.0035f;
    private static final float MAX_MODEL_PITCH_DEGREES = 16.0f;
    private static final float MAX_MODEL_YAW_DEGREES = 10.0f;
    private static final float THIRD_PERSON_MINIMUM_PITCH_DEGREES = 12.0f;
    private static final float THIRD_PERSON_BACK_TRANSLATION_SCALE = 0.006f;
    private static final long MODEL_IMPULSE_VISIBLE_TIME_MS = 180;
    private static PolynomialSplineFunction pitchSplineFunction;
    private static PolynomialSplineFunction yawSplineFunction;
    private static double previousPitch;
    private static double previousYaw;
    private static float pendingModelPitch;
    private static float pendingModelYaw;
    private static UUID recoilSourceStackId;
    private static ResourceLocation recoilSourceGunId;
    private static final SplineInterpolator SPLINE_INTERPOLATOR = new SplineInterpolator();
    private static final LinearInterpolator LINEAR_INTERPOLATOR = new LinearInterpolator();
    private static final Set<String> WARNED_INVALID_RECOIL_TRACKS = ConcurrentHashMap.newKeySet();
    private static long shootTimestamp = -1;
    private static Quaternionf firstPersonShotCameraRotation = new Quaternionf();
    private static boolean firstPersonShotCameraRotationValid;

    private OffhandCameraController() {
    }

    public static void recordShot(LocalPlayer player, ItemStack stack, GunData gunData) {
        if (player == null || stack == null || stack.isEmpty() || gunData == null) {
            clearProceduralRecoil();
            return;
        }
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            clearProceduralRecoil();
            return;
        }
        ResourceLocation gunId = gun.getGunId(stack);
        recoilSourceStackId = DualWieldStackId.get(stack);
        recoilSourceGunId = gunId;
        GunRecoil recoil = gunData.getRecoil();
        if (recoil == null) {
            pitchSplineFunction = null;
            yawSplineFunction = null;
            beginRecoilTimeline(0.0f, 0.0f);
            return;
        }
        float aimingRecoilModifier = 1.0f;
        if (!player.isSwimming() && player.getPose() == Pose.SWIMMING) {
            aimingRecoilModifier = 1.0f * gunData.getCrawlRecoilMultiplier();
        }
        float aimingRecoilModifier2 = finiteOrDefault(aimingRecoilModifier, 1.0f);
        float pitchBaseModifier = aimingRecoilModifier2;
        float yawBaseModifier = aimingRecoilModifier2;
        AttachmentCacheProperty cacheProperty = null;
        try {
            cacheProperty = OffhandDisplayManager.getClientState().getAttachmentCacheForRecoil(player, stack, gun, gunData);
            ParameterizedCachePair<Float, Float> attachmentRecoilModifier = cacheProperty == null ? null : (ParameterizedCachePair) cacheProperty.getCache(RecoilModifier.ID);
            if (attachmentRecoilModifier != null) {
                pitchBaseModifier = finiteOrDefault((float) attachmentRecoilModifier.left().eval(aimingRecoilModifier2), aimingRecoilModifier2);
                yawBaseModifier = finiteOrDefault((float) attachmentRecoilModifier.right().eval(aimingRecoilModifier2), aimingRecoilModifier2);
            }
        } catch (RuntimeException exception) {
            TaczFixesMod.LOGGER.warn("Failed to resolve offhand recoil modifiers; using base gun recoil", exception);
        }
        float pitchModifier = DualRecoilMultiplier.applyOffhand(player, pitchBaseModifier);
        float yawModifier = DualRecoilMultiplier.applyOffhand(player, yawBaseModifier);
        pitchSplineFunction = buildOffhandRecoilSpline(recoil.getPitch(), pitchModifier, gunId, "pitch");
        yawSplineFunction = buildOffhandRecoilSpline(recoil.getYaw(), yawModifier, gunId, "yaw");
        beginRecoilTimeline(estimateRecoilPeak(recoil.getPitch(), pitchModifier, MAX_MODEL_PITCH_DEGREES), estimateRecoilPeak(recoil.getYaw(), yawModifier, MAX_MODEL_YAW_DEGREES));
    }

    private static void beginRecoilTimeline(float modelPitch, float modelYaw) {
        shootTimestamp = System.currentTimeMillis();
        previousPitch = 0.0d;
        previousYaw = 0.0d;
        pendingModelPitch = modelPitch;
        pendingModelYaw = modelYaw;
    }

    private static PolynomialSplineFunction buildOffhandRecoilSpline(GunRecoilKeyFrame[] keyFrames, float modifier, ResourceLocation gunId, String trackName) {
        if (keyFrames == null || keyFrames.length == 0) {
            return null;
        }
        if (!Float.isFinite(modifier)) {
            warnInvalidRecoilTrack(gunId, trackName, "modifier is not finite", null);
            return null;
        }
        double[] times = new double[keyFrames.length + 1];
        double[] values = new double[keyFrames.length + 1];
        times[0] = 0.0d;
        values[0] = 0.0d;
        double previousTime = 0.0d;
        for (int index = 0; index < keyFrames.length; index++) {
            GunRecoilKeyFrame keyFrame = keyFrames[index];
            if (keyFrame == null) {
                warnInvalidRecoilTrack(gunId, trackName, "keyframe is null", null);
                return null;
            }
            float[] range = keyFrame.getValue();
            if (range == null || range.length < 2 || !Float.isFinite(range[0]) || !Float.isFinite(range[1])) {
                warnInvalidRecoilTrack(gunId, trackName, "keyframe value range is invalid", null);
                return null;
            }
            double time = (keyFrame.getTime() * 1000.0d) + 30.0d;
            if (!Double.isFinite(time) || time <= previousTime) {
                warnInvalidRecoilTrack(gunId, trackName, "keyframe times are not strictly increasing", null);
                return null;
            }
            double sampledValue = (range[0] + (Math.random() * (range[1] - range[0]))) * modifier;
            if (!Double.isFinite(sampledValue)) {
                warnInvalidRecoilTrack(gunId, trackName, "sampled recoil is not finite", null);
                return null;
            }
            times[index + 1] = time;
            values[index + 1] = sampledValue;
            previousTime = time;
        }
        try {
            if (keyFrames.length == 1) {
                return LINEAR_INTERPOLATOR.interpolate(times, values);
            }
            return SPLINE_INTERPOLATOR.interpolate(times, values);
        } catch (RuntimeException exception) {
            warnInvalidRecoilTrack(gunId, trackName, "interpolation failed", exception);
            return null;
        }
    }

    private static void warnInvalidRecoilTrack(ResourceLocation gunId, String trackName, String reason, RuntimeException exception) {
        String key = String.valueOf(gunId) + ":" + trackName;
        if (!WARNED_INVALID_RECOIL_TRACKS.add(key)) {
            return;
        }
        if (exception == null) {
            TaczFixesMod.LOGGER.warn("Ignored invalid offhand {} recoil track for {}: {}", trackName, gunId, reason);
        } else {
            TaczFixesMod.LOGGER.warn("Ignored invalid offhand {} recoil track for {}: {}", trackName, gunId, reason, exception);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void applyCameraRecoil(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        applyPendingRecoil(player);
        // 与主手一致: 枪械动画里的视角摇晃只属于第一人称, 第三人称不应用
        if (DualWieldEligibility.isDualWielding(player) && DualWieldClient.isDualMode(player)
                && ((Boolean) Minecraft.getInstance().options.bobView().get()).booleanValue()
                && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            if (shootTimestamp < 0 || System.currentTimeMillis() - shootTimestamp > MODEL_IMPULSE_VISIBLE_TIME_MS) {
                firstPersonShotCameraRotationValid = false;
            }
            if (firstPersonShotCameraRotationValid) {
                applyRotationToEvent(event, firstPersonShotCameraRotation);
            }
        }
    }

    /** 第一人称副手模型渲染时捕获枪械动画的相机轨道(camera), 供视角摇晃回放。 */
    static void captureFirstPersonShotCamera(LocalPlayer player, ItemStack stack, BedrockGunModel model) {
        if (player == null || stack == null || model == null || !DualWieldClient.isDualMode(player)
                || !((Boolean) Minecraft.getInstance().options.bobView().get()).booleanValue()
                || !matchesRecoilSource(stack)) {
            return;
        }
        Quaternionf rotation = getMirroredCameraRotation(player, stack, model);
        if (!isFinite(rotation)) {
            return;
        }
        if ((rotation.x() * rotation.x()) + (rotation.y() * rotation.y()) + (rotation.z() * rotation.z()) <= 1.0E-8f) {
            return;
        }
        firstPersonShotCameraRotation = rotation;
        firstPersonShotCameraRotationValid = true;
    }

    static void applyModelCameraAnimation(LocalPlayer player, ItemStack stack, BedrockGunModel model, PoseStack poseStack) {
        if (player == null || stack == null || model == null || poseStack == null || !DualWieldClient.isDualMode(player) || !((Boolean) Minecraft.getInstance().options.bobView().get()).booleanValue()) {
            return;
        }
        Quaternionf cameraRotation = getMirroredCameraRotation(player, stack, model);
        poseStack.mulPose(cameraRotation);
        model.cleanCameraAnimationTransform();
    }

    static void applyPendingRecoil(LocalPlayer player) {
        if (!DualWieldEligibility.isDualWielding(player)) {
            reset();
        } else if (player == null || !matchesRecoilSource(player.getOffhandItem())) {
            clearProceduralRecoil();
        } else {
            applyProceduralRecoil(player);
        }
    }

    private static void applyProceduralRecoil(LocalPlayer player) {
        if (player == null || shootTimestamp < 0) {
            return;
        }
        long elapsedMillis = System.currentTimeMillis() - shootTimestamp;
        if (pitchSplineFunction != null && pitchSplineFunction.isValidPoint(elapsedMillis)) {
            double pitch = pitchSplineFunction.value(elapsedMillis);
            if (!Double.isFinite(pitch)) {
                pitchSplineFunction = null;
                return;
            }
            float delta = (float) (pitch - previousPitch);
            try {
            if (!ShoulderSurfingBridge.applyPitch(delta)) {
                player.setXRot(player.getXRot() - delta);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
            previousPitch = pitch;
        }
        if (yawSplineFunction != null && yawSplineFunction.isValidPoint(elapsedMillis)) {
            double yaw = yawSplineFunction.value(elapsedMillis);
            if (!Double.isFinite(yaw)) {
                yawSplineFunction = null;
                return;
            }
            float delta2 = (float) (yaw - previousYaw);
            try {
            if (!ShoulderSurfingBridge.applyYaw(delta2)) {
                player.setYRot(player.getYRot() - delta2);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
            previousYaw = yaw;
        }
    }

    static void applyModelRecoil(PoseStack poseStack, ItemStack renderedStack, boolean thirdPerson) {
        float f;
        LocalPlayer player = Minecraft.getInstance().player;
        if (poseStack == null || !DualWieldEligibility.isDualWielding(player) || shootTimestamp < 0 || !matchesRecoilSource(renderedStack)) {
            return;
        }
        long elapsedMillis = System.currentTimeMillis() - shootTimestamp;
        float pitch = clampFinite(sampleSpline(pitchSplineFunction, elapsedMillis) * 3.0d, -16.0f, MAX_MODEL_PITCH_DEGREES);
        float yaw = clampFinite(sampleSpline(yawSplineFunction, elapsedMillis) * 3.0d, -10.0f, MAX_MODEL_YAW_DEGREES);
        if (elapsedMillis >= 0 && elapsedMillis <= MODEL_IMPULSE_VISIBLE_TIME_MS) {
            float envelope = 1.0f - (elapsedMillis / 180.0f);
            float minimumPitch = pendingModelPitch * MODEL_RECOIL_GAIN * envelope * MODEL_PITCH_ROTATION_SCALE;
            float minimumYaw = pendingModelYaw * MODEL_RECOIL_GAIN * envelope * MODEL_PITCH_ROTATION_SCALE;
            if (Math.abs(pitch) < Math.abs(minimumPitch)) {
                pitch = clampFinite(minimumPitch, -16.0f, MAX_MODEL_PITCH_DEGREES);
            }
            if (Math.abs(yaw) < Math.abs(minimumYaw)) {
                yaw = clampFinite(minimumYaw, -10.0f, MAX_MODEL_YAW_DEGREES);
            }
            if (thirdPerson) {
                float pitchDirection = pendingModelPitch < 0.0f ? -1.0f : 1.0f;
                float visiblePitch = pitchDirection * THIRD_PERSON_MINIMUM_PITCH_DEGREES * envelope;
                if (Math.abs(pitch) < Math.abs(visiblePitch)) {
                    pitch = clampFinite(visiblePitch, -16.0f, MAX_MODEL_PITCH_DEGREES);
                }
            }
        }
        if (Math.abs(pitch) < 1.0E-4f && Math.abs(yaw) < 1.0E-4f) {
            return;
        }
        if (thirdPerson) {
            f = THIRD_PERSON_BACK_TRANSLATION_SCALE;
        } else {
            f = MODEL_BACK_TRANSLATION_SCALE;
        }
        float backTranslationScale = f;
        poseStack.translate((-yaw) * MODEL_SIDE_TRANSLATION_SCALE, (-Math.max(pitch, 0.0f)) * 0.0025f, Math.max(Math.abs(pitch), Math.abs(yaw) * 0.5f) * backTranslationScale);
        poseStack.mulPose(Axis.XP.rotationDegrees((-pitch) * MODEL_PITCH_ROTATION_SCALE));
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw * MODEL_YAW_ROTATION_SCALE));
        poseStack.mulPose(Axis.ZP.rotationDegrees((-yaw) * MODEL_ROLL_ROTATION_SCALE));
    }

    private static double sampleSpline(PolynomialSplineFunction spline, long elapsedMillis) {
        if (spline == null || elapsedMillis < 0 || !spline.isValidPoint(elapsedMillis)) {
            return 0.0d;
        }
        try {
            double value = spline.value(elapsedMillis);
            if (Double.isFinite(value)) {
                return value;
            }
            return 0.0d;
        } catch (RuntimeException e) {
            return 0.0d;
        }
    }

    private static float clampFinite(double value, float minimum, float maximum) {
        if (!Double.isFinite(value)) {
            return 0.0f;
        }
        return (float) Math.max(minimum, Math.min(maximum, value));
    }

    private static float estimateRecoilPeak(GunRecoilKeyFrame[] keyFrames, float modifier, float maximum) {
        if (keyFrames == null || !Float.isFinite(modifier)) {
            return 0.0f;
        }
        float peak = 0.0f;
        for (GunRecoilKeyFrame keyFrame : keyFrames) {
            if (keyFrame != null && keyFrame.getValue() != null) {
                for (float value : keyFrame.getValue()) {
                    float modifiedValue = value * modifier;
                    if (Float.isFinite(modifiedValue) && Math.abs(modifiedValue) > Math.abs(peak)) {
                        peak = modifiedValue;
                    }
                }
            }
        }
        return clampFinite(peak, -maximum, maximum);
    }

    private static void applyLevelCameraAnimation(ViewportEvent.ComputeCameraAngles event, LocalPlayer player) {
        ItemStack stack = getRenderedOffhandStack(player);
        BedrockGunModel model = getOffhandModel(stack);
        if (model == null) {
            return;
        }
        Quaternionf rotation = getMirroredCameraRotation(player, stack, model);
        applyRotationToEvent(event, rotation);
    }

    private static void applyRotationToEvent(ViewportEvent.ComputeCameraAngles event, Quaternionf rotation) {
        double yawArgument = 2.0d * ((rotation.w() * rotation.y()) - (rotation.x() * rotation.z()));
        double yaw = Math.asin(Math.max(-1.0d, Math.min(1.0d, yawArgument)));
        double pitch = Math.atan2(2.0d * ((rotation.w() * rotation.x()) + (rotation.y() * rotation.z())), 1.0d - (2.0d * ((rotation.x() * rotation.x()) + (rotation.y() * rotation.y()))));
        double roll = Math.atan2(2.0d * ((rotation.w() * rotation.z()) + (rotation.x() * rotation.y())), 1.0d - (2.0d * ((rotation.y() * rotation.y()) + (rotation.z() * rotation.z()))));
        event.setYaw(event.getYaw() + ((float) Math.toDegrees(yaw)));
        event.setPitch(event.getPitch() + ((float) Math.toDegrees(pitch)));
        event.setRoll(event.getRoll() + ((float) Math.toDegrees(roll)));
    }

    private static Quaternionf getMirroredCameraRotation(LocalPlayer player, ItemStack stack, BedrockGunModel model) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return new Quaternionf();
        }
        float multiplier = 1.0f - DualFocusAimState.getFrameProgress();
        Quaternionf source = model.getCameraAnimationObject().rotationQuaternion;
        float lengthSquared = (source.x() * source.x()) + (source.y() * source.y()) + (source.z() * source.z()) + (source.w() * source.w());
        if (!isFinite(source) || !Float.isFinite(lengthSquared) || lengthSquared <= 1.0E-8f) {
            return new Quaternionf();
        }
        Quaternionf mirrored = new Quaternionf(source.x(), -source.y(), -source.z(), source.w());
        mirrored.normalize();
        Quaternionf result = MathUtil.multiplyQuaternion(mirrored, multiplier);
        return isFinite(result) ? result : new Quaternionf();
    }

    private static BedrockGunModel getOffhandModel(ItemStack stack) {
        GunDisplayInstance display = OffhandDisplayManager.getOrCreate(stack);
        if (display == null) {
            return null;
        }
        return display.getGunModel();
    }

    private static ItemStack getRenderedOffhandStack(LocalPlayer player) {
        if (OffhandDisplayManager.isPuttingAway()) {
            return OffhandDisplayManager.getPutAwayStack();
        }
        return player == null ? ItemStack.EMPTY : player.getOffhandItem();
    }

    private static boolean isFinite(Quaternionf quaternion) {
        return Float.isFinite(quaternion.x()) && Float.isFinite(quaternion.y()) && Float.isFinite(quaternion.z()) && Float.isFinite(quaternion.w());
    }

    private static float finiteOrDefault(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static boolean matchesRecoilSource(ItemStack stack) {
        IGun gun;
        if (stack == null || stack.isEmpty() || recoilSourceGunId == null || (gun = IGun.getIGunOrNull(stack)) == null || !recoilSourceGunId.equals(gun.getGunId(stack))) {
            return false;
        }
        UUID stackId = DualWieldStackId.get(stack);
        return recoilSourceStackId == null || recoilSourceStackId.equals(stackId);
    }

    private static void clearProceduralRecoil() {
        pitchSplineFunction = null;
        yawSplineFunction = null;
        shootTimestamp = -1L;
        previousPitch = 0.0d;
        previousYaw = 0.0d;
        pendingModelPitch = 0.0f;
        pendingModelYaw = 0.0f;
        recoilSourceStackId = null;
        recoilSourceGunId = null;
        firstPersonShotCameraRotationValid = false;
    }

    static void reset() {
        if (DualWieldEligibility.isDualWielding(Minecraft.getInstance().player)) {
            return;
        }
        clearProceduralRecoil();
    }

    private static final class ShoulderSurfingBridge {
        private static final String API_CLASS = "com.github.exopandora.shouldersurfing.api.client.ShoulderSurfing";
        private static boolean initialized;
        private static boolean unavailable;
        private static Method getInstanceMethod;
        private static Method isShoulderSurfingMethod;
        private static Method getCameraMethod;
        private static Method getXRotMethod;
        private static Method setXRotMethod;
        private static Method getYRotMethod;
        private static Method setYRotMethod;

        private ShoulderSurfingBridge() {
        }

        private static boolean applyPitch(float delta) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, ClassNotFoundException {
            Object camera = getActiveCamera();
            if (camera == null) {
                return false;
            }
            try {
                float current = ((Number) getXRotMethod.invoke(camera, new Object[0])).floatValue();
                setXRotMethod.invoke(camera, Float.valueOf(current - delta));
                return true;
            } catch (IllegalAccessException | InvocationTargetException exception) {
                disable(exception);
                return false;
            }
        }

        private static boolean applyYaw(float delta) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, ClassNotFoundException {
            Object camera = getActiveCamera();
            if (camera == null) {
                return false;
            }
            try {
                float current = ((Number) getYRotMethod.invoke(camera, new Object[0])).floatValue();
                setYRotMethod.invoke(camera, Float.valueOf(current - delta));
                return true;
            } catch (IllegalAccessException | InvocationTargetException exception) {
                disable(exception);
                return false;
            }
        }

        private static Object getActiveCamera() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, ClassNotFoundException {
            if (!ShoulderSurfingCompat.isInstalled() || unavailable || !initialize()) {
                return null;
            }
            try {
                Object instance = getInstanceMethod.invoke(null, new Object[0]);
                boolean active = ((Boolean) isShoulderSurfingMethod.invoke(instance, new Object[0])).booleanValue();
                if (active) {
                    return getCameraMethod.invoke(instance, new Object[0]);
                }
                return null;
            } catch (IllegalAccessException | InvocationTargetException exception) {
                disable(exception);
                return null;
            }
        }

        private static boolean initialize() throws ClassNotFoundException {
            if (initialized) {
                return true;
            }
            try {
                Class<?> shoulderSurfingClass = Class.forName(API_CLASS);
                getInstanceMethod = shoulderSurfingClass.getMethod("getInstance", new Class[0]);
                isShoulderSurfingMethod = shoulderSurfingClass.getMethod("isShoulderSurfing", new Class[0]);
                getCameraMethod = shoulderSurfingClass.getMethod("getCamera", new Class[0]);
                Class<?> cameraClass = getCameraMethod.getReturnType();
                getXRotMethod = cameraClass.getMethod("getXRot", new Class[0]);
                setXRotMethod = cameraClass.getMethod("setXRot", Float.TYPE);
                getYRotMethod = cameraClass.getMethod("getYRot", new Class[0]);
                setYRotMethod = cameraClass.getMethod("setYRot", Float.TYPE);
                initialized = true;
                return true;
            } catch (ClassNotFoundException | LinkageError | NoSuchMethodException exception) {
                disable(exception);
                return false;
            }
        }

        private static void disable(Throwable throwable) {
            if (!unavailable) {
                TaczFixesMod.LOGGER.warn("Shoulder Surfing camera bridge is unavailable; using player camera recoil instead", throwable);
            }
            unavailable = true;
        }
    }
}
