package com.ssscript.taczfixes.client.render;

import com.tacz.guns.api.client.animation.AnimationListenerSupplier;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.ObjectAnimationChannel;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.runtime.ObjectMethods;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DualManualActionAnimationFilter {
    private DualManualActionAnimationFilter() {
    }

    public static ObjectAnimation filterPrototype(ObjectAnimation prototype, AnimationListenerSupplier listenerSupplier, int track, ObjectAnimation.PlayType playType) {
        if (prototype != null && (listenerSupplier instanceof BedrockAnimatedModel)) {
            BedrockAnimatedModel model = (BedrockAnimatedModel) listenerSupplier;
            if (playType != ObjectAnimation.PlayType.LOOP && OffhandDisplayManager.canStabilizeManualActionArm(model)) {
                Set<String> holdingNodes = OffhandArmPoseResolver.resolveHoldingArmAnimationNodes(model);
                Set<String> supportNodes = OffhandArmPoseResolver.resolveSupportArmAnimationNodes(model);
                ArmMotion motion = inspectArmMotion(prototype, holdingNodes, supportNodes);
                if (!motion.hasDynamicArmMotion() || !OffhandDisplayManager.shouldStabilizeManualActionArm(model, track)) {
                    return prototype;
                }
                OffhandDisplayManager.recordManualActionArmMotion(model, motion.holdingArmDynamic(), motion.supportArmDynamic());
                return prototype;
            }
        }
        return prototype;
    }

    private static ArmMotion inspectArmMotion(ObjectAnimation prototype, Set<String> holdingNodes, Set<String> supportNodes) {
        boolean holdingArmDynamic = false;
        boolean supportArmDynamic = false;
        for (Map.Entry<String, List<ObjectAnimationChannel>> entry : prototype.getChannels().entrySet()) {
            String node = normalize(entry.getKey());
            boolean holdingNode = holdingNodes.contains(node);
            boolean supportNode = supportNodes.contains(node);
            if (holdingNode || supportNode) {
                for (ObjectAnimationChannel channel : entry.getValue()) {
                    if (channel.type == ObjectAnimationChannel.ChannelType.TRANSLATION || channel.type == ObjectAnimationChannel.ChannelType.ROTATION) {
                        if (isDynamic(channel)) {
                            holdingArmDynamic |= holdingNode;
                            supportArmDynamic |= supportNode;
                        }
                    }
                }
            }
        }
        return new ArmMotion(holdingArmDynamic, supportArmDynamic);
    }

    private static boolean isDynamic(ObjectAnimationChannel channel) {
        float[] baseline;
        if (channel == null || channel.content == null || channel.content.values == null || channel.content.values.length < 2 || (baseline = channel.content.values[0]) == null) {
            return false;
        }
        for (int keyframe = 1; keyframe < channel.content.values.length; keyframe++) {
            float[] value = channel.content.values[keyframe];
            if (value == null || value.length != baseline.length) {
                return true;
            }
            for (int component = 0; component < value.length; component++) {
                if (!nearlyEqual(baseline[component], value[component])) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean nearlyEqual(float first, float second) {
        if (Float.floatToIntBits(first) == Float.floatToIntBits(second)) {
            return true;
        }
        return Float.isFinite(first) && Float.isFinite(second) && Math.abs(first - second) <= 1.0E-6f;
    }

    private static String normalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                result.append(Character.toLowerCase(character));
            }
        }
        return result.toString();
    }

    private record ArmMotion(boolean holdingArmDynamic, boolean supportArmDynamic) {

        public boolean holdingArmDynamic() {
            return this.holdingArmDynamic;
        }

        public boolean supportArmDynamic() {
            return this.supportArmDynamic;
        }

        private boolean hasDynamicArmMotion() {
            return this.holdingArmDynamic || this.supportArmDynamic;
        }
    }
}
