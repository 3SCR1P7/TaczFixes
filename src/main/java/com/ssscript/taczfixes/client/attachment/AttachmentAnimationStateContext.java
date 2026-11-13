package com.ssscript.taczfixes.client.attachment;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateContext;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunHeatData;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luaj.vm2.LuaTable;

/**
 * 配件动画状态机上下文。每个配件槽位/视角实例都有独立的上下文与状态机。
 *
 * <p>Lua 侧通过 {@code context:方法名()} 调用下列方法。所有方法在配件/枪械缺失时都会返回
 * 安全的默认值，不会抛异常。
 */
@SuppressWarnings("unused")
public class AttachmentAnimationStateContext extends AnimationStateContext {
    private final ResourceLocation attachmentId;
    private final LuaTable stateMachineParams;
    private ItemStack attachmentItem = ItemStack.EMPTY;
    private ItemStack gunItem = ItemStack.EMPTY;
    private @Nullable IGun iGun;
    private @Nullable ResourceLocation gunId;
    private @Nullable GunData gunData;
    private boolean firstPerson;
    private float partialTicks;

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
        this.partialTicks = partialTicks;
        this.iGun = IGun.getIGunOrNull(this.gunItem);
        if (this.iGun != null) {
            this.gunId = this.iGun.getGunId(this.gunItem);
            this.gunData = this.gunId == null ? null
                    : TimelessAPI.getClientGunIndex(this.gunId).map(ClientGunIndex::getGunData).orElse(null);
        } else {
            this.gunId = null;
            this.gunData = null;
        }
    }

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

    /** 当前渲染帧的 partialTicks。 */
    public float getPartialTicks() {
        return partialTicks;
    }

    /** display JSON 的 {@code taczfixes.state_machine_param}。 */
    public LuaTable getStateMachineParams() {
        return stateMachineParams;
    }

    /** 是否正在开镜/瞄准。 */
    public boolean isAiming() {
        return processOperator(IClientPlayerGunOperator::isAim).orElse(false);
    }

    /** 开镜进度 0~1。 */
    public float getAimingProgress() {
        return processOperator(operator -> operator.getClientAimingProgress(partialTicks)).orElse(0f);
    }

    /** 是否正在换弹。 */
    public boolean isReloading() {
        return getReloadStateTypeValue().isReloading();
    }

    /** 换弹状态 {@link ReloadState.StateType} 的 ordinal。 */
    public int getReloadStateType() {
        return getReloadStateTypeValue().ordinal();
    }

    /** 当前弹匣内子弹数。 */
    public int getAmmoCount() {
        return iGun == null ? 0 : Math.max(iGun.getCurrentAmmoCount(gunItem), 0);
    }

    /** 当前弹匣容量（含扩容弹匣）。 */
    public int getMaxAmmoCount() {
        if (gunData == null) {
            return 0;
        }
        return Math.max(AttachmentDataUtils.getAmmoCountWithAttachment(gunItem, gunData), 0);
    }

    /** 枪膛内是否有子弹。 */
    public boolean hasBulletInBarrel() {
        return iGun != null && iGun.hasBulletInBarrel(gunItem);
    }

    /** 开火模式 {@code FireMode} 的 ordinal。 */
    public int getFireMode() {
        return iGun == null ? 0 : iGun.getFireMode(gunItem).ordinal();
    }

    /** 是否处于过热锁定状态。 */
    public boolean isOverHeat() {
        return iGun != null && iGun.hasHeatData(gunItem) && iGun.isOverheatLocked(gunItem);
    }

    /** 过热进度 0~1。 */
    public float getHeatProgress() {
        if (iGun == null || gunData == null) {
            return 0f;
        }
        GunHeatData heatData = gunData.getHeatData();
        if (heatData == null || heatData.getHeatMax() <= 0f) {
            return 0f;
        }
        return Mth.clamp(iGun.getHeatAmount(gunItem) / heatData.getHeatMax(), 0f, 1f);
    }

    /** 客户端射击冷却（毫秒）。 */
    public long getShootCoolDown() {
        return processOperator(IClientPlayerGunOperator::getClientShootCoolDown).orElse(0L);
    }

    /** 上次射击的时间戳（毫秒），未射击过为 -1。 */
    public long getLastShootTimestamp() {
        return processOperator(operator -> operator.getDataHolder().clientLastShootTimestamp).orElse(-1L);
    }

    /** 当前系统时间（毫秒）。 */
    public long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }

    /** 蓄力进度 0~1。 */
    public float getChargeProgress() {
        return processOperator(IClientPlayerGunOperator::getChargeProgress).orElse(0f);
    }

    /** 是否正在蓄力。 */
    public boolean isCharging() {
        return processOperator(IClientPlayerGunOperator::isCharging).orElse(false);
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

    /** 玩家是否潜行。 */
    public boolean isCrouching() {
        LocalPlayer player = getShooter();
        return player != null && player.isCrouching();
    }

    /** 玩家是否在地面。 */
    public boolean isOnGround() {
        LocalPlayer player = getShooter();
        return player != null && player.onGround();
    }

    /** 玩家是否匍匐。 */
    public boolean isCrawl() {
        return processOperator(IClientPlayerGunOperator::isCrawl).orElse(false);
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
        // ParCool 爬行：TaCZ 匍匐标志由兼容 mixin 同步，可能随原版 tickCrawl 反复，
        // 这里直接读取 ParCool 爬行动作状态，避免趴下状态逐帧抖动。
        if (com.ssscript.taczfixes.common.util.ParCoolHelper.isCrawling(player)) {
            return true;
        }
        if (player.getPose() == net.minecraft.world.entity.Pose.SWIMMING) {
            return true;
        }
        return isCrawl();
    }

    /** 玩家是否正在移动（任意方向）。 */
    public boolean isMoving() {
        LocalPlayer player = getShooter();
        return player != null && player.input != null && player.input.getMoveVector().length() > 0.01f;
    }

    private ReloadState.StateType getReloadStateTypeValue() {
        LocalPlayer player = getShooter();
        if (player != null) {
            try {
                return IGunOperator.fromLivingEntity(player).getSynReloadState().getStateType();
            } catch (Throwable ignored) {
            }
        }
        return ReloadState.StateType.NOT_RELOADING;
    }

    private <T> java.util.Optional<T> processOperator(java.util.function.Function<IClientPlayerGunOperator, T> processor) {
        LocalPlayer player = getShooter();
        if (player == null) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.ofNullable(processor.apply(IClientPlayerGunOperator.fromLocalPlayer(player)));
        } catch (Throwable ignored) {
            return java.util.Optional.empty();
        }
    }
}
