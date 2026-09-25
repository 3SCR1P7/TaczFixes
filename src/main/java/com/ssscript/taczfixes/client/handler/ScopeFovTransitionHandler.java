package com.ssscript.taczfixes.client.handler;

import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.ssscript.taczfixes.client.util.ScopeViewHelper;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.util.math.MathUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 瞄具视图切换时的世界层(镜外) FOV 过渡, 仅在镜内放大状态发生变化时生效:
 * - sight→scope(切到有镜内放大的视图): 保持切换前显示值 200ms, 再缓动到游戏自身数值(沿用原逻辑);
 * - scope→sight(切到无镜内放大的视图): 切换瞬间算出目标 FOV, 直接从切换前显示值缓动到该目标;
 * - scope→scope / sight→sight: 不干预世界层 FOV, 交给原生/Arcana 逻辑。
 */
public class ScopeFovTransitionHandler {

    private static final long HOLD_MS = 200L;
    private static final long EASE_MS = 300L;
    private static final double EASE_TARGET_EPSILON = 2.0;
    private static final long SETTLE_TIMEOUT_MS = 1500L;
    private static final long SETTLE_STABLE_MS = 150L;
    private static final double SETTLE_STABLE_EPSILON = 0.05;

    private static boolean worldFovCallPending;
    private static double rawBaseFov = -1.0;
    private static String lastKey = "";
    private static boolean lastMagnified;
    private static Phase phase = Phase.NONE;
    private static long phaseStart;
    private static double transitionFrom;
    private static double transitionTarget = -1.0;
    private static double easeTo = -1.0;
    private static double prevLive = -1.0;
    private static long lastLiveChangeAt;
    private static double lastOutput = -1.0;

    private enum Phase { NONE, HOLD, EASE, EASE_FIXED, SETTLE }

    /** 由 GameRenderer#renderLevel 开头调用: 标记接下来那次 configured-FOV 调用是真正的世界层调用。 */
    public static void markWorldFovCall() {
        worldFovCallPending = true;
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            worldFovCallPending = false;
        }
    }

    /** 记录世界层未修改前的基础 FOV(供计算 target)。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRawFov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov() && worldFovCallPending) {
            rawBaseFov = event.getFOV();
        }
    }

    /** 只处理世界层调用(renderLevel 内那次), Arcana 的镜内投影等 configured-FOV 调用完全不干预。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov() || !worldFovCallPending) {
            return;
        }
        worldFovCallPending = false;
        if (!(event.getCamera().getEntity() instanceof LocalPlayer player)) {
            return;
        }
        handleWorldFov(event, player);
    }

    private void handleWorldFov(ViewportEvent.ComputeFov event, LocalPlayer player) {
        String key = viewKey(player);
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        boolean aiming = operator != null && operator.isAim();
        if (key == null || !aiming) {
            reset(key, player);
            return;
        }
        double live = event.getFOV();
        long now = System.currentTimeMillis();
        if (!key.equals(lastKey)) {
            boolean magnified = magnificationActive(player);
            lastKey = key;
            // scope→scope / sight→sight: 镜内放大状态未变化, 不触发本模组的镜外 FOV 平滑
            if (magnified == lastMagnified) {
                phase = Phase.NONE;
                lastOutput = live;
                return;
            }
            lastMagnified = magnified;
            transitionFrom = lastOutput > 0.0 ? lastOutput : live;
            if (magnified) {
                phase = Phase.HOLD;
                phaseStart = now;
                easeTo = -1.0;
            } else {
                double target = destinationFov(player, rawBaseFov);
                transitionTarget = target > 0.0 ? target : live;
                phase = Phase.EASE_FIXED;
                phaseStart = now;
            }
        }
        if (phase == Phase.HOLD) {
            if (now - phaseStart >= HOLD_MS) {
                phase = Phase.EASE;
                phaseStart = now;
                easeTo = live;
            } else {
                event.setFOV(transitionFrom);
                lastOutput = transitionFrom;
                return;
            }
        }
        if (phase == Phase.EASE) {
            if (easeTo < 0.0 || Math.abs(live - easeTo) > EASE_TARGET_EPSILON) {
                easeTo = live;
            }
            float progress = Math.min(1.0f, (now - phaseStart) / (float) EASE_MS);
            float eased = 1.0f - (float) Math.exp(-5.0f * progress);
            double out = transitionFrom + (easeTo - transitionFrom) * eased;
            if (progress >= 1.0f) {
                phase = Phase.NONE;
            }
            event.setFOV(out);
            lastOutput = out;
            return;
        }
        if (phase == Phase.EASE_FIXED) {
            float progress = Math.min(1.0f, (now - phaseStart) / (float) EASE_MS);
            float eased = 1.0f - (float) Math.exp(-5.0f * progress);
            double out = transitionFrom + (transitionTarget - transitionFrom) * eased;
            if (progress >= 1.0f) {
                if (Math.abs(live - transitionTarget) < 0.5) {
                    phase = Phase.NONE;
                    lastOutput = live;
                    return;
                }
                // 已达目标但游戏自身仍在中间态: 保持目标值, 等其收敛稳定后再交还, 避免瞬跳/抽动。
                phase = Phase.SETTLE;
                phaseStart = now;
                prevLive = live;
                lastLiveChangeAt = now;
            }
            event.setFOV(out);
            lastOutput = out;
            return;
        }
        if (phase == Phase.SETTLE) {
            if (Math.abs(live - prevLive) > SETTLE_STABLE_EPSILON) {
                lastLiveChangeAt = now;
            }
            prevLive = live;
            boolean stable = now - lastLiveChangeAt >= SETTLE_STABLE_MS;
            if (stable && Math.abs(live - transitionTarget) < 0.5) {
                phase = Phase.NONE;
                lastOutput = live;
                return;
            }
            if (now - phaseStart >= SETTLE_TIMEOUT_MS) {
                transitionFrom = transitionTarget;
                transitionTarget = live;
                phase = Phase.EASE_FIXED;
                phaseStart = now;
                return;
            }
            event.setFOV(transitionTarget);
            lastOutput = transitionTarget;
            return;
        }
        lastOutput = live;
    }

    /** 当前视图是否启用镜内放大(scope 视图)。 */
    private static boolean magnificationActive(LocalPlayer player) {
        ClientAttachmentIndex index = currentIndex(player);
        if (index == null) {
            return false;
        }
        CompoundTag tag = currentTag(player);
        return ScopeViewHelper.isCombinedSight(index)
                ? ScopeViewHelper.isScopeView(index, tag)
                : index.isScope();
    }

    /** 当前视图下世界层的目标 FOV: 有镜内放大时为基础 FOV, 否则为基础 FOV 经该视图倍率缩放。 */
    private static double destinationFov(LocalPlayer player, double baseFov) {
        ItemStack gun = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null || baseFov <= 0.0) {
            return -1.0;
        }
        if (magnificationActive(player)) {
            return baseFov;
        }
        float zoom = iGun.getAimingZoom(gun);
        if (zoom <= 1.0001f) {
            return baseFov;
        }
        return MathUtil.magnificationToFov(zoom, baseFov);
    }

    private static ClientAttachmentIndex currentIndex(LocalPlayer player) {
        ItemStack gun = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            return null;
        }
        ResourceLocation id = ScopeSwitchState.attachmentId(iGun, gun, AttachmentType.SCOPE);
        if (id == null || DefaultAssets.isEmptyAttachmentId(id)) {
            return null;
        }
        return TimelessAPI.getClientAttachmentIndex(id).orElse(null);
    }

    private static CompoundTag currentTag(LocalPlayer player) {
        IGun iGun = IGun.getIGunOrNull(player.getMainHandItem());
        return iGun == null ? null : ScopeSwitchState.attachmentTag(iGun, player.getMainHandItem(), AttachmentType.SCOPE);
    }

    /** 未开镜时静默同步当前视图, 避免开镜首帧被误判为切换。 */
    private static void reset(String key, LocalPlayer player) {
        if (key != null) {
            lastKey = key;
        }
        lastMagnified = key != null && magnificationActive(player);
        phase = Phase.NONE;
        lastOutput = -1.0;
        easeTo = -1.0;
        transitionTarget = -1.0;
        prevLive = -1.0;
    }

    private static String viewKey(LocalPlayer player) {
        ItemStack gun = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            return null;
        }
        ResourceLocation id = ScopeSwitchState.attachmentId(iGun, gun, AttachmentType.SCOPE);
        if (id == null || DefaultAssets.isEmptyAttachmentId(id)) {
            return "none";
        }
        CompoundTag tag = ScopeSwitchState.attachmentTag(iGun, gun, AttachmentType.SCOPE);
        ClientAttachmentIndex index = TimelessAPI.getClientAttachmentIndex(id).orElse(null);
        int zoomNumber = tag == null ? 0 : AttachmentItemDataAccessor.getZoomNumberFromTag(tag);
        boolean scopeView = index != null && ScopeViewHelper.isScopeView(index, tag);
        return id + "|" + zoomNumber + "|" + scopeView;
    }
}
