package com.ssscript.taczfixes.client.render;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.client.gameplay.LocalPlayerSprint;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.input.InspectKey;
import com.tacz.guns.client.input.InteractKey;
import com.tacz.guns.client.input.ShootKey;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.config.client.KeyConfig;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.custom.SilenceModifier;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunDefaultMeleeData;
import com.tacz.guns.resource.pojo.data.gun.GunMeleeData;
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.client.render.DualRenderContext;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.ssscript.taczfixes.common.util.DualWieldStackId;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.common.network.ClientMessageOffhandBolt;
import com.ssscript.taczfixes.common.network.ClientMessageOffhandCancelReload;
import com.ssscript.taczfixes.common.network.ClientMessageOffhandFireSelect;
import com.ssscript.taczfixes.common.network.ClientMessageOffhandMelee;
import com.ssscript.taczfixes.common.network.ClientMessageOffhandReload;
import com.ssscript.taczfixes.common.network.ClientMessageOffhandShoot;
import it.unimi.dsi.fastutil.Pair;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.runtime.ObjectMethods;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import com.tacz.guns.compat.playeranimator.AnimationName;
import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;
import com.tacz.guns.compat.playeranimator.animation.AnimationManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.item.Item;

@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = {Dist.CLIENT})
public final class DualWieldClient {
    private static boolean lastLeftDown;
    private static boolean lastRightDown;
    private static boolean lastLeftShootSuccess;
    private static boolean lastRightShootSuccess;
    private static boolean controllerShootDown;
    private static boolean controllerAimDown;
    private static boolean waitShootReleaseAfterFocusChange;
    private static boolean waitAimReleaseAfterFocusChange;
    private static boolean focusAimToggle;
    private static boolean lastFocusAimPhysicalDown;
    private static boolean lastMeleeWasOffhand = true;
    private static long lastOffhandMeleeTimestamp;
    private static long offhandMeleeEndTimestamp;
    private static boolean lastFocusRequested;
    private static boolean focusInputChangedThisTick;
    private static boolean waitLeftReleaseAfterScreen;
    private static boolean waitRightReleaseAfterScreen;
    private static boolean dualWasActive;
    private static volatile boolean offhandShotPending;
    private static volatile long offhandShotToken;
    /** 第三人称枪口火焰归属的手: 由本地预测开火记录(不受 OtherHand 的 clear 影响)。 */
    private static volatile DualRenderContext.HandPhase thirdPersonFlashHand = DualRenderContext.HandPhase.NONE;
    private static volatile long thirdPersonFlashTimestamp = -1L;
    private static final int MAX_SHOT_VISUAL_RESERVATIONS = 256;
    private static ItemStack lastDualMainStack = ItemStack.EMPTY;
    private static ItemStack lastDualOffhandStack = ItemStack.EMPTY;
    private static final Object OFFHAND_SHOT_LOCK = new Object();
    private static final LinkedHashMap<ShotRequestKey, ShotVisualReservation> SHOT_VISUAL_RESERVATIONS = new LinkedHashMap<>();

    private DualWieldClient() {
    }

    public static boolean isDualMode(LocalPlayer player) {
        return player != null && !player.isSpectator() && !player.isDeadOrDying() && DualWieldEligibility.isDualWielding(player) && hasClientDisplay(player.getMainHandItem()) && hasClientDisplay(player.getOffhandItem());
    }

    static boolean isDualMovementActive(LocalPlayer player) {
        return (!dualWasActive || player == null || !isDualMode(player) || OffhandDisplayManager.isPuttingAway() || DualFocusAimState.isPoseVisible()) ? false : true;
    }

    public static void setControllerShootDown(boolean isDown) {
        controllerShootDown = isDown;
    }

    public static void setControllerAimDown(boolean isDown) {
        controllerAimDown = isDown;
    }

    private static boolean lastSyncedOffhandShootState = false;

    /** 同步副手开火键状态到服务端 (Arcana 技能桥接的 ON_CLICK / ON_AUTO_SHOOT)。 */
    private static void syncOffhandShootState(boolean down) {
        if (down == lastSyncedOffhandShootState) {
            return;
        }
        lastSyncedOffhandShootState = down;
        com.ssscript.taczfixes.common.network.NetworkHandler.CHANNEL.sendToServer(
                new com.ssscript.taczfixes.common.network.ClientMessageOffhandShootState(down));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        OffhandCameraController.applyPendingRecoil(player);
        boolean dual = isDualMode(player);
        if (minecraft.screen != null && dualWasActive && !OffhandDisplayManager.isPuttingAway() && player != null && !player.isSpectator() && !player.isDeadOrDying()) {
            cancelPendingOffhandShot();
            waitLeftReleaseAfterScreen = ShootKey.SHOOT_KEY.isDown() || controllerShootDown;
            waitRightReleaseAfterScreen = AimKey.AIM_KEY.isDown() || controllerAimDown;
            resetFocusAimImmediately(player);
            IClientPlayerGunOperator deferredOperator = IClientPlayerGunOperator.fromLocalPlayer(player);
            LocalPlayerDataHolder deferredMainData = deferredOperator.getDataHolder();
            deferredMainData.chargeProgress = 0.0f;
            deferredMainData.isCharging = false;
            OffhandDisplayManager.getClientState().resetCharge();
            lastLeftDown = false;
            lastRightDown = false;
            lastLeftShootSuccess = false;
            lastRightShootSuccess = false;
            controllerShootDown = false;
            controllerAimDown = false;
            syncOffhandShootState(false);
            return;
        }
        if (!dual) {
            boolean dualEnded = dualWasActive;
            dualWasActive = false;
            lastMeleeWasOffhand = true;
            lastOffhandMeleeTimestamp = 0L;
            offhandMeleeEndTimestamp = 0L;
            DualReloadAnimationManager.clear();
            if (dualEnded || DualFocusAimState.isPoseVisible()) {
                resetFocusAimImmediately(player);
            }
            if (dualEnded) {
                MainhandMovementAnimation.resetMovementBoundary(lastDualMainStack);
                cancelPendingOffhandShot();
                if (player == null || player.isSpectator() || player.isDeadOrDying()) {
                    OffhandDisplayManager.clear();
                } else {
                    OffhandDisplayManager.beginPutAway(player, lastDualMainStack, lastDualOffhandStack, player.getMainHandItem(), player.getOffhandItem(), false);
                }
            }
            if (player != null) {
                updateThirdPersonGunAnimations(minecraft, ItemStack.EMPTY, player.getMainHandItem());
                OffhandDisplayManager.updatePutAwayTarget(player.getMainHandItem(), player.getOffhandItem(), false);
            }
            OffhandDisplayManager.tickPutAway(player);
            lastDualMainStack = ItemStack.EMPTY;
            lastDualOffhandStack = ItemStack.EMPTY;
            lastLeftDown = false;
            lastRightDown = false;
            lastLeftShootSuccess = false;
            lastRightShootSuccess = false;
            controllerShootDown = false;
            controllerAimDown = false;
            waitLeftReleaseAfterScreen = false;
            waitRightReleaseAfterScreen = false;
            waitShootReleaseAfterFocusChange = false;
            waitAimReleaseAfterFocusChange = false;
            syncOffhandShootState(false);
            return;
        }
        boolean dualStarted = !dualWasActive;
        if (dualStarted) {
            OffhandDisplayManager.cancelPutAway(player, player.getMainHandItem(), player.getOffhandItem());
            resetFocusAimImmediately(player);
            MainhandMovementAnimation.resetMovementBoundary(player.getMainHandItem());
        }
        dualWasActive = true;
        ItemStack currentMainStack = player.getMainHandItem();
        ItemStack currentOffhandStack = player.getOffhandItem();
        boolean stackPairChanged = (dualStarted || (sameLogicalStack(lastDualMainStack, currentMainStack) && sameLogicalStack(lastDualOffhandStack, currentOffhandStack))) ? false : true;
        if (stackPairChanged && !OffhandDisplayManager.isPuttingAway()) {
            lastMeleeWasOffhand = true;
            lastOffhandMeleeTimestamp = 0L;
            offhandMeleeEndTimestamp = 0L;
            DualReloadAnimationManager.clear();
            MainhandMovementAnimation.resetMovementBoundary(lastDualMainStack);
            cancelPendingOffhandShot();
            waitLeftReleaseAfterScreen = ShootKey.SHOOT_KEY.isDown() || controllerShootDown;
            waitRightReleaseAfterScreen = AimKey.AIM_KEY.isDown() || controllerAimDown;
            resetFocusAimImmediately(player);
            if (taczfixes$isHandSwap(lastDualMainStack, lastDualOffhandStack, currentMainStack, currentOffhandStack)) {
                // F 交换主副手: 不做收枪过渡, 立刻播放两把枪的掏枪动画
                OffhandDisplayManager.swapHandsImmediately(player, currentMainStack, currentOffhandStack);
            } else {
                OffhandDisplayManager.beginPutAway(player, lastDualMainStack, lastDualOffhandStack, currentMainStack, currentOffhandStack, true);
            }
        }
        if (OffhandDisplayManager.isPuttingAway()) {
            OffhandDisplayManager.updatePutAwayTarget(currentMainStack, currentOffhandStack, true);
            OffhandDisplayManager.tickPutAway(player);
            lastDualMainStack = currentMainStack.copy();
            lastDualOffhandStack = currentOffhandStack.copy();
            if (OffhandDisplayManager.isPuttingAway()) {
                lastLeftDown = false;
                lastRightDown = false;
                lastLeftShootSuccess = false;
                lastRightShootSuccess = false;
                controllerShootDown = false;
                controllerAimDown = false;
                resetFocusAimImmediately(player);
                syncOffhandShootState(false);
                return;
            }
        }
        DualReloadAnimationManager.tick(player);
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        ClientOffhandState leftState = OffhandDisplayManager.getClientState();
        if (minecraft.screen != null) {
            cancelPendingOffhandShot();
            waitLeftReleaseAfterScreen = ShootKey.SHOOT_KEY.isDown() || controllerShootDown;
            waitRightReleaseAfterScreen = AimKey.AIM_KEY.isDown() || controllerAimDown;
            resetFocusAimImmediately(player);
            LocalPlayerDataHolder mainData = operator.getDataHolder();
            mainData.chargeProgress = 0.0f;
            mainData.isCharging = false;
            leftState.resetCharge();
            LocalPlayerSprint.stopSprint = leftState.isReloading() || leftState.isBolting();
            lastLeftDown = false;
            lastRightDown = false;
            lastLeftShootSuccess = false;
            lastRightShootSuccess = false;
            controllerShootDown = false;
            controllerAimDown = false;
            lastDualMainStack = player.getMainHandItem().copy();
            lastDualOffhandStack = player.getOffhandItem().copy();
            syncOffhandShootState(false);
            return;
        }
        boolean physicalShootDown = ShootKey.SHOOT_KEY.isDown() || controllerShootDown;
        boolean physicalAimDown = AimKey.AIM_KEY.isDown() || controllerAimDown;
        if (waitLeftReleaseAfterScreen) {
            if (!physicalShootDown) {
                waitLeftReleaseAfterScreen = false;
            }
            physicalShootDown = false;
        }
        if (waitRightReleaseAfterScreen) {
            if (!physicalAimDown) {
                waitRightReleaseAfterScreen = false;
            }
            physicalAimDown = false;
        }
        ItemStack leftGun = player.getOffhandItem();
        ItemStack rightGun = player.getMainHandItem();
        updateThirdPersonGunAnimations(minecraft, leftGun, rightGun);
        leftState.tickManualShotChamberSync(leftGun);
        leftState.tickBoltCompletion(player, leftGun);
        OffhandDisplayManager.synchronizeManualActionCaptureState();
        boolean focusRequested = DualFocusAimKey.FOCUS_AIM_KEY.isDown() && !InteractKey.INTERACT_KEY.isDown();
        focusInputChangedThisTick = focusRequested != lastFocusRequested;
        lastFocusRequested = focusRequested;
        boolean focusTarget = focusRequested && canUseFocusAim(player, operator, leftState, leftGun, rightGun, DualFocusAimState.isActive());
        boolean focusChanged = DualFocusAimState.tick(focusTarget);
        if (focusChanged) {
            handleFocusAimBoundary(player, operator, leftState, rightGun, physicalShootDown, physicalAimDown);
            if (focusTarget) {
                waitShootReleaseAfterFocusChange |= physicalShootDown;
                waitAimReleaseAfterFocusChange = false;
            }
        }
        if (waitShootReleaseAfterFocusChange) {
            if (!physicalShootDown) {
                waitShootReleaseAfterFocusChange = false;
            }
            physicalShootDown = false;
        }
        if (waitAimReleaseAfterFocusChange) {
            if (!physicalAimDown) {
                waitAimReleaseAfterFocusChange = false;
            }
            physicalAimDown = false;
        }
        boolean desiredAim = updateFocusAimLatch(focusTarget, physicalAimDown);
        if (operator.isAim() != desiredAim) {
            operator.aim(desiredAim);
            if (!desiredAim) {
                DualFocusAimState.releasePreservedMainAimAfterInputExit();
            }
        }
        boolean leftDown = focusTarget ? false : physicalShootDown;
        boolean rightDown = focusTarget ? physicalShootDown : physicalAimDown;
        if (InteractKey.INTERACT_KEY.isDown()) {
            LocalPlayerSprint.stopSprint = false;
            LocalPlayerDataHolder mainData2 = operator.getDataHolder();
            mainData2.chargeProgress = 0.0f;
            mainData2.isCharging = false;
            leftState.resetCharge();
            lastLeftDown = false;
            lastRightDown = false;
            lastLeftShootSuccess = false;
            lastRightShootSuccess = false;
            lastDualMainStack = player.getMainHandItem().copy();
            lastDualOffhandStack = player.getOffhandItem().copy();
            syncOffhandShootState(false);
            return;
        }
        LocalPlayerSprint.stopSprint = focusTarget || desiredAim || leftDown || rightDown || leftState.isReloading() || leftState.isBolting();
        if (!focusTarget) {
            tryStartOffhandBolt(player, leftGun, leftState);
        }
        boolean cancelingLeftReload = requestOffhandReloadCancel(leftGun, leftState, leftDown);
        boolean leftContinuous = canContinuouslyShoot(leftGun);
        boolean leftShouldCharge = !cancelingLeftReload && leftDown && (leftContinuous || !lastLeftShootSuccess);
        boolean leftChargeReady = !cancelingLeftReload && leftState.chargeShoot(player, leftGun, leftShouldCharge);
        if (cancelingLeftReload) {
            leftState.resetCharge();
        }
        if (leftChargeReady && ((leftContinuous || !lastLeftShootSuccess) && shootOffhand(player, leftGun, leftState.getChargeProgress()))) {
            lastLeftShootSuccess = true;
        }
        boolean rightContinuous = canContinuouslyShoot(rightGun);
        boolean rightShouldCharge = rightDown && (rightContinuous || !lastRightShootSuccess);
        boolean rightChargeReady = operator.chargeShoot(rightShouldCharge);
        if (rightChargeReady && (rightContinuous || !lastRightShootSuccess)) {
            ShootResult result = operator.shoot();
            if (result == ShootResult.SUCCESS) {
                DualReloadAnimationManager.clearHand(InteractionHand.MAIN_HAND);
                lastRightShootSuccess = true;
                DualMuzzleFlashState.record(DualRenderContext.HandPhase.MAIN);
                markThirdPersonFlashHand(DualRenderContext.HandPhase.MAIN);
                GunDisplayInstance mainDisplay = (GunDisplayInstance) TimelessAPI.getGunDisplay(rightGun).orElse(null);
                if (mainDisplay != null) {
                    LuaAnimationStateMachine<GunAnimationStateContext> mainMachine = mainDisplay.getAnimationStateMachine();
                    if (mainMachine != null && mainMachine.isInitialized()) {
                        mainMachine.trigger("shoot");
                    }
                }
            }
        }
        if ((!leftDown && lastLeftDown) || (!rightDown && lastRightDown)) {
            SoundPlayManager.resetDryFireSound();
        }
        if (!leftDown) {
            lastLeftShootSuccess = false;
        }
        if (!rightDown) {
            lastRightShootSuccess = false;
        }
        syncOffhandShootState(leftDown);
        lastLeftDown = leftDown;
        lastRightDown = rightDown;
        lastDualMainStack = player.getMainHandItem().copy();
        lastDualOffhandStack = player.getOffhandItem().copy();
    }

    private static boolean canUseFocusAim(LocalPlayer player, IClientPlayerGunOperator operator, ClientOffhandState leftState, ItemStack leftGun, ItemStack rightGun, boolean alreadyActive) {
        if (player == null || operator == null || leftState == null || !isDualMode(player) || OffhandDisplayManager.isPuttingAway()) {
            return false;
        }
        IGunOperator syncedOperator = IGunOperator.fromLivingEntity(player);
        LocalPlayerDataHolder mainData = operator.getDataHolder();
        boolean inspectBlocksFocus = isInspectActive(rightGun, false) || isInspectActive(leftGun, true);
        if (syncedOperator.getSynReloadState().getStateType().isReloading() || syncedOperator.getSynDrawCoolDown() != 0 || DualReloadAnimationManager.isHandActive(InteractionHand.MAIN_HAND)) {
            return false;
        }
        if (inspectBlocksFocus && !alreadyActive && !focusInputChangedThisTick) {
            return false;
        }
        if (!alreadyActive) {
            if (leftState.isDrawing() || leftState.isBolting() || leftState.isManualShotAwaitingChamberSync() || leftState.isServerManualActionBoltReady() || leftState.isFireSelectRequestPending() || leftState.isShootTransitionLocked(player, leftGun) || syncedOperator.getSynIsBolting() || mainData.isBolting || mainHandLockedForOffhand(mainData, syncedOperator) || operator.getClientShootCoolDown() > 0 || leftState.getShootCoolDown(player, leftGun) > 0 || hasPendingOffhandShotSequence()) {
                return false;
            }
            return true;
        }
        return true;
    }

    /** 主手状态锁: 因近战冷却而保持的锁不影响副手; 其余持锁状态(换弹/射击冷却/切枪/拉栓)仍视为忙。 */
    private static boolean mainHandLockedForOffhand(LocalPlayerDataHolder data, IGunOperator operator) {
        if (!data.clientStateLock) {
            return false;
        }
        if (operator.getSynMeleeCoolDown() == 0) {
            return true;
        }
        return operator.getSynReloadState().getStateType().isReloading() || operator.getSynShootCoolDown() > 0
                || operator.getSynDrawCoolDown() > 0 || operator.getSynIsBolting() || data.isBolting;
    }

    private static boolean isInspectActive(ItemStack stack, boolean offhand) {
        GunDisplayInstance orCreate;
        if (offhand) {
            orCreate = OffhandDisplayManager.getOrCreate(stack);
        } else {
            orCreate = (GunDisplayInstance) TimelessAPI.getGunDisplay(stack).orElse(null);
        }
        GunDisplayInstance display = orCreate;
        return display != null && DualInspectAnimationFilter.isInspectActive(display.getGunModel());
    }

    private static void updateThirdPersonGunAnimations(Minecraft minecraft, ItemStack leftGun, ItemStack rightGun) {
        LuaAnimationStateMachine<GunAnimationStateContext> animationStateMachine;
        if (minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        // 第三人称下主手换弹的收枪/掏枪覆盖动画会把枪械模型拉出画面(表现为飞到玩家身后), 这里直接不应用
        DualReloadAnimationManager.clearHand(InteractionHand.MAIN_HAND);
        if (!leftGun.isEmpty()) {
            OffhandDisplayManager.updateThirdPersonAnimation(leftGun, 1.0f);
        }
        GunDisplayInstance display = (GunDisplayInstance) TimelessAPI.getGunDisplay(rightGun).orElse(null);
        BedrockGunModel model = display == null ? null : display.getGunModel();
        if (display == null) {
            animationStateMachine = null;
        } else {
            animationStateMachine = display.getAnimationStateMachine();
        }
        LuaAnimationStateMachine<GunAnimationStateContext> stateMachine = animationStateMachine;
        if (model == null || stateMachine == null || !stateMachine.isInitialized()) {
            return;
        }
        model.cleanAnimationTransform();
        model.cleanCameraAnimationTransform();
        stateMachine.processContextIfExist(context -> {
            context.setCurrentGunItem(rightGun);
            context.setPartialTicks(1.0f);
        });
        stateMachine.update();
        model.cleanCameraAnimationTransform();
    }

    private static boolean hasPendingOffhandShotSequence() {
        boolean z;
        synchronized (OFFHAND_SHOT_LOCK) {
            z = offhandShotPending || !SHOT_VISUAL_RESERVATIONS.isEmpty();
        }
        return z;
    }

    private static boolean updateFocusAimLatch(boolean focusActive, boolean physicalAimDown) {
        if (!focusActive) {
            focusAimToggle = false;
            lastFocusAimPhysicalDown = false;
            return false;
        }
        boolean holdToAim = KeyConfig.HOLD_TO_AIM == null || Boolean.TRUE.equals(KeyConfig.HOLD_TO_AIM.get());
        if (holdToAim) {
            lastFocusAimPhysicalDown = physicalAimDown;
            return physicalAimDown;
        }
        if (physicalAimDown && !lastFocusAimPhysicalDown) {
            focusAimToggle = !focusAimToggle;
        }
        lastFocusAimPhysicalDown = physicalAimDown;
        return focusAimToggle;
    }

    private static void handleFocusAimBoundary(LocalPlayer player, IClientPlayerGunOperator operator, ClientOffhandState leftState, ItemStack rightGun, boolean physicalShootDown, boolean physicalAimDown) {
        LocalPlayerDataHolder mainData = operator.getDataHolder();
        mainData.chargeProgress = 0.0f;
        mainData.isCharging = false;
        leftState.resetCharge();
        lastLeftDown = false;
        lastRightDown = false;
        lastLeftShootSuccess = false;
        lastRightShootSuccess = false;
        focusAimToggle = false;
        lastFocusAimPhysicalDown = false;
        boolean requireRelease = shouldWaitForFocusBoundaryRelease();
        waitShootReleaseAfterFocusChange |= physicalShootDown && requireRelease;
        waitAimReleaseAfterFocusChange |= physicalAimDown && requireRelease;
        SoundPlayManager.resetDryFireSound();
        MainhandMovementAnimation.resetMovementBoundary(rightGun);
        if (operator.isAim()) {
            operator.aim(false);
        }
    }

    private static boolean shouldWaitForFocusBoundaryRelease() {
        return !DualFocusAimState.isActive() || focusInputChangedThisTick;
    }

    static void cancelFocusAimForAction(LocalPlayer player) {
        if (player == null) {
            return;
        }
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (!DualFocusAimState.isActive() && !operator.isAim()) {
            return;
        }
        if (DualFocusAimState.isActive()) {
            DualFocusAimState.tick(false);
        }
        handleFocusAimBoundary(player, operator, OffhandDisplayManager.getClientState(), player.getMainHandItem(), ShootKey.SHOOT_KEY.isDown() || controllerShootDown, AimKey.AIM_KEY.isDown() || controllerAimDown);
    }

    private static void resetFocusAimImmediately(LocalPlayer player) {
        if (player != null) {
            IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
            LocalPlayerDataHolder mainData = operator.getDataHolder();
            mainData.chargeProgress = 0.0f;
            mainData.isCharging = false;
            if (operator.isAim()) {
                operator.aim(false);
            }
        }
        OffhandDisplayManager.getClientState().resetCharge();
        DualFocusAimState.resetImmediately();
        focusAimToggle = false;
        lastFocusAimPhysicalDown = false;
        lastFocusRequested = false;
        focusInputChangedThisTick = false;
        waitShootReleaseAfterFocusChange = false;
        waitAimReleaseAfterFocusChange = false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRenderOffhand(RenderHandEvent event) {
        LocalPlayer player;
        ItemStack itemStackM_21206_;
        if (event.getHand() != InteractionHand.OFF_HAND || (player = Minecraft.getInstance().player) == null) {
            return;
        }
        boolean dual = isDualMode(player);
        boolean puttingAway = OffhandDisplayManager.isPuttingAway();
        if (!dual && !puttingAway) {
            return;
        }
        if (Minecraft.getInstance().screen instanceof GunRefitScreen) {
            event.setCanceled(true);
            return;
        }

        if (puttingAway) {
            itemStackM_21206_ = OffhandDisplayManager.getPutAwayStack();
        } else {
            itemStackM_21206_ = player.getOffhandItem();
        }
        ItemStack renderStack = itemStackM_21206_;
        if (renderStack.isEmpty()) {
            return;
        }
        boolean rendered = DualFirstPersonRenderer.renderOffhand(player, renderStack, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), event.getPartialTick());
        if (rendered) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void blockVanillaInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (Minecraft.getInstance().screen != null || !isDualMode(player) || InteractKey.INTERACT_KEY.isDown()) {
            return;
        }
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDualActionKey(InputEvent.Key event) {
        if (event.getAction() != 1) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (Minecraft.getInstance().screen != null || !isDualMode(player) || OffhandDisplayManager.isPuttingAway()) {
            return;
        }
        if (InspectKey.INSPECT_KEY.matches(event.getKey(), event.getScanCode())) {
            cancelFocusAimForAction(player);
            inspectOffhand(player);
        } else if (OffhandFireSelectKey.OFFHAND_FIRE_SELECT_KEY.matches(event.getKey(), event.getScanCode())) {
            cancelFocusAimForAction(player);
            fireSelectOffhand(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLocalGunFire(GunFireEvent event) {
        if (event.getLogicalSide() != LogicalSide.CLIENT) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || event.getShooter() != player) {
            return;
        }
        // 网络同步回来的 GunFireEvent 里的 ItemStack 是新对象, 不能用 == 比较, 否则主手开火记录不到
        ItemStack firedStack = event.getGunItemStack();
        if (sameLogicalStack(firedStack, player.getMainHandItem())) {
            DualMuzzleFlashState.record(DualRenderContext.HandPhase.MAIN);
            markThirdPersonFlashHand(DualRenderContext.HandPhase.MAIN);
        } else if (sameLogicalStack(firedStack, player.getOffhandItem())) {
            DualMuzzleFlashState.record(DualRenderContext.HandPhase.OFFHAND);
            markThirdPersonFlashHand(DualRenderContext.HandPhase.OFFHAND);
        }
    }

    static void markThirdPersonFlashHand(DualRenderContext.HandPhase hand) {
        thirdPersonFlashHand = hand;
        thirdPersonFlashTimestamp = System.currentTimeMillis();
    }

    public static DualRenderContext.HandPhase getThirdPersonFlashHand() {
        return thirdPersonFlashHand;
    }

    public static boolean isThirdPersonFlashHandFresh(long windowMillis) {
        return thirdPersonFlashTimestamp >= 0 && System.currentTimeMillis() - thirdPersonFlashTimestamp <= windowMillis;
    }

    /**
     * 双持副手开火/换弹时补播 TaCZ 的第三人称玩家动画。
     * TaCZ 只对主手的 GunShootEvent/GunReloadEvent 播放第三人称手臂动画, 副手缺这一段,
     * 导致第三人称下副手看起来只有第一人称的枪械动画。
     */
    private static void playOffhandThirdPersonPlayerAnimation(LocalPlayer player, ItemStack stack, String upperAnimation, String lieAnimation) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }
        if (Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            return;
        }
        if (!PlayerAnimatorCompat.isInstalled()) {
            return;
        }
        GunDisplayInstance display = OffhandDisplayManager.getOrCreate(stack);
        if (display == null || !AnimationManager.hasPlayerAnimator3rd(display)) {
            return;
        }
        boolean lie = !player.isSwimming() && player.getPose() == Pose.SWIMMING;
        String animation = lie ? lieAnimation : upperAnimation;
        try {
            AnimationManager.playOnceAnimation(player, display, PlayerAnimatorCompat.ONCE_UPPER_ANIMATION, animation);
        } catch (RuntimeException exception) {
            TaczFixesMod.LOGGER.error("Failed to play offhand third-person animation {}", animation, exception);
        }
    }

    private static boolean hasClientDisplay(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        return gun != null && TimelessAPI.getClientGunIndex(gun.getGunId(stack)).isPresent() && ((Boolean) TimelessAPI.getGunDisplay(stack).map(display -> {
            return Boolean.valueOf((display.getGunModel() == null || display.getAnimationStateMachine() == null) ? false : true);
        }).orElse(false)).booleanValue();
    }

    private static boolean sameLogicalStack(ItemStack first, ItemStack second) {
        if (first == null || second == null || first.isEmpty() || second.isEmpty()) {
            return first != null && second != null && first.isEmpty() && second.isEmpty();
        }
        UUID firstId = DualWieldStackId.get(first);
        UUID secondId = DualWieldStackId.get(second);
        if (firstId != null && secondId != null) {
            return firstId.equals(secondId);
        }
        IGun firstGun = IGun.getIGunOrNull(first);
        IGun secondGun = IGun.getIGunOrNull(second);
        return firstGun != null && secondGun != null && first.getItem() == second.getItem() && firstGun.getGunId(first).equals(secondGun.getGunId(second));
    }

    /** 是否为主副手互换(上一帧的主/副手正好是这一帧的副/主手)。 */
    private static boolean taczfixes$isHandSwap(ItemStack previousMain, ItemStack previousOffhand,
                                                ItemStack currentMain, ItemStack currentOffhand) {
        return sameLogicalStack(previousMain, currentOffhand)
                && sameLogicalStack(previousOffhand, currentMain)
                && !sameLogicalStack(previousMain, previousOffhand);
    }

    private static boolean canContinuouslyShoot(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return false;
        }
        FireMode mode = gun.getFireMode(stack);
        if (mode == FireMode.AUTO) {
            return true;
        }
        if (mode == FireMode.BURST) {
            boolean continuous = ((Boolean) TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).map(index -> {
                return Boolean.valueOf(index.getGunData().getBurstData().isContinuousShoot());
            }).orElse(false)).booleanValue();
            return continuous;
        }
        return false;
    }

    private static boolean requestOffhandReloadCancel(ItemStack stack, ClientOffhandState state, boolean leftDown) {
        int reloadRequestId;
        if (!leftDown || !state.isReloading()) {
            return false;
        }
        UUID stackId = DualWieldStackId.get(stack);
        if (stackId != null && (reloadRequestId = state.beginReloadCancel(stackId)) != 0) {
            NetworkHandler.CHANNEL.sendToServer(new ClientMessageOffhandCancelReload(stackId, reloadRequestId));
            return true;
        }
        return true;
    }

    private static boolean shootOffhand(LocalPlayer player, ItemStack stack, float chargeProgress) {
        UUID sourceStackId;
        GunData gunData;
        if (offhandShotPending || (sourceStackId = DualWieldStackId.get(stack)) == null) {
            return false;
        }
        IGun gun = IGun.getIGunOrNull(stack);
        ClientGunIndex gunIndex = gun == null ? null : (ClientGunIndex) TimelessAPI.getClientGunIndex(gun.getGunId(stack)).orElse(null);
        GunDisplayInstance display = OffhandDisplayManager.getOrCreate(stack);
        if (gun == null || gunIndex == null || display == null) {
            return false;
        }
        ClientOffhandState state = OffhandDisplayManager.getClientState();
        long coolDown = state.getShootCoolDown(player, stack);
        if (state.isDrawing() || state.isReloading() || state.isBolting() || state.isManualShotAwaitingChamberSync() || state.isServerManualActionBoltReady() || !state.canBeginBoltCycle() || coolDown >= 50 || isGlobalShootInputBlocked(player) || IGunOperator.fromLivingEntity(player).getSynSprintTime() > 0.0f || (gunData = gunIndex.getGunData()) == null) {
            return false;
        }
        Bolt bolt = gunData.getBolt();
        boolean inBarrel = gun.hasBulletInBarrel(stack) && bolt != Bolt.OPEN_BOLT;
        boolean inventoryAmmo = gun.hasInventoryAmmo(player, stack, IGunOperator.fromLivingEntity(player).needCheckAmmo());
        int ammo = gun.getCurrentAmmoCount(stack) + (inBarrel ? 1 : 0);
        boolean noAmmo = gun.useInventoryAmmo(stack) ? (inventoryAmmo || inBarrel) ? false : true : ammo < 1;
        if (noAmmo || (gunData.hasHeatData() && gun.isOverheatLocked(stack))) {
            SoundPlayManager.playDryFireSound(player, display);
            return false;
        }
        if (com.ssscript.taczfixes.common.util.GunBlocking.isFireDisabled(player, stack)) {
            SoundPlayManager.playDryFireSound(player, display);
            return false;
        }
        if (com.ssscript.taczfixes.common.util.UnderwaterShooting.isBlocked(player, stack)) {
            SoundPlayManager.playDryFireSound(player, display);
            return false;
        }
        com.ssscript.taczfixes.common.data.GunTaczFixesData.ChargeConfig chargeCfg =
                com.ssscript.taczfixes.common.util.ChargeStorage.config(stack);
        if (chargeCfg != null && Boolean.TRUE.equals(chargeCfg.blocking_fire)
                && chargeCfg.fire_consumption != null && chargeCfg.fire_consumption.intValue() > 0
                && com.ssscript.taczfixes.common.util.ChargeStorage.getMax(stack) > 0
                && com.ssscript.taczfixes.common.util.ChargeStorage.get(stack) < chargeCfg.fire_consumption.intValue()) {
            SoundPlayManager.playDryFireSound(player, display);
            return false;
        }
        if (bolt == Bolt.MANUAL_ACTION && !inBarrel) {
            tryStartOffhandBolt(player, stack, state);
            return false;
        }
        DualReloadAnimationManager.clearHand(InteractionHand.OFF_HAND);
        scheduleOffhandInitialShot(player, stack, sourceStackId, gunData, coolDown, chargeProgress, ammo, bolt == Bolt.MANUAL_ACTION);
        state.consumeChargeAfterShot(stack, gunData);
        DynamicCrosshair.onShot(player, stack);
        return true;
    }

    private static void scheduleOffhandInitialShot(LocalPlayer player, ItemStack stack, UUID sourceStackId, GunData sourceGunData, long delayMillis, float chargeProgress, int availableAmmo, boolean manualAction) {
        long token;
        synchronized (OFFHAND_SHOT_LOCK) {
            token = offhandShotToken + 1;
            offhandShotToken = token;
            offhandShotPending = true;
        }
        ClientOffhandState state = OffhandDisplayManager.getClientState();
        LocalPlayerDataHolder mainData = IClientPlayerGunOperator.fromLocalPlayer(player).getDataHolder();
        IGun sourceGun = IGun.getIGunOrNull(stack);
        ResourceLocation sourceGunId = sourceGun == null ? null : sourceGun.getGunId(stack);
        ItemStack recoilStack = stack.copy();
        LocalPlayerDataHolder.SCHEDULED_EXECUTOR_SERVICE.schedule(() -> {
            dispatchScheduledOffhandShot(token, player, sourceStackId, sourceGunId, recoilStack, sourceGunData, state, mainData, chargeProgress, availableAmmo, manualAction);
        }, Math.max(delayMillis, 0L), TimeUnit.MILLISECONDS);
    }

    public static void dispatchScheduledOffhandShot(long token, LocalPlayer sourcePlayer, UUID sourceStackId, ResourceLocation sourceGunId, ItemStack recoilStack, GunData recoilGunData, ClientOffhandState state, LocalPlayerDataHolder mainData, float chargeProgress, int availableAmmo, boolean manualAction) {
        synchronized (OFFHAND_SHOT_LOCK) {
            if (offhandShotPending && token == offhandShotToken) {
                long shootTimestamp = state.recordShot();
                long relativeTimestamp = shootTimestamp - mainData.clientBaseTimestamp;
                state.recordShootRequest(sourceStackId, relativeTimestamp);
                if (manualAction) {
                    state.beginShootTransitionLock(sourceStackId, relativeTimestamp);
                }
                ShotVisualReservation reservation = registerShotVisualReservationLocked(new ShotRequestKey(sourceStackId, relativeTimestamp));
                if (manualAction) {
                    state.beginManualShotAwaitingChamberSync(sourceStackId, relativeTimestamp);
                }
                boolean sent = false;
                try {
                    NetworkHandler.CHANNEL.sendToServer(new ClientMessageOffhandShoot(sourceStackId, relativeTimestamp, chargeProgress));
                    sent = true;
                    offhandShotPending = false;
                    if (1 == 0) {
                        state.discardShootRequest(sourceStackId, relativeTimestamp);
                        if (manualAction) {
                            state.discardManualShotAwaitingChamberSync(sourceStackId, relativeTimestamp);
                        }
                        cancelShotVisualReservationLocked(reservation);
                    }
                    try {
                        Minecraft.getInstance().execute(() -> {
                            applyDispatchedOffhandShot(sourcePlayer, sourceStackId, sourceGunId, recoilStack, recoilGunData, availableAmmo, reservation);
                        });
                    } catch (RuntimeException exception) {
                        cancelShotVisualReservation(reservation);
                        completeShotVisualTask(reservation);
                        TaczFixesMod.LOGGER.warn("Failed to enqueue offhand shot visuals", exception);
                    }
                } catch (Throwable th) {
                    offhandShotPending = false;
                    if (!sent) {
                        state.discardShootRequest(sourceStackId, relativeTimestamp);
                        if (manualAction) {
                            state.discardManualShotAwaitingChamberSync(sourceStackId, relativeTimestamp);
                        }
                        cancelShotVisualReservationLocked(reservation);
                    }
                    throw th;
                }
            }
        }
    }

    public static void applyDispatchedOffhandShot(LocalPlayer sourcePlayer, UUID sourceStackId, ResourceLocation sourceGunId, ItemStack recoilStack, GunData recoilGunData, int availableAmmo, ShotVisualReservation reservation) {
        try {
            boolean liveShotSource = isLiveOffhandShotSource(sourcePlayer, sourceStackId, sourceGunId);
            if (liveShotSource && recoilGunData != null && recoilStack != null && !recoilStack.isEmpty()) {
                OffhandCameraController.recordShot(sourcePlayer, recoilStack, recoilGunData);
            }
            if (reservation.isCanceled()) {
                return;
            }
            if (liveShotSource && recoilGunData != null && recoilStack != null && !recoilStack.isEmpty()) {
                scheduleOffhandBurstShots(sourcePlayer, sourceStackId, sourceGunId, recoilStack, recoilGunData, availableAmmo, reservation);
            }
            if (!reservation.isCanceled()) {
                playScheduledOffhandInitialVisual(sourcePlayer, sourceStackId, sourceGunId);
            }
            completeShotVisualTask(reservation);
        } finally {
            completeShotVisualTask(reservation);
        }
    }

    private static boolean isLiveOffhandShotSource(LocalPlayer sourcePlayer, UUID sourceStackId, ResourceLocation sourceGunId) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != sourcePlayer || player == null || player.isSpectator() || player.isDeadOrDying() || !isDualMode(player) || sourceStackId == null || sourceGunId == null) {
            return false;
        }
        ItemStack currentStack = player.getOffhandItem();
        UUID currentStackId = DualWieldStackId.get(currentStack);
        IGun currentGun = IGun.getIGunOrNull(currentStack);
        return sourceStackId.equals(currentStackId) && currentGun != null && sourceGunId.equals(currentGun.getGunId(currentStack));
    }

    private static void playScheduledOffhandInitialVisual(LocalPlayer sourcePlayer, UUID sourceStackId, ResourceLocation sourceGunId) {
        ClientGunIndex gunIndex;
        GunData gunData;
        GunDisplayInstance display;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != sourcePlayer || minecraft.screen != null || InteractKey.INTERACT_KEY.isDown() || player.isSpectator() || player.isDeadOrDying() || !isDualMode(player)) {
            return;
        }
        ItemStack currentStack = player.getOffhandItem();
        UUID currentStackId = DualWieldStackId.get(currentStack);
        boolean sameStack = sourceStackId.equals(currentStackId);
        IGun gun = IGun.getIGunOrNull(currentStack);
        if (!sameStack || gun == null || sourceGunId == null || !sourceGunId.equals(gun.getGunId(currentStack)) || (gunIndex = (ClientGunIndex) TimelessAPI.getClientGunIndex(gun.getGunId(currentStack)).orElse(null)) == null || (gunData = gunIndex.getGunData()) == null || (display = OffhandDisplayManager.getOrCreate(currentStack)) == null) {
            return;
        }
        ClientOffhandState state = OffhandDisplayManager.getClientState();
        if (isOffhandShotVisualBlocked(state)) {
            return;
        }
        playOffhandShotVisual(player, currentStack, display, gunData);
    }

    private static boolean isGlobalShootInputBlocked(LocalPlayer player) {
        return System.currentTimeMillis() - LocalPlayerDataHolder.clientClickButtonTimestamp < 50;
    }

    private static boolean isOffhandShotVisualBlocked(ClientOffhandState state) {
        return state.isDrawing() || state.isReloading() || state.isBolting();
    }

    static void cancelPendingOffhandShot() {
        synchronized (OFFHAND_SHOT_LOCK) {
            offhandShotToken++;
            offhandShotPending = false;
            for (ShotVisualReservation reservation : SHOT_VISUAL_RESERVATIONS.values()) {
                reservation.cancel();
            }
            SHOT_VISUAL_RESERVATIONS.clear();
        }
    }

    static void cancelOffhandShot(UUID stackId, long shootTimestamp) {
        if (stackId == null) {
            return;
        }
        synchronized (OFFHAND_SHOT_LOCK) {
            ShotRequestKey key = new ShotRequestKey(stackId, shootTimestamp);
            ShotVisualReservation reservation = SHOT_VISUAL_RESERVATIONS.remove(key);
            if (reservation != null) {
                reservation.cancel();
            }
        }
    }

    private static ShotVisualReservation registerShotVisualReservationLocked(ShotRequestKey key) {
        ShotVisualReservation previous = SHOT_VISUAL_RESERVATIONS.remove(key);
        if (previous != null) {
            previous.cancel();
        }
        while (SHOT_VISUAL_RESERVATIONS.size() >= MAX_SHOT_VISUAL_RESERVATIONS) {
            Iterator<Map.Entry<ShotRequestKey, ShotVisualReservation>> iterator = SHOT_VISUAL_RESERVATIONS.entrySet().iterator();
            if (!iterator.hasNext()) {
                break;
            }
            Map.Entry<ShotRequestKey, ShotVisualReservation> eldest = iterator.next();
            eldest.getValue().cancel();
            iterator.remove();
        }
        ShotVisualReservation reservation = new ShotVisualReservation(key);
        SHOT_VISUAL_RESERVATIONS.put(key, reservation);
        return reservation;
    }

    private static void cancelShotVisualReservationLocked(ShotVisualReservation reservation) {
        if (reservation == null) {
            return;
        }
        SHOT_VISUAL_RESERVATIONS.remove(reservation.key(), reservation);
        reservation.cancel();
    }

    private static void cancelShotVisualReservation(ShotVisualReservation reservation) {
        synchronized (OFFHAND_SHOT_LOCK) {
            cancelShotVisualReservationLocked(reservation);
        }
    }

    private static void completeShotVisualTask(ShotVisualReservation reservation) {
        if (reservation == null || reservation.completeTask() > 0) {
            return;
        }
        synchronized (OFFHAND_SHOT_LOCK) {
            SHOT_VISUAL_RESERVATIONS.remove(reservation.key(), reservation);
        }
    }

    private static boolean tryStartOffhandBolt(LocalPlayer player, ItemStack stack, ClientOffhandState state) {
        IGun gun;
        boolean z;
        UUID sourceStackId;
        if (state.isDrawing() || state.isReloading() || state.isBolting() || state.isShootTransitionLocked(player, stack) || state.isManualShotAwaitingChamberSync() || !state.canBeginBoltCycle() || state.getShootCoolDown(player, stack) != 0 || (gun = IGun.getIGunOrNull(stack)) == null) {
            return false;
        }
        GunData gunData = (GunData) TimelessAPI.getClientGunIndex(gun.getGunId(stack)).map((v0) -> {
            return v0.getGunData();
        }).orElse(null);
        GunDisplayInstance display = OffhandDisplayManager.getOrCreate(stack);
        boolean serverBoltReady = state.isServerManualActionBoltReady();
        if (gunData != null && display != null && gunData.getBolt() == Bolt.MANUAL_ACTION) {
            if (gun.hasBulletInBarrel(stack) && !serverBoltReady) {
                return false;
            }
            boolean inventoryAmmo = gun.hasInventoryAmmo(player, stack, IGunOperator.fromLivingEntity(player).needCheckAmmo());
            if (gun.useInventoryAmmo(stack)) {
                z = !inventoryAmmo;
            } else {
                z = gun.getCurrentAmmoCount(stack) < 1;
            }
            boolean noAmmo = z;
            if ((noAmmo && !serverBoltReady) || (sourceStackId = DualWieldStackId.get(stack)) == null) {
                return false;
            }
            cancelPendingOffhandShot();
            state.beginBolt(sourceStackId);
            NetworkHandler.CHANNEL.sendToServer(new ClientMessageOffhandBolt(sourceStackId, state.getBoltRequestId()));
            OffhandDisplayManager.triggerNative("blot");
            SoundPlayManager.playBoltSound(player, display);
            return true;
        }
        return false;
    }

    private static void playOffhandShotVisual(LocalPlayer player, ItemStack stack, GunDisplayInstance display, GunData gunData) {
        OffhandDisplayManager.triggerNative("shoot");
        DualMuzzleFlashState.record(DualRenderContext.HandPhase.OFFHAND);
        markThirdPersonFlashHand(DualRenderContext.HandPhase.OFFHAND);
        // 副手开火是服务端权威的, TaCZ 的 ServerMessageGunFire 只发给追踪该实体的其他玩家(TRACKING_ENTITY 不含自己),
        // 本地玩家的副手拿不到 GunFireEvent, ThirdPersonMuzzleParticleManager 永远没有这副手的开火记录, 第三人称自然没有火光。
        // 这里在副手开火瞬间自己补一条记录。
        com.tacz.guns.client.particle.ThirdPersonMuzzleParticleManager.onShoot(player, stack);
        boolean aiming = IGunOperator.fromLivingEntity(player).getSynAimingProgress() > 0.0f;
        playOffhandThirdPersonPlayerAnimation(player, stack,
                aiming ? AnimationName.AIM_FIRE_UPPER : AnimationName.NORMAL_FIRE_UPPER,
                aiming ? AnimationName.LIE_AIM_FIRE : AnimationName.LIE_NORMAL_FIRE);
        MuzzleFlashRender.onShoot();
        playShootSound(player, stack, display, gunData);
        taczfixes$addOffhandFireLight(player, stack);
    }

    /** 副手开火的枪口光照(客户端预测, 规则与主手 GunLightHandler 一致, 位置偏向左手的枪口)。 */
    private static void taczfixes$addOffhandFireLight(LocalPlayer player, ItemStack stack) {
        com.ssscript.taczfixes.common.data.GunTaczFixesData.LightConfig light =
                com.ssscript.taczfixes.common.data.TaczFixesDataManager.resolveLight(stack);
        if (light == null || light.fire == null || light.fire.time == null || light.fire.time <= 0) {
            return;
        }
        net.minecraft.world.phys.Vec3 look = player.getLookAngle();
        net.minecraft.world.phys.Vec3 left = new net.minecraft.world.phys.Vec3(look.z, 0.0d, -look.x);
        left = left.lengthSqr() > 1.0E-4d ? left.normalize().scale(0.3d) : net.minecraft.world.phys.Vec3.ZERO;
        net.minecraft.world.phys.Vec3 pos = player.getEyePosition().add(look.scale(0.5d)).add(left);
        ClientGunLightManager.add(net.minecraft.core.BlockPos.containing(pos), light.fire);
    }

    private static void scheduleOffhandBurstShots(LocalPlayer player, UUID sourceStackId, ResourceLocation sourceGunId, ItemStack recoilStack, GunData gunData, int availableAmmo, ShotVisualReservation reservation) {
        IGun gun = IGun.getIGunOrNull(recoilStack);
        if (reservation.isCanceled() || gun == null || gun.getFireMode(recoilStack) != FireMode.BURST || gunData.getBurstData() == null) {
            return;
        }
        int burstCount = Math.max(gunData.getBurstData().getCount(), 1);
        if (IGunOperator.fromLivingEntity(player).consumesAmmoOrNot() && !gun.useInventoryAmmo(recoilStack)) {
            burstCount = Math.min(burstCount, Math.max(availableAmmo, 1));
        }
        if (burstCount <= 1) {
            return;
        }
        long periodMillis = Math.max(gunData.getBurstShootInterval(), 1L);
        int burstTaskCount = burstCount - 1;
        reservation.addTasks(burstTaskCount);
        int scheduledTaskCount = 0;
        for (int shotIndex = 1; shotIndex < burstCount; shotIndex++) {
            try {
                long delayMillis = periodMillis * shotIndex;
                LocalPlayerDataHolder.SCHEDULED_EXECUTOR_SERVICE.schedule(() -> {
                    enqueueScheduledBurstShot(player, sourceStackId, sourceGunId, recoilStack, gunData, reservation);
                }, delayMillis, TimeUnit.MILLISECONDS);
                scheduledTaskCount++;
            } catch (RuntimeException exception) {
                cancelShotVisualReservation(reservation);
                int unscheduledTaskCount = burstTaskCount - scheduledTaskCount;
                for (int index = 0; index < unscheduledTaskCount; index++) {
                    completeShotVisualTask(reservation);
                }
                TaczFixesMod.LOGGER.warn("Failed to schedule offhand burst visuals", exception);
                return;
            }
        }
    }

    public static void enqueueScheduledBurstShot(LocalPlayer sourcePlayer, UUID sourceStackId, ResourceLocation sourceGunId, ItemStack recoilStack, GunData recoilGunData, ShotVisualReservation reservation) {
        try {
            Minecraft.getInstance().execute(() -> {
                try {
                    applyScheduledBurstShot(sourcePlayer, sourceStackId, sourceGunId, recoilStack, recoilGunData, reservation);
                    completeShotVisualTask(reservation);
                } catch (Throwable th) {
                    completeShotVisualTask(reservation);
                    throw th;
                }
            });
        } catch (RuntimeException exception) {
            completeShotVisualTask(reservation);
            TaczFixesMod.LOGGER.warn("Failed to enqueue an offhand burst visual", exception);
        }
    }

    private static void applyScheduledBurstShot(LocalPlayer sourcePlayer, UUID sourceStackId, ResourceLocation sourceGunId, ItemStack recoilStack, GunData recoilGunData, ShotVisualReservation reservation) {
        if (reservation.isCanceled() || !isLiveOffhandShotSource(sourcePlayer, sourceStackId, sourceGunId) || recoilStack == null || recoilStack.isEmpty() || recoilGunData == null) {
            return;
        }
        OffhandCameraController.recordShot(sourcePlayer, recoilStack, recoilGunData);
        if (!reservation.isCanceled()) {
            playScheduledBurstVisual(sourcePlayer, sourceStackId, sourceGunId, reservation);
        }
    }

    private static void playScheduledBurstVisual(LocalPlayer sourcePlayer, UUID sourceStackId, ResourceLocation sourceGunId, ShotVisualReservation reservation) {
        Minecraft minecraft;
        LocalPlayer player;
        GunData currentGunData;
        GunDisplayInstance currentDisplay;
        if (reservation.isCanceled() || (player = (minecraft = Minecraft.getInstance()).player) != sourcePlayer || minecraft.screen != null || player.isSpectator() || player.isDeadOrDying() || !isDualMode(player)) {
            return;
        }
        ItemStack currentStack = player.getOffhandItem();
        UUID currentStackId = DualWieldStackId.get(currentStack);
        boolean sameStack = sourceStackId != null && sourceStackId.equals(currentStackId);
        IGun currentGun = IGun.getIGunOrNull(currentStack);
        if (!sameStack || currentGun == null || !sourceGunId.equals(currentGun.getGunId(currentStack)) || (currentGunData = (GunData) TimelessAPI.getClientGunIndex(sourceGunId).map((v0) -> {
            return v0.getGunData();
        }).orElse(null)) == null || (currentDisplay = OffhandDisplayManager.getOrCreate(currentStack)) == null || isOffhandShotVisualBlocked(OffhandDisplayManager.getClientState())) {
            return;
        }
        playOffhandShotVisual(player, currentStack, currentDisplay, currentGunData);
    }

    private static void reloadOffhand(LocalPlayer player) {
        GunData data;
        float tacticalTime;
        ItemStack stack = player.getOffhandItem();
        Item abstractGunItemM_41720_ = stack.getItem();
        if (abstractGunItemM_41720_ instanceof AbstractGunItem) {
            AbstractGunItem gun = (AbstractGunItem) abstractGunItemM_41720_;
            if (gun.useInventoryAmmo(stack)) {
                return;
            }
            ClientOffhandState state = OffhandDisplayManager.getClientState();
            if (!state.isDrawing() && !state.isReloading() && !state.isBolting() && !DualReloadAnimationManager.isHandActive(InteractionHand.OFF_HAND) && state.getShootCoolDown(player, stack) <= 0) {
                if (IGunOperator.fromLivingEntity(player).needCheckAmmo() && !gun.canReload(player, stack)) {
                    return;
                }
                ClientGunIndex index = (ClientGunIndex) TimelessAPI.getClientGunIndex(gun.getGunId(stack)).orElse(null);
                GunDisplayInstance display = OffhandDisplayManager.getOrCreate(stack);
                if (index == null || (data = index.getGunData()) == null) {
                    return;
                }
                int ammoCount = gun.getCurrentAmmoCount(stack);
                if (data.getBolt() != Bolt.OPEN_BOLT && gun.hasBulletInBarrel(stack)) {
                    ammoCount++;
                }
                boolean empty = ammoCount <= 0;
                float feedTime;
                if (empty) {
                    feedTime = data.getReloadData().getFeed().getEmptyTime();
                    tacticalTime = feedTime + data.getReloadData().getCooldown().getEmptyTime();
                } else {
                    feedTime = data.getReloadData().getFeed().getTacticalTime();
                    tacticalTime = feedTime + data.getReloadData().getCooldown().getTacticalTime();
                }
                float duration = tacticalTime;
                UUID sourceStackId = DualWieldStackId.get(stack);
                if (sourceStackId == null) {
                    return;
                }
                cancelPendingOffhandShot();
                state.beginReload(stack, sourceStackId, empty, duration);
                NetworkHandler.CHANNEL.sendToServer(new ClientMessageOffhandReload(sourceStackId, state.getReloadRequestId()));
                SoundPlayManager.stopPlayGunSound();
                if (com.ssscript.taczfixes.common.data.TaczFixesDataManager.usesNativeReloadAnimation(stack)) {
                    OffhandDisplayManager.triggerNative("reload");
                } else {
                    DualReloadAnimationManager.beginOffhandReload(player, stack, data, feedTime);
                }
                playOffhandThirdPersonPlayerAnimation(player, stack, AnimationName.RELOAD_UPPER, AnimationName.LIE_RELOAD);
                if (display != null) {
                    DualReloadSoundFilter.playOffhandLegacyReload(player, stack, display, empty);
                }
            }
        }
    }

    static boolean requestOffhandReload(LocalPlayer player) {
        ClientOffhandState state = OffhandDisplayManager.getClientState();
        boolean wasReloading = state.isReloading();
        reloadOffhand(player);
        return !wasReloading && state.isReloading();
    }

    private static void inspectOffhand(LocalPlayer player) {
        GunData data;
        ItemStack stack = player.getOffhandItem();
        IGun gun = IGun.getIGunOrNull(stack);
        GunDisplayInstance display = OffhandDisplayManager.getOrCreate(stack);
        ClientOffhandState state = OffhandDisplayManager.getClientState();
        if (gun == null || display == null || state.isDrawing() || state.isReloading() || state.isBolting() || offhandShotPending || DualReloadAnimationManager.isActive() || (data = (GunData) TimelessAPI.getClientGunIndex(gun.getGunId(stack)).map((v0) -> {
            return v0.getGunData();
        }).orElse(null)) == null) {
            return;
        }
        int ammoCount = gun.getCurrentAmmoCount(stack);
        if (data.getBolt() != Bolt.OPEN_BOLT && gun.hasBulletInBarrel(stack)) {
            ammoCount++;
        }
        boolean empty = ammoCount <= 0;
        SoundPlayManager.stopPlayGunSound();
        SoundPlayManager.playInspectSound(player, display, empty);
        OffhandDisplayManager.triggerFilteredInspect();
    }

    private static void fireSelectOffhand(LocalPlayer player) {
        UUID sourceStackId;
        ItemStack stack = player.getOffhandItem();
        Item abstractGunItemM_41720_ = stack.getItem();
        if (!(abstractGunItemM_41720_ instanceof AbstractGunItem)) {
            return;
        }
        AbstractGunItem gun = (AbstractGunItem) abstractGunItemM_41720_;
        GunDisplayInstance display = OffhandDisplayManager.getOrCreate(stack);
        ClientOffhandState state = OffhandDisplayManager.getClientState();
        if (display == null || state.isDrawing() || state.isReloading() || state.isBolting() || offhandShotPending || state.isFireSelectRequestPending() || (sourceStackId = DualWieldStackId.get(stack)) == null) {
            return;
        }
        int requestId = state.beginFireSelect(sourceStackId);
        gun.fireSelect((ShooterDataHolder) null, stack);
        NetworkHandler.CHANNEL.sendToServer(new ClientMessageOffhandFireSelect(sourceStackId, requestId));
        SoundPlayManager.playFireSelectSound(player, display);
        OffhandDisplayManager.triggerNative("fire_select");
    }

    /* ---------- 双持近战: 两把枪都支持时奇数次主手、偶数次副手 ---------- */

    private enum MeleeKind { MUZZLE, STOCK, PUSH }

    /** 双持近战键处理: 返回 true 表示由副手执行(需取消 TACZ 的主手近战)。 */
    public static boolean handleDualMeleeKey(LocalPlayer player) {
        if (player == null || !isDualMode(player)) {
            return false;
        }
        ItemStack mainStack = player.getMainHandItem();
        ItemStack offhandStack = player.getOffhandItem();
        boolean mainSupported = supportsMelee(mainStack);
        boolean offhandSupported = supportsMelee(offhandStack);
        if (!mainSupported && !offhandSupported) {
            return false;
        }
        boolean mainReady = mainSupported && isMainHandMeleeReady(player);
        boolean offhandReady = offhandSupported && isOffhandMeleeReady(player);
        if (!mainReady && !offhandReady) {
            return false;
        }
        // 上次是主手 → 优先副手(不能则主手); 上次是副手(或尚未近战) → 优先主手(不能则副手)。
        boolean useOffhand = lastMeleeWasOffhand ? !mainReady : offhandReady;
        if (!useOffhand) {
            if (mainReady) {
                lastMeleeWasOffhand = false;
            }
            return false;
        }
        if (meleeOffhand(player)) {
            lastMeleeWasOffhand = true;
        }
        return true;
    }

    /** 主手是否可以近战: 副手近战冷却(未到切换比例)或主手正在换弹/切枪/拉栓/开火/状态锁定时不可用。 */
    private static boolean isMainHandMeleeReady(LocalPlayer player) {
        if (offhandMeleeBlocksMainHand()) {
            return false;
        }
        IGunOperator operator = IGunOperator.fromLivingEntity(player);
        if (operator.getSynMeleeCoolDown() > 0
                || operator.getSynReloadState().getStateType().isReloading()
                || operator.getSynDrawCoolDown() != 0
                || operator.getSynIsBolting()
                || operator.getSynShootCoolDown() > 0) {
            return false;
        }
        return !IClientPlayerGunOperator.fromLocalPlayer(player).getDataHolder().clientStateLock;
    }

    /** 副手是否可以近战: 主手近战冷却(未到切换比例)、副手自身近战冷却中或副手正在切枪/换弹/拉栓/开火/连发视觉中不可用。 */
    private static boolean isOffhandMeleeReady(LocalPlayer player) {
        // 单手瞄准(主手)期间副手降下到低位, 不允许副手近战
        if (DualFocusAimState.isPoseVisible()) {
            return false;
        }
        if (mainHandMeleeBlocksOffhand(player) || isOffhandMeleeCoolingDown()) {
            return false;
        }
        ItemStack stack = player.getOffhandItem();
        ClientOffhandState state = OffhandDisplayManager.getClientState();
        if (state.isDrawing() || state.isReloading() || state.isBolting()
                || state.isManualShotAwaitingChamberSync() || state.isServerManualActionBoltReady()
                || offhandShotPending || DualReloadAnimationManager.isActive()
                || state.getShootCoolDown(player, stack) > 0) {
            return false;
        }
        return true;
    }

    private static boolean meleeOffhand(LocalPlayer player) {
        ItemStack stack = player.getOffhandItem();
        IGun gun = IGun.getIGunOrNull(stack);
        GunDisplayInstance display = OffhandDisplayManager.getOrCreate(stack);
        ClientOffhandState state = OffhandDisplayManager.getClientState();
        if (gun == null || display == null || state.isDrawing() || state.isReloading() || state.isBolting()
                || offhandShotPending || DualReloadAnimationManager.isActive()) {
            return false;
        }
        if (mainHandMeleeBlocksOffhand(player)) {
            return false;
        }
        com.ssscript.taczfixes.common.data.GunTaczFixesData.AimingStaminaConfig staminaCfg =
                com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager.resolveAimingStamina(stack);
        float meleeCost = staminaCfg.melee_cost.floatValue();
        if (meleeCost > 0.0f && com.ssscript.taczfixes.client.util.AimingStaminaClientState.isInsufficient(meleeCost)) {
            return false;
        }
        ClientGunIndex gunIndex = (ClientGunIndex) TimelessAPI.getClientGunIndex(gun.getGunId(stack)).orElse(null);
        GunData gunData = gunIndex == null ? null : gunIndex.getGunData();
        MeleeKind kind = resolveMeleeKind(stack, gun, gunData);
        if (kind == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        long cooldownMillis = meleeCooldownMillis(stack, gun, gunData, kind);
        if (now - lastOffhandMeleeTimestamp < cooldownMillis) {
            return false;
        }
        GunMeleeEvent event = new GunMeleeEvent(player, stack, LogicalSide.CLIENT);
        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event)) {
            return false;
        }
        lastOffhandMeleeTimestamp = now;
        offhandMeleeEndTimestamp = now + cooldownMillis;
        NetworkHandler.CHANNEL.sendToServer(new ClientMessageOffhandMelee(DualWieldStackId.getOrCreate(stack)));
        switch (kind) {
            case MUZZLE -> {
                SoundPlayManager.playMeleeBayonetSound(player, display);
                OffhandDisplayManager.triggerNative("bayonet_muzzle");
            }
            case STOCK -> {
                SoundPlayManager.playMeleeStockSound(player, display);
                OffhandDisplayManager.triggerNative("bayonet_stock");
            }
            case PUSH -> {
                SoundPlayManager.playMeleePushSound(player, display);
                OffhandDisplayManager.triggerNative("bayonet_push");
            }
        }
        // TaCZ 的第三人称近战动画只由主手的 GunMeleeEvent 驱动, 副手补播一次
        String meleeAnimation = switch (player.getRandom().nextInt(3)) {
            case 0 -> AnimationName.MELEE_UPPER;
            case 1 -> AnimationName.MELEE_2_UPPER;
            default -> AnimationName.MELEE_3_UPPER;
        };
        playOffhandThirdPersonPlayerAnimation(player, stack, meleeAnimation, meleeAnimation);
        return true;
    }

    /** 副手近战冷却中(此时副手自身也不能再次近战)。 */
    public static boolean isOffhandMeleeCoolingDown() {
        return System.currentTimeMillis() < offhandMeleeEndTimestamp;
    }

    /** 主手近战冷却是否仍阻挡副手(冷却进行到配置比例后不再阻挡)。 */
    private static boolean mainHandMeleeBlocksOffhand(LocalPlayer player) {
        long remaining = IGunOperator.fromLivingEntity(player).getSynMeleeCoolDown();
        long total = com.ssscript.taczfixes.common.util.MeleeCooldownHelper
                .totalCooldownMillis(player.getMainHandItem());
        return meleeStillBlocking(remaining, total);
    }

    /** 副手近战冷却是否仍阻挡主手(冷却进行到配置比例后不再阻挡)。 */
    public static boolean offhandMeleeBlocksMainHand() {
        long now = System.currentTimeMillis();
        long remaining = offhandMeleeEndTimestamp - now;
        long total = offhandMeleeEndTimestamp - lastOffhandMeleeTimestamp;
        return meleeStillBlocking(remaining, total);
    }

    private static boolean meleeStillBlocking(long remaining, long total) {
        if (remaining <= 0L) {
            return false;
        }
        if (total <= 0L) {
            return true;
        }
        double switchPercent = com.ssscript.taczfixes.common.config.Config.DUAL_WIELD_MELEE_SWITCH_PERCENT.get();
        return remaining > total * (1.0d - switchPercent);
    }

    /** 枪械是否支持近战: 枪口/枪托配件带近战数据, 或枪械 data 有近战默认数据。 */
    private static boolean supportsMelee(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return false;
        }
        if (attachmentMeleeData(gun.getAttachmentId(stack, AttachmentType.MUZZLE)) != null
                || attachmentMeleeData(gun.getAttachmentId(stack, AttachmentType.STOCK)) != null) {
            return true;
        }
        GunData data = clientGunData(gun, stack);
        return data != null && data.getMeleeData() != null && data.getMeleeData().getDefaultMeleeData() != null;
    }

    private static MeleeKind resolveMeleeKind(ItemStack stack, IGun gun, GunData gunData) {
        if (attachmentMeleeData(gun.getAttachmentId(stack, AttachmentType.MUZZLE)) != null) {
            return MeleeKind.MUZZLE;
        }
        if (attachmentMeleeData(gun.getAttachmentId(stack, AttachmentType.STOCK)) != null) {
            return MeleeKind.STOCK;
        }
        GunMeleeData meleeData = gunData == null ? null : gunData.getMeleeData();
        GunDefaultMeleeData defaultMelee = meleeData == null ? null : meleeData.getDefaultMeleeData();
        if (defaultMelee == null) {
            return null;
        }
        return "melee_stock".equals(defaultMelee.getAnimationType()) ? MeleeKind.STOCK : MeleeKind.PUSH;
    }

    /** 近战总冷却 = 枪械近战冷却 + 对应近战数据冷却。 */
    private static long meleeCooldownMillis(ItemStack stack, IGun gun, GunData gunData, MeleeKind kind) {
        GunMeleeData meleeData = gunData == null ? null : gunData.getMeleeData();
        float seconds = meleeData == null ? 0.0f : meleeData.getCooldown();
        if (kind == MeleeKind.MUZZLE) {
            seconds += attachmentMeleeCooldown(stack, gun, AttachmentType.MUZZLE);
        } else if (kind == MeleeKind.STOCK) {
            seconds += attachmentMeleeCooldown(stack, gun, AttachmentType.STOCK);
        } else if (meleeData != null && meleeData.getDefaultMeleeData() != null) {
            seconds += meleeData.getDefaultMeleeData().getCooldown();
        }
        return Math.max(0L, (long) (seconds * 1000.0f));
    }

    private static float attachmentMeleeCooldown(ItemStack stack, IGun gun, AttachmentType type) {
        com.tacz.guns.resource.pojo.data.attachment.MeleeData data =
                attachmentMeleeData(gun.getAttachmentId(stack, type));
        return data == null ? 0.0f : data.getCooldown();
    }

    private static com.tacz.guns.resource.pojo.data.attachment.MeleeData attachmentMeleeData(ResourceLocation attachmentId) {
        if (attachmentId == null || com.tacz.guns.api.DefaultAssets.isEmptyAttachmentId(attachmentId)) {
            return null;
        }
        return TimelessAPI.getClientAttachmentIndex(attachmentId)
                .map(index -> index.getData().getMeleeData())
                .orElse(null);
    }

    private static GunData clientGunData(IGun gun, ItemStack stack) {
        return (GunData) TimelessAPI.getClientGunIndex(gun.getGunId(stack))
                .map(index -> index.getGunData())
                .orElse(null);
    }

    private static void playShootSound(LocalPlayer player, ItemStack stack, GunDisplayInstance display, GunData data) {
        AttachmentCacheProperty cache = new AttachmentCacheProperty();
        cache.eval(stack, data);
        Pair<Integer, Boolean> silence = (Pair) cache.getCache(SilenceModifier.ID);
        if (silence != null && ((Boolean) silence.right()).booleanValue()) {
            SoundPlayManager.playSilenceSound(player, display, data);
        } else {
            SoundPlayManager.playShootSound(player, display, data);
        }
    }

    private record ShotRequestKey(UUID stackId, long shootTimestamp) {

        private ShotRequestKey(UUID stackId, long shootTimestamp) {
            this.stackId = stackId;
            this.shootTimestamp = shootTimestamp;
        }

        public UUID stackId() {
            return this.stackId;
        }

        public long shootTimestamp() {
            return this.shootTimestamp;
        }
    }

    private static final class ShotVisualReservation {
        private final ShotRequestKey key;
        private final AtomicBoolean canceled = new AtomicBoolean();
        private final AtomicInteger remainingTasks = new AtomicInteger(1);

        private ShotVisualReservation(ShotRequestKey key) {
            this.key = key;
        }

        private ShotRequestKey key() {
            return this.key;
        }

        private boolean isCanceled() {
            return this.canceled.get();
        }

        private void cancel() {
            this.canceled.set(true);
        }

        private void addTasks(int count) {
            if (count > 0) {
                this.remainingTasks.addAndGet(count);
            }
        }

        private int completeTask() {
            return this.remainingTasks.updateAndGet(value -> {
                if (value > 0) {
                    return value - 1;
                }
                return 0;
            });
        }
    }
}
