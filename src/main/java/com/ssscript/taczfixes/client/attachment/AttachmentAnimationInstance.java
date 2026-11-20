package com.ssscript.taczfixes.client.attachment;

import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.Animations;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.client.animation.statemachine.LuaStateMachineFactory;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.animation.bedrock.BedrockAnimationFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.luaj.vm2.LuaTable;

/**
 * 一个配件槽位/视角实例的独立动画状态机。
 *
 * <p>每个实例拥有独立的 {@link AnimationController} 与 {@link LuaAnimationStateMachine}，
 * 但共用 {@code ClientAttachmentIndex} 中的模型（模型是全局缓存的）。
 * 因此动画变换只在本次渲染内生效，渲染结束后由
 * {@link AttachmentAnimationManager#afterRender()} 清理，避免串扰。
 */
public final class AttachmentAnimationInstance {
    private final AttachmentAnimationKey key;
    private final AttachmentAnimationDef def;
    private final BedrockAttachmentModel model;
    private final LuaAnimationStateMachine<AttachmentAnimationStateContext> stateMachine;
    private final AttachmentAnimationStateContext context;
    /** camera / root / lefthand_pos / righthand_pos 四个功能组的最近一帧动画增量。 */
    private final AttachmentGroupTransforms transforms = AttachmentGroupTransforms.create();
    private ItemStack attachmentItem = ItemStack.EMPTY;
    /** 本次枪械渲染入口是否已经推进过状态机（避免配件渲染时再推进一次）。 */
    private boolean updatedByGunRender;
    /** 最近一次真正被渲染的时间（纳秒），用于避免给已卸下的旧配件继续跑叠加动画。 */
    private long lastRenderedNanos;
    /** 最近一次由枪械渲染入口采集 camera 组的时间（纳秒），用于避免停止渲染后镜头残留倾斜。 */
    private long lastCameraNanos;
    private boolean broken;

    private AttachmentAnimationInstance(AttachmentAnimationKey key, AttachmentAnimationDef def,
                                        BedrockAttachmentModel model,
                                        LuaAnimationStateMachine<AttachmentAnimationStateContext> stateMachine,
                                        AttachmentAnimationStateContext context) {
        this.key = key;
        this.def = def;
        this.model = model;
        this.stateMachine = stateMachine;
        this.context = context;
    }

    /**
     * 构建状态机实例。动画文件或脚本缺失、脚本执行失败时返回 null，由调用方静默回退到静态渲染。
     */
    static AttachmentAnimationInstance create(AttachmentAnimationKey key, AttachmentAnimationDef def,
                                              ItemStack attachmentItem, ItemStack gunItem,
                                              BedrockAttachmentModel model) {
        try {
            AttachmentGroupTransforms.ensureNodes(model);
            BedrockAnimationFile animationFile = ClientAssetsManager.INSTANCE.getBedrockAnimations(def.animationId());
            if (animationFile == null) {
                return null;
            }
            LuaTable script = ClientAssetsManager.INSTANCE.getScript(def.stateMachineId());
            if (script == null) {
                return null;
            }
            AnimationController controller = Animations.createControllerFromBedrock(animationFile, model);
            LuaAnimationStateMachine<AttachmentAnimationStateContext> stateMachine =
                    new LuaStateMachineFactory<AttachmentAnimationStateContext>()
                            .setController(controller)
                            .setLuaScripts(script)
                            .build();
            AttachmentAnimationStateContext context =
                    new AttachmentAnimationStateContext(def.displayId(), def.createParamTable());
            stateMachine.setContext(context);
            context.setRenderData(attachmentItem, gunItem, key.firstPerson(), 0f);
            stateMachine.initialize();
            return new AttachmentAnimationInstance(key, def, model, stateMachine, context);
        } catch (Throwable throwable) {
            // 静默回退：缺少文件、脚本错误等都不应影响原有静态配件渲染。
            return null;
        }
    }

    /** 渲染前更新上下文并推进状态机（动画变换写入模型）。 */
    void update(ItemStack attachmentItem, ItemStack gunItem, boolean firstPerson, float partialTicks) {
        if (broken) {
            return;
        }
        this.attachmentItem = attachmentItem == null ? ItemStack.EMPTY : attachmentItem;
        context.setRenderData(attachmentItem, gunItem, firstPerson, partialTicks);
        try {
            if (stateMachine.isInitialized()) {
                stateMachine.update();
            }
        } catch (Throwable throwable) {
            // 脚本运行时报错：停用本实例，回退到静态渲染，不影响其它配件。
            broken = true;
            return;
        }
        // 状态机跑完后把 camera/root/lefthand_pos/righthand_pos 的动画增量取出来,
        // 由 AttachmentAnimationManager 叠加到枪械模型上。
        transforms.capture(model);
    }

    AttachmentGroupTransforms transforms() {
        return transforms;
    }

    ItemStack attachmentItem() {
        return attachmentItem;
    }

    void markUpdatedByGunRender(boolean updated) {
        this.updatedByGunRender = updated;
    }

    void markRendered() {
        this.lastRenderedNanos = System.nanoTime();
    }

    /** 最近 1 秒内是否被真正渲染过（没被渲染的实例不参与枪械功能组叠加）。 */
    boolean wasRenderedRecently() {
        return lastRenderedNanos != 0L && System.nanoTime() - lastRenderedNanos <= 1_000_000_000L;
    }

    /** 记录一次由枪械渲染入口采集 camera 组的时间。 */
    void markCameraUpdated() {
        this.lastCameraNanos = System.nanoTime();
    }

    /** 最近是否由枪械渲染入口采集过 camera 组（停止渲染后不把最后一帧的镜头旋转继续挂着）。 */
    boolean cameraFresh() {
        return lastCameraNanos != 0L && System.nanoTime() - lastCameraNanos <= 200_000_000L;
    }

    boolean wasUpdatedByGunRender() {
        return updatedByGunRender;
    }

    /** 清理共享模型上的残留变换（例如配件本帧没被渲染时）。 */
    void cleanModelTransforms() {
        AttachmentGroupTransforms.clean(model);
    }

    /** 转发一次状态机输入。 */
    void trigger(String input) {
        if (broken || input == null || input.isEmpty() || !stateMachine.isInitialized()) {
            return;
        }
        try {
            stateMachine.trigger(input);
        } catch (Throwable throwable) {
            broken = true;
        }
    }

    boolean isBroken() {
        return broken;
    }

    /** 资源重载/实例淘汰时退出状态机（触发 Lua 的 exit）。 */
    void exit() {
        try {
            if (stateMachine.isInitialized()) {
                stateMachine.exit();
            }
        } catch (Throwable ignored) {
        }
    }

    AttachmentAnimationKey key() {
        return key;
    }

    BedrockAttachmentModel model() {
        return model;
    }

    AttachmentAnimationDef def() {
        return def;
    }

    /** 该实例绑定的枪械是否仍由本地玩家手持（避免为其他玩家的枪械运行状态机）。 */
    boolean matchesLocalGun(@org.jetbrains.annotations.Nullable ResourceLocation gunId) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        ItemStack boundGun = key.gunStack();
        if (!AttachmentAnimationManager.isLocalHeldStack(boundGun, player)) {
            return false;
        }
        if (gunId == null) {
            return true;
        }
        ResourceLocation boundGunId = AttachmentAnimationManager.gunIdOf(boundGun);
        return gunId.equals(boundGunId);
    }
}
