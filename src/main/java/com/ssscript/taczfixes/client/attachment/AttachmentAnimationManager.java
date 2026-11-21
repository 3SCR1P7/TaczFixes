package com.ssscript.taczfixes.client.attachment;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import org.joml.Quaternionf;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.index.ClientAttachmentVariantIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 配件动画管理器。
 *
 * <p>资源重载时解析 {@code display/attachments/*.json} 的 {@code taczfixes} 块并清空全部状态机实例；
 * 状态机在第一次渲染时懒创建，避免与 TaCZ 自身资源加载顺序耦合。
 *
 * <p>状态机实例按“配件 id + 配件 ItemStack + 枪械 ItemStack + 第一/第三人称”区分，
 * 不同槽位与视角互相独立。模型本身由 {@code ClientAttachmentIndex} 全局缓存，
 * 所以动画变换只在一次渲染内有效，渲染结束立即清理。
 */
public final class AttachmentAnimationManager {
    public static final String INPUT_AIM = "aim";
    public static final String INPUT_AIM_STOP = "aim_stop";
    public static final String INPUT_MELEE = "melee";
    public static final String INPUT_BOLT_ALIAS = "bolt";

    private static final int MAX_INSTANCES = 64;
    private static final long DEF_RETRY_INTERVAL_MS = 1000L;
    private static final long CREATE_RETRY_INTERVAL_MS = 4000L;

    private static Map<ResourceLocation, AttachmentAnimationDef> definitionsByDisplayId = Map.of();
    private static final Map<ResourceLocation, AttachmentAnimationDef> resolvedDefinitions = new HashMap<>();
    private static final Map<ResourceLocation, Long> failedDefinitionLookups = new HashMap<>();

    private static final LinkedHashMap<AttachmentAnimationKey, AttachmentAnimationInstance> INSTANCES =
            new LinkedHashMap<>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<AttachmentAnimationKey, AttachmentAnimationInstance> eldest) {
                    if (size() > MAX_INSTANCES) {
                        eldest.getValue().exit();
                        return true;
                    }
                    return false;
                }
            };
    private static final Map<AttachmentAnimationKey, Long> failedInstances = new HashMap<>();

    /** 渲染调用可能是嵌套的（配件内部再渲染配件），用栈保证每层都能正确清理。 */
    private static final java.util.LinkedList<BedrockAttachmentModel> RENDER_STACK = new java.util.LinkedList<>();
    /** 枪械渲染入口的叠加层栈：枪械模型按 display 共享，渲染结束必须还原。 */
    private static final java.util.LinkedList<Object> GUN_RENDER_STACK = new java.util.LinkedList<>();
    private static boolean lastAiming;

    private AttachmentAnimationManager() {
    }

    // ------------------------------------------------------------------
    // 资源重载
    // ------------------------------------------------------------------

    /** 资源重载完成后调用（主线程）：替换定义表、销毁全部状态机。 */
    public static void onResourceReload(Map<ResourceLocation, AttachmentAnimationDef> definitions) {
        for (AttachmentAnimationInstance instance : INSTANCES.values()) {
            instance.exit();
        }
        INSTANCES.clear();
        failedInstances.clear();
        resolvedDefinitions.clear();
        failedDefinitionLookups.clear();
        definitionsByDisplayId = definitions == null ? Map.of() : Map.copyOf(definitions);
        RENDER_STACK.clear();
        GUN_RENDER_STACK.clear();
        lastAiming = false;
    }

    /** 退出世界时释放实例（避免持有旧 ItemStack）。 */
    public static void clearInstances() {
        for (AttachmentAnimationInstance instance : INSTANCES.values()) {
            instance.exit();
        }
        INSTANCES.clear();
        failedInstances.clear();
        RENDER_STACK.clear();
        GUN_RENDER_STACK.clear();
        lastAiming = false;
    }

    // ------------------------------------------------------------------
    // 定义解析
    // ------------------------------------------------------------------

    /** 通过配件注册名找到动画定义；找不到时静默返回 null。 */
    @Nullable
    public static AttachmentAnimationDef resolveDefinition(ResourceLocation attachmentId) {
        if (attachmentId == null || definitionsByDisplayId.isEmpty()) {
            return null;
        }
        AttachmentAnimationDef resolved = resolvedDefinitions.get(attachmentId);
        if (resolved != null) {
            return resolved;
        }
        Long failedAt = failedDefinitionLookups.get(attachmentId);
        long now = System.currentTimeMillis();
        if (failedAt != null && now - failedAt < DEF_RETRY_INTERVAL_MS) {
            return null;
        }
        AttachmentAnimationDef definition = null;
        try {
            Optional<com.tacz.guns.resource.index.CommonAttachmentIndex> commonIndex =
                    TimelessAPI.getCommonAttachmentIndex(attachmentId);
            if (commonIndex.isPresent() && commonIndex.get().getPojo() != null) {
                ResourceLocation displayId = commonIndex.get().getPojo().getDisplay();
                if (displayId != null) {
                    definition = definitionsByDisplayId.get(displayId);
                }
            }
        } catch (Throwable ignored) {
            definition = null;
        }
        if (definition == null) {
            if (failedDefinitionLookups.size() > MAX_INSTANCES) {
                failedDefinitionLookups.clear();
            }
            failedDefinitionLookups.put(attachmentId, now);
            return null;
        }
        failedDefinitionLookups.remove(attachmentId);
        resolvedDefinitions.put(attachmentId, definition);
        return definition;
    }

    // ------------------------------------------------------------------
    // 输入
    // ------------------------------------------------------------------

    /**
     * 转发一次来自 TaCZ 枪械状态机的输入。只有本地玩家手持的枪械才会被转发。
     *
     * @param gunStack 触发输入的枪械 ItemStack（来自 GunAnimationStateContext）
     * @param input    TaCZ 的输入名，如 shoot / reload / cancel_reload / blot / fire_select / inspect /
     *                 bayonet_muzzle / bayonet_stock / bayonet_push / idle / run / walk / draw / put_away
     */
    public static void onGunInput(@Nullable ItemStack gunStack, @Nullable String input) {
        if (input == null || input.isEmpty()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (gunStack == null || gunStack.isEmpty() || !isLocalHeldStack(gunStack, player)) {
            // 上下文里的枪械可能还没刷新（例如刚切枪/第三人称），退化为“本地手持的枪械”全部转发。
            forwardToInstances(null, input, true);
            return;
        }
        forwardToInstances(gunIdOf(gunStack), input, false);
    }

    /** 客户端 tick 检测开镜开始/结束，向配件状态机发送 aim / aim_stop。 */
    public static void clientTick() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            lastAiming = false;
            return;
        }
        boolean aiming = isAimingNow(player);
        if (aiming == lastAiming) {
            return;
        }
        lastAiming = aiming;
        forwardToInstances(null, aiming ? INPUT_AIM : INPUT_AIM_STOP, true);
    }

    private static void forwardToInstances(@Nullable ResourceLocation gunId, String input, boolean localOnly) {
        if (INSTANCES.isEmpty()) {
            return;
        }
        List<AttachmentAnimationInstance> snapshot = new ArrayList<>(INSTANCES.values());
        for (AttachmentAnimationInstance instance : snapshot) {
            if (localOnly && !instance.matchesLocalGun(null)) {
                continue;
            }
            if (!localOnly && !instance.matchesLocalGun(gunId)) {
                continue;
            }
            instance.trigger(input);
            forwardAliases(instance, input);
        }
    }

    private static void forwardAliases(AttachmentAnimationInstance instance, String input) {
        if ("blot".equals(input)) {
            instance.trigger(INPUT_BOLT_ALIAS);
        } else if (input.startsWith("bayonet_")) {
            instance.trigger(INPUT_MELEE);
        }
    }

    // ------------------------------------------------------------------
    // 枪械渲染入口: camera / root / lefthand_pos / righthand_pos 功能组
    // ------------------------------------------------------------------

    /**
     * 枪械模型渲染前调用：统一推进匹配的配件状态机，并把配件动画里
     * {@code camera} / {@code root} / {@code lefthand_pos} / {@code righthand_pos}
     * 四个功能组的动画增量叠加到枪械模型的对应组上。
     */
    public static void beforeGunRender(BedrockGunModel model, ItemStack gunStack, ItemDisplayContext transformType) {
        if (GUN_RENDER_STACK.size() > 8) {
            // 极端情况下（渲染中途抛异常）防止栈不平衡
            GUN_RENDER_STACK.clear();
        }
        GUN_RENDER_STACK.push(null);
        GroupOverlay overlay = null;
        try {
            overlay = applyGroupOverlay(model, gunStack, transformType);
        } catch (Throwable ignored) {
        }
        if (overlay != null) {
            GUN_RENDER_STACK.pop();
            GUN_RENDER_STACK.push(overlay);
        }
    }

    /** 枪械模型渲染结束后调用：还原功能组并清理共享模型上的残留变换。 */
    public static void afterGunRender() {
        Object entry = GUN_RENDER_STACK.poll();
        if (!(entry instanceof GroupOverlay overlay)) {
            return;
        }
        overlay.revert();
        for (AttachmentAnimationInstance instance : overlay.instances) {
            instance.markUpdatedByGunRender(false);
            instance.cleanModelTransforms();
        }
    }

    /** 配件动画 camera 组的当前旋转（叠加给第一人称手部/视角用）；没有动画时返回 null。 */
    @Nullable
    public static Quaternionf cameraRotation(ItemStack gunStack) {
        if (INSTANCES.isEmpty() || gunStack == null || gunStack.isEmpty()) {
            return null;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !isLocalHeldStack(gunStack, player)) {
            return null;
        }
        ResourceLocation gunId = gunIdOf(gunStack);
        boolean offhand = isOffhandGun(gunStack, player);
        Quaternionf total = null;
        for (AttachmentAnimationInstance instance : INSTANCES.values()) {
            AttachmentAnimationKey key = instance.key();
            if (instance.isBroken() || !instance.cameraFresh() || key.offhand() != offhand
                    || !key.firstPerson() || key.gunItem() != gunStack.getItem()) {
                continue;
            }
            if (!instance.matchesLocalGun(gunId)) {
                continue;
            }
            Quaternionf rotation = instance.transforms().cameraRotation();
            if (rotation == null) {
                continue;
            }
            total = total == null ? rotation : total.mul(rotation);
        }
        return total;
    }

    @Nullable
    private static GroupOverlay applyGroupOverlay(BedrockGunModel model, ItemStack gunStack,
                                                  ItemDisplayContext transformType) {
        if (model == null || gunStack == null || gunStack.isEmpty() || transformType == null) {
            return null;
        }
        boolean firstPerson = transformType.firstPerson();
        boolean thirdPerson = transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        if (!firstPerson && !thirdPerson) {
            return null;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !isLocalHeldStack(gunStack, player)) {
            return null;
        }
        ResourceLocation gunId = gunIdOf(gunStack);
        boolean offhand = isOffhandGun(gunStack, player);
        List<AttachmentAnimationInstance> matched = new ArrayList<>();
        for (AttachmentAnimationInstance instance : INSTANCES.values()) {
            AttachmentAnimationKey key = instance.key();
            if (instance.isBroken() || !instance.wasRenderedRecently()
                    || key.firstPerson() != firstPerson || key.offhand() != offhand
                    || key.gunItem() != gunStack.getItem()) {
                continue;
            }
            if (!instance.matchesLocalGun(gunId)) {
                continue;
            }
            matched.add(instance);
        }
        if (matched.isEmpty()) {
            return null;
        }

        float partialTicks = Minecraft.getInstance().getFrameTime();
        AttachmentGroupTransforms.Group root = AttachmentGroupTransforms.Group.IDENTITY;
        AttachmentGroupTransforms.Group leftHand = AttachmentGroupTransforms.Group.IDENTITY;
        AttachmentGroupTransforms.Group rightHand = AttachmentGroupTransforms.Group.IDENTITY;
        for (AttachmentAnimationInstance instance : matched) {
            instance.update(instance.attachmentItem(), gunStack, firstPerson, partialTicks);
            instance.markUpdatedByGunRender(true);
            instance.markCameraUpdated();
            root = root.combine(instance.transforms().root());
            leftHand = leftHand.combine(instance.transforms().leftHand());
            rightHand = rightHand.combine(instance.transforms().rightHand());
        }
        NodeSnapshot rootSnapshot = NodeSnapshot.apply(model.getNode(AttachmentGroupTransforms.ROOT), root);
        NodeSnapshot leftSnapshot = NodeSnapshot.apply(model.getNode(AttachmentGroupTransforms.LEFT_HAND), leftHand);
        NodeSnapshot rightSnapshot = NodeSnapshot.apply(model.getNode(AttachmentGroupTransforms.RIGHT_HAND), rightHand);
        return new GroupOverlay(rootSnapshot, leftSnapshot, rightSnapshot, matched);
    }

    private static final class GroupOverlay {
        private final NodeSnapshot root;
        private final NodeSnapshot leftHand;
        private final NodeSnapshot rightHand;
        private final List<AttachmentAnimationInstance> instances;

        private GroupOverlay(NodeSnapshot root, NodeSnapshot leftHand, NodeSnapshot rightHand,
                             List<AttachmentAnimationInstance> instances) {
            this.root = root;
            this.leftHand = leftHand;
            this.rightHand = rightHand;
            this.instances = instances;
        }

        private void revert() {
            if (root != null) {
                root.revert();
            }
            if (leftHand != null) {
                leftHand.revert();
            }
            if (rightHand != null) {
                rightHand.revert();
            }
        }
    }

    /** 记录一个骨骼叠加前的变换，渲染结束后精确还原。 */
    private static final class NodeSnapshot {
        private final BedrockPart part;
        private final float offsetX;
        private final float offsetY;
        private final float offsetZ;
        private final Quaternionf rotation;
        private final float scaleX;
        private final float scaleY;
        private final float scaleZ;

        private NodeSnapshot(BedrockPart part, float offsetX, float offsetY, float offsetZ,
                             Quaternionf rotation, float scaleX, float scaleY, float scaleZ) {
            this.part = part;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.rotation = rotation;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.scaleZ = scaleZ;
        }

        @Nullable
        private static NodeSnapshot apply(@Nullable BedrockPart part, AttachmentGroupTransforms.Group group) {
            if (part == null) {
                return null;
            }
            NodeSnapshot snapshot = new NodeSnapshot(part, part.offsetX, part.offsetY, part.offsetZ,
                    new Quaternionf(part.additionalQuaternion), part.xScale, part.yScale, part.zScale);
            if (group != null) {
                group.applyTo(part);
            }
            return snapshot;
        }

        private void revert() {
            if (part == null) {
                return;
            }
            part.offsetX = offsetX;
            part.offsetY = offsetY;
            part.offsetZ = offsetZ;
            part.additionalQuaternion.set(rotation);
            part.xScale = scaleX;
            part.yScale = scaleY;
            part.zScale = scaleZ;
        }
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

    /**
     * 配件渲染前调用：更新状态机并把动画写入模型。
     * 只在第一/第三人称、且枪械由本地玩家手持时生效。
     */
    public static void beforeRender(ItemStack attachmentItem, ItemStack gunItem, ItemDisplayContext transformType) {
        if (RENDER_STACK.size() > 8) {
            // 极端情况下（渲染中途抛异常）防止栈不平衡
            RENDER_STACK.clear();
        }
        RENDER_STACK.push(null);
        if (transformType == null) {
            return;
        }
        boolean firstPerson = transformType.firstPerson();
        boolean thirdPerson = transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        if (!firstPerson && !thirdPerson) {
            // GUI/展示框/掉落物等场景保持静态，避免预览界面里动画状态被意外推进。
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !isLocalHeldStack(gunItem, player)) {
            return;
        }
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        if (iAttachment == null) {
            return;
        }
        ResourceLocation attachmentId = iAttachment.getAttachmentId(attachmentItem);
        if (attachmentId == null) {
            return;
        }
        AttachmentAnimationDef definition = resolveDefinition(attachmentId);
        if (definition == null) {
            return;
        }
        // 替换型配件皮肤使用独立模型，暂不参与动画，保持静态渲染。
        try {
            Optional<ClientAttachmentVariantIndex> variant = TimelessAPI.getClientAttachmentVariantIndex(attachmentItem);
            if (variant.isPresent() && variant.get().isReplaceMode()
                    && variant.get().getAttachmentModel() != null) {
                return;
            }
        } catch (Throwable ignored) {
        }
        ClientAttachmentIndex index = TimelessAPI.getClientAttachmentIndex(attachmentId).orElse(null);
        if (index == null) {
            return;
        }
        BedrockAttachmentModel model = index.getAttachmentModel();
        if (model == null) {
            return;
        }
        AttachmentAnimationKey key = new AttachmentAnimationKey(attachmentId, gunItem.getItem(), gunItem,
                firstPerson, isOffhandGun(gunItem, player));
        AttachmentAnimationInstance instance = INSTANCES.get(key);
        if (instance == null) {
            long now = System.currentTimeMillis();
            Long failedAt = failedInstances.get(key);
            if (failedAt != null && now - failedAt < CREATE_RETRY_INTERVAL_MS) {
                return;
            }
            instance = AttachmentAnimationInstance.create(key, definition, attachmentItem, gunItem, model);
            if (instance == null) {
                if (failedInstances.size() > MAX_INSTANCES) {
                    failedInstances.clear();
                }
                failedInstances.put(key, now);
                return;
            }
            failedInstances.remove(key);
            INSTANCES.put(key, instance);
            if (isAimingNow(player)) {
                instance.trigger(INPUT_AIM);
            }
        }
        instance.markRendered();
        if (!instance.wasUpdatedByGunRender()) {
            // 没走到枪械渲染入口（老路径/其它调用方）时兜底更新；正常情况已在 beforeGunRender 里推进过。
            instance.update(attachmentItem, gunItem, firstPerson, Minecraft.getInstance().getFrameTime());
        }
        if (instance.isBroken()) {
            INSTANCES.remove(key);
            instance.exit();
            if (failedInstances.size() > MAX_INSTANCES) {
                failedInstances.clear();
            }
            failedInstances.put(key, System.currentTimeMillis());
            RENDER_STACK.pop();
            RENDER_STACK.push(model);
            return;
        }
        RENDER_STACK.pop();
        RENDER_STACK.push(model);
    }

    /** 配件渲染结束后清理动画变换，防止共享模型串扰。 */
    public static void afterRender() {
        BedrockAttachmentModel model = RENDER_STACK.poll();
        if (model != null) {
            try {
                model.cleanAnimationTransform();
            } catch (Throwable ignored) {
            }
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    public static boolean isLocalHeldStack(@Nullable ItemStack stack, LocalPlayer player) {
        if (stack == null || stack.isEmpty() || player == null) {
            return false;
        }
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        if (stack == mainHand || stack == offHand) {
            return true;
        }
        // 枪械 NBT 会随开火/换弹变化，不能按完整内容比较；否则开火当帧会误判“非本地手持”，
        // 跳过动画更新，模型在该帧回到基础姿态（表现为闪回 idle）。这里按枪械 id 比较。
        ResourceLocation gunId = gunIdOf(stack);
        if (gunId != null) {
            return gunId.equals(gunIdOf(mainHand)) || gunId.equals(gunIdOf(offHand));
        }
        return ItemStack.isSameItemSameTags(stack, mainHand) || ItemStack.isSameItemSameTags(stack, offHand);
    }

    /** 该枪械当前是否由副手持握（主手优先；用于区分双持两手）。 */
    public static boolean isOffhandGun(ItemStack gunItem, LocalPlayer player) {
        if (gunItem == null || gunItem.isEmpty() || player == null) {
            return false;
        }
        if (gunItem.getItem() == player.getMainHandItem().getItem()) {
            return false;
        }
        return gunItem.getItem() == player.getOffhandItem().getItem();
    }
    private static boolean isAimingNow(LocalPlayer player) {
        try {
            return IClientPlayerGunOperator.fromLocalPlayer(player).isAim();
        } catch (Throwable throwable) {
            return false;
        }
    }

    @Nullable
    public static ResourceLocation gunIdOf(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        IGun iGun = IGun.getIGunOrNull(stack);
        return iGun == null ? null : iGun.getGunId(stack);
    }
}
