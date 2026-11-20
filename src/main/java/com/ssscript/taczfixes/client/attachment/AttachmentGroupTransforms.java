package com.ssscript.taczfixes.client.attachment;

import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import org.joml.Quaternionf;

/**
 * 配件动画的四个"功能组"：{@code camera} / {@code root} / {@code lefthand_pos} / {@code righthand_pos}。
 *
 * <p>TaCZ 的枪械动画用这四个组控制镜头旋转、整枪位移和第一人称左右手的位置。
 * 配件动画文件可以把它们当普通骨骼来写；本模组在状态机更新后读出这些组的动画增量，
 * 再由 {@link AttachmentAnimationManager} 叠加到枪械模型的对应组上一起渲染。</p>
 *
 * <p>配件模型通常没有这些骨骼，创建实例时会用
 * {@link com.tacz.guns.client.model.BedrockAnimatedModel#setFunctionalRenderer} 补一个空占位骨骼，
 * 让 TaCZ 的动画监听器有地方写值；占位骨骼不在渲染树里，不会画出任何东西。</p>
 *
 * <p>语义和枪械动画完全一致：平移/旋转/缩放都按 TaCZ 的 Bedrock 动画监听器结果读取，
 * 所以同一份动画数值挪到枪上表现相同。TaCZ 的 camera 组只支持旋转（平移通道没有监听器），
 * 这里保持一致。</p>
 */
public final class AttachmentGroupTransforms {
    public static final String ROOT = "root";
    public static final String LEFT_HAND = "lefthand_pos";
    public static final String RIGHT_HAND = "righthand_pos";
    public static final String CAMERA = "camera";
    private static final String[] ALL_NODES = {ROOT, LEFT_HAND, RIGHT_HAND, CAMERA};

    /** 一个功能组的动画增量（平移 / 旋转 / 缩放）。 */
    public static final class Group {
        public static final Group IDENTITY = new Group(0.0F, 0.0F, 0.0F, new Quaternionf(), 1.0F, 1.0F, 1.0F);

        private final float x;
        private final float y;
        private final float z;
        private final Quaternionf rotation;
        private final float scaleX;
        private final float scaleY;
        private final float scaleZ;

        private Group(float x, float y, float z, Quaternionf rotation,
                      float scaleX, float scaleY, float scaleZ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.rotation = new Quaternionf(rotation == null ? new Quaternionf() : rotation);
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.scaleZ = scaleZ;
        }

        public boolean isIdentity() {
            return Math.abs(x) < 1.0E-6F && Math.abs(y) < 1.0E-6F && Math.abs(z) < 1.0E-6F
                    && Math.abs(scaleX - 1.0F) < 1.0E-6F
                    && Math.abs(scaleY - 1.0F) < 1.0E-6F
                    && Math.abs(scaleZ - 1.0F) < 1.0E-6F
                    && isIdentityQuaternion(rotation);
        }

        /** 两个组的增量叠加（后一个作用在前一个之后）。 */
        public Group combine(Group other) {
            if (other == null || other == IDENTITY) {
                return this;
            }
            if (this == IDENTITY) {
                return other;
            }
            return new Group(x + other.x, y + other.y, z + other.z,
                    new Quaternionf(rotation).mul(other.rotation),
                    scaleX * other.scaleX, scaleY * other.scaleY, scaleZ * other.scaleZ);
        }

        /** 把本组增量叠加到目标骨骼上（调用方负责事后还原）。 */
        public void applyTo(BedrockPart part) {
            if (part == null || isIdentity()) {
                return;
            }
            part.offsetX += x;
            part.offsetY += y;
            part.offsetZ += z;
            part.additionalQuaternion.mul(rotation);
            part.xScale *= scaleX;
            part.yScale *= scaleY;
            part.zScale *= scaleZ;
        }
    }

    private Group root = Group.IDENTITY;
    private Group leftHand = Group.IDENTITY;
    private Group rightHand = Group.IDENTITY;
    private final Quaternionf cameraRotation = new Quaternionf();

    private AttachmentGroupTransforms() {
    }

    public static AttachmentGroupTransforms create() {
        return new AttachmentGroupTransforms();
    }

    /**
     * 给配件模型补上四个功能组的占位骨骼（只补缺的）。必须在第一次运行状态机动画之前调用。
     */
    public static void ensureNodes(BedrockAttachmentModel model) {
        if (model == null) {
            return;
        }
        for (String node : ALL_NODES) {
            if (model.getNode(node) == null) {
                model.setFunctionalRenderer(node, part -> null);
            }
        }
    }

    /** 读取并清空当前帧这四个组的动画增量（每次状态机更新后调用一次）。 */
    public void capture(BedrockAttachmentModel model) {
        root = readGroup(model, ROOT);
        leftHand = readGroup(model, LEFT_HAND);
        rightHand = readGroup(model, RIGHT_HAND);
        if (model != null && model.getCameraAnimationObject() != null) {
            cameraRotation.set(model.getCameraAnimationObject().rotationQuaternion);
            model.cleanCameraAnimationTransform();
        } else {
            cameraRotation.identity();
        }
    }

    /** 清理占位骨骼上的残留动画变换（模型是共享的，渲染结束必须还原）。 */
    public static void clean(BedrockAttachmentModel model) {
        if (model == null) {
            return;
        }
        try {
            model.cleanAnimationTransform();
            model.cleanCameraAnimationTransform();
        } catch (Throwable ignored) {
        }
    }

    public Group root() {
        return root;
    }

    public Group leftHand() {
        return leftHand;
    }

    public Group rightHand() {
        return rightHand;
    }

    /** camera 组的旋转增量；没有动画时返回 null。 */
    public Quaternionf cameraRotation() {
        return isIdentityQuaternion(cameraRotation) ? null : new Quaternionf(cameraRotation);
    }

    private static Group readGroup(BedrockAttachmentModel model, String node) {
        if (model == null) {
            return Group.IDENTITY;
        }
        BedrockPart part = model.getNode(node);
        if (part == null) {
            return Group.IDENTITY;
        }
        Group group = new Group(part.offsetX, part.offsetY, part.offsetZ, part.additionalQuaternion,
                part.xScale, part.yScale, part.zScale);
        part.offsetX = 0.0F;
        part.offsetY = 0.0F;
        part.offsetZ = 0.0F;
        part.additionalQuaternion.identity();
        part.xScale = 1.0F;
        part.yScale = 1.0F;
        part.zScale = 1.0F;
        return group;
    }

    private static boolean isIdentityQuaternion(Quaternionf quaternion) {
        return Math.abs(quaternion.x()) < 1.0E-6F
                && Math.abs(quaternion.y()) < 1.0E-6F
                && Math.abs(quaternion.z()) < 1.0E-6F
                && Math.abs(Math.abs(quaternion.w()) - 1.0F) < 1.0E-6F;
    }
}
