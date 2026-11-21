package com.ssscript.taczfixes.client.attachment;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.api.util.LuaNbtAccessor;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.animation.statemachine.ItemAnimationStateContext;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.ssscript.taczfixes.client.handler.SteplessZoomHandler;
import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.ssscript.taczfixes.common.util.ItemNbt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luaj.vm2.LuaTable;

/**
 * 配件动画状态机上下文。
 *
 * <p>TaCZ 已经有的上下文数据一律复用，本类不再重复实现：
 * <ul>
 *   <li>基类用 TaCZ 的 {@link ItemAnimationStateContext}（partialTicks / putAwayTime）；</li>
 *   <li>枪械与玩家状态（弹药、瞄准、换弹、过热、蓄力、输入、移动距离、NBT 等）
 *       全部委托给一个 TaCZ 的 {@link GunAnimationStateContext}，由 {@link #setRenderData}
 *       在每次渲染前写入当前枪械。</li>
 * </ul>
 * 本类只补 TaCZ 上下文里没有的配件层数据：当前配件 id / ItemStack、配件 display 的
 * {@code taczfixes.state_machine_param}、第一/第三人称标志，以及 TaCZ 没有的
 * {@code isReloading} / {@code isSprinting} / {@code isWalking} / {@code isMoving} / {@code isProne}。
 *
 * <p>注意这里用组合而不是直接继承 {@code GunAnimationStateContext}：不少模组（含 Arcana）用
 * {@code context instanceof GunAnimationStateContext} 判断"这是枪的动画"，配件上下文如果伪装成枪上下文，
 * 会被它们按枪动画处理（改动画名、改播放速率、读枪 NBT），而且本模组转发枪械输入时也会递归触发。
 */
@SuppressWarnings("unused")
public class AttachmentAnimationStateContext extends ItemAnimationStateContext {
    private final ResourceLocation attachmentId;
    private final LuaTable stateMachineParams;
    /** TaCZ 的枪械上下文，只用来复用其数据查询实现。 */
    private final GunAnimationStateContext gunContext = new GunAnimationStateContext();
    private ItemStack attachmentItem = ItemStack.EMPTY;
    private ItemStack gunItem = ItemStack.EMPTY;
    private boolean firstPerson;

    public AttachmentAnimationStateContext(ResourceLocation attachmentId, LuaTable stateMachineParams) {
        this.attachmentId = attachmentId;
        this.stateMachineParams = stateMachineParams == null ? new LuaTable() : stateMachineParams;
    }

    /**
     * 状态机脚本不要调用此方法。每次渲染前由本模组写入当前渲染的配件/枪械。
     */
    public void setRenderData(ItemStack attachmentItem, ItemStack gunItem, boolean firstPerson, float partialTicks) {
        this.attachmentItem = attachmentItem == null ? ItemStack.EMPTY : attachmentItem;
        this.gunItem = gunItem == null ? ItemStack.EMPTY : gunItem;
        this.firstPerson = firstPerson;
        setPartialTicks(partialTicks);
        gunContext.setPartialTicks(partialTicks);
        gunContext.setCurrentGunItem(this.gunItem);
    }

    /** TaCZ 的部分查询对"没有枪"不做空判断, 这里统一兜底, 避免脚本异常导致该配件状态机被停用。 */
    private boolean hasGun() {
        return !gunItem.isEmpty() && com.tacz.guns.api.item.IGun.getIGunOrNull(gunItem) != null;
    }

    // ---------------- 配件层新增（TaCZ 上下文没有的部分） ----------------

    /** 配件注册名（如 {@code mypack:my_scope}）。 */
    public String getAttachmentId() {
        return attachmentId == null ? "tacz:empty" : attachmentId.toString();
    }

    /** 当前配件的 ItemStack，可继续调用原版/ TaCZ 的物品方法。 */
    public ItemStack getAttachmentItem() {
        return attachmentItem;
    }

    /** 当前枪械的 ItemStack。 */
    public ItemStack getGunItem() {
        return gunItem;
    }

    /** 本地玩家（射手）。 */
    @Nullable
    public LocalPlayer getShooter() {
        return Minecraft.getInstance().player;
    }

    /** 当前是否第一人称渲染。 */
    public boolean isFirstPerson() {
        return firstPerson;
    }

    /** display JSON 的 {@code taczfixes.state_machine_param}（覆盖枪械自己的状态机参数）。 */
    public LuaTable getStateMachineParams() {
        return stateMachineParams;
    }

    /** 是否正在换弹。TaCZ 上下文只暴露 getReloadStateType()，这里补一个布尔便捷方法。 */
    public boolean isReloading() {
        try {
            ReloadState.StateType[] types = ReloadState.StateType.values();
            int ordinal = getReloadStateType();
            return ordinal >= 0 && ordinal < types.length && types[ordinal].isReloading();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 玩家是否正在疾跑。 */
    public boolean isSprinting() {
        LocalPlayer player = getShooter();
        return player != null && player.isSprinting();
    }

    /** 玩家是否正在行走（有移动输入且未疾跑）。 */
    public boolean isWalking() {
        LocalPlayer player = getShooter();
        if (player == null || player.input == null || player.isSprinting()) {
            return false;
        }
        return player.input.getMoveVector().length() > 0.01f;
    }

    /** 玩家是否正在移动（任意方向）。 */
    public boolean isMoving() {
        LocalPlayer player = getShooter();
        return player != null && player.input != null && player.input.getMoveVector().length() > 0.01f;
    }

    /**
     * 玩家是否处于趴下状态：TaCZ 匍匐标志，或玩家实际使用 SWIMMING 姿势
     * （TaCZ 匍匐会强制该姿势，兼容其它 mod 的爬行）。
     */
    public boolean isProne() {
        LocalPlayer player = getShooter();
        if (player == null) {
            return false;
        }
        // ParCool 爬行：TaCZ 的匍匐标志由兼容 mixin 同步，可能随原版 tickCrawl 反复，
        // 这里直接读取 ParCool 的爬行动作状态，避免趴下状态逐帧抖动。
        if (com.ssscript.taczfixes.common.util.ParCoolHelper.isCrawling(player)) {
            return true;
        }
        if (player.getPose() == net.minecraft.world.entity.Pose.SWIMMING) {
            return true;
        }
        return isCrawl();
    }

    // ---------------- 以下全部复用 TaCZ GunAnimationStateContext 的实现 ----------------

    /** 枪膛内是否有子弹。 */
    public boolean hasBulletInBarrel() {
        return gunContext.hasBulletInBarrel();
    }

    /** 是否处于过热锁定状态。 */
    public boolean isOverHeat() {
        return hasGun() && gunContext.isOverHeat();
    }

    /** 过热进度 0~1。 */
    public float getHeatProgress() {
        return hasGun() ? gunContext.getHeatProgress() : 0.0F;
    }

    /** 射击间隔（毫秒）。 */
    public long getShootInterval() {
        return hasGun() ? gunContext.getShootInterval() : 0L;
    }

    /** 上次射击的时间戳（毫秒），未射击过为 -1。 */
    public long getLastShootTimestamp() {
        return gunContext.getLastShootTimestamp();
    }

    /** 当前时间戳（毫秒，TaCZ 的 GunTime 时间轴）。 */
    public long getCurrentTimestamp() {
        return gunContext.getCurrentTimestamp();
    }

    /** 真实系统时间（毫秒，不受游戏暂停影响）。 */
    public long getRealSystemTimestamp() {
        return gunContext.getRealSystemTimestamp();
    }

    /** 调整客户端射击间隔（TaCZ 原实现）。 */
    public void adjustClientShootInterval(long alpha) {
        gunContext.adjustClientShootInterval(alpha);
    }

    /** 当前弹匣内子弹数。 */
    public int getAmmoCount() {
        return gunContext.getAmmoCount();
    }

    /** 当前弹匣容量（含扩容弹匣）。 */
    public int getMaxAmmoCount() {
        return gunContext.getMaxAmmoCount();
    }

    /** 是否还有可消耗的弹药（弹匣/备弹/虚拟弹药）。 */
    public boolean hasAmmoToConsume() {
        return hasGun() && gunContext.hasAmmoToConsume();
    }

    /** 扩容弹匣等级。 */
    public int getMagExtentLevel() {
        return gunContext.getMagExtentLevel();
    }

    /** 开火模式 {@code FireMode} 的 ordinal。 */
    public int getFireMode() {
        return gunContext.getFireMode();
    }

    /** 开镜进度 0~1。 */
    public float getAimingProgress() {
        return gunContext.getAimingProgress();
    }

    /** 是否正在开镜/瞄准。 */
    public boolean isAiming() {
        return gunContext.isAiming();
    }

    /** 客户端射击冷却（毫秒）。 */
    public long getShootCoolDown() {
        return gunContext.getShootCoolDown();
    }

    /** 换弹状态 {@link ReloadState.StateType} 的 ordinal。 */
    public int getReloadStateType() {
        return gunContext.getReloadStateType();
    }

    public boolean isInputUp() {
        return gunContext.isInputUp();
    }

    public boolean isInputDown() {
        return gunContext.isInputDown();
    }

    public boolean isInputLeft() {
        return gunContext.isInputLeft();
    }

    public boolean isInputRight() {
        return gunContext.isInputRight();
    }

    public boolean isInputJumping() {
        return gunContext.isInputJumping();
    }

    /** 是否匍匐（TaCZ 匍匐标志）。 */
    public boolean isCrawl() {
        return gunContext.isCrawl();
    }

    /** 是否在地面（TaCZ 按相机实体判断）。 */
    public boolean isOnGround() {
        return gunContext.isOnGround();
    }

    /** 是否潜行（TaCZ 按相机实体判断）。 */
    public boolean isCrouching() {
        return gunContext.isCrouching();
    }

    public boolean shouldSlide() {
        return hasGun() && gunContext.shouldSlide();
    }

    public void anchorWalkDist() {
        gunContext.anchorWalkDist();
    }

    public float getWalkDist() {
        return gunContext.getWalkDist();
    }

    /** 抛出弹壳（枪械弹壳渲染；配件脚本正常不会调用）。 */
    public void popShellFrom(int index) {
        if (!hasGun()) {
            return;
        }
        try {
            gunContext.popShellFrom(index);
        } catch (Throwable ignored) {
        }
    }

    /** 枪械 NBT 访问器。 */
    public LuaNbtAccessor getNbtAccessor() {
        return gunContext.getNbtAccessor();
    }

    /** 当前枪械某个槽位的配件 id 字符串。 */
    public String getAttachment(String type) {
        return hasGun() ? gunContext.getAttachment(type) : DefaultAssets.EMPTY_ATTACHMENT_ID.toString();
    }

    /** 蓄力进度 0~1。 */
    public float getChargeProgress() {
        return gunContext.getChargeProgress();
    }

    public float getMaxCharge() {
        return gunContext.getMaxCharge();
    }

    public float getChargeThreshold() {
        return gunContext.getChargeThreshold();
    }

    /** 是否正在蓄力。 */
    public boolean isCharging() {
        return gunContext.isCharging();
    }

    // ---------------- 瞄具相关(TaCZ 上下文没有的部分) ----------------

    /**
     * 本配件是否是玩家当前正在使用的那个瞄具。
     *
     * <p>本模组支持多个瞄具槽: 玩家用切换键选中的那个瞄具才算激活; 没有切换过时,
     * 原生 SCOPE 槽的瞄具在枪上没有其它瞄具的情况下视为激活。</p>
     */
    public boolean isActiveScope() {
        ResourceLocation id = attachmentId;
        if (id == null || DefaultAssets.isEmptyAttachmentId(id)) {
            return false;
        }
        ClientAttachmentIndex index = TimelessAPI.getClientAttachmentIndex(id).orElse(null);
        if (index == null || (!index.isScope() && !index.isSight())) {
            return false;
        }
        if (!hasGun()) {
            return false;
        }
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null) {
            return false;
        }
        // 已经切到自定义槽的某个瞄具: 只有那一个算激活。
        if (ScopeSwitchState.getActiveSlot(gunItem) != null) {
            return id.equals(ScopeSwitchState.activeCustomOpticId(gunItem));
        }
        // 没有切换过: 原生 SCOPE 槽的瞄具是当前瞄具(枪上没有其它瞄具时同样成立)。
        ResourceLocation nativeScope = gun.getAttachmentId(gunItem, AttachmentType.SCOPE);
        if (nativeScope == null || DefaultAssets.isEmptyAttachmentId(nativeScope)) {
            nativeScope = gun.getBuiltInAttachmentId(gunItem, AttachmentType.SCOPE);
        }
        return id.equals(nativeScope) && !ScopeSwitchState.hasOtherOptics(gunItem, id);
    }

    /**
     * 本配件的当前放大倍率(不是瞄具时返回 1.0)。
     *
     * <p>按配件自己的倍率档位(切换倍率键改的那个值)计算; 若本瞄具正被使用且开了本模组的
     * 无极变倍, 则返回无极变倍的实时倍率。</p>
     */
    public float getMagnification() {
        ResourceLocation id = attachmentId;
        if (id == null || DefaultAssets.isEmptyAttachmentId(id)) {
            return 1.0F;
        }
        ClientAttachmentIndex index = TimelessAPI.getClientAttachmentIndex(id).orElse(null);
        if (index == null || (!index.isScope() && !index.isSight())) {
            return 1.0F;
        }
        // 无极变倍只作用于当前使用的瞄具: 直接取相机实际使用的倍率。
        if (hasGun() && isActiveScope()) {
            float stepless = SteplessZoomHandler.getSteplessZoom(gunItem);
            if (stepless > 0.0F) {
                return stepless;
            }
        }
        float[] zooms = index.getZoom();
        if (zooms == null || zooms.length == 0) {
            return 1.0F;
        }
        int zoomNumber = AttachmentItemDataAccessor.getZoomNumberFromTag(ItemNbt.get(attachmentItem));
        return zooms[Math.floorMod(zoomNumber, zooms.length)];
    }
}
