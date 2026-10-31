package com.ssscript.taczfixes.client.client;

import com.ssscript.taczfixes.client.client.DynamicCrosshair;
import com.ssscript.taczfixes.client.render.ClientGunLightManager;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
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
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.ssscript.taczfixes.common.util.DualWieldStackId;
import com.ssscript.taczfixes.common.util.OffhandItemCombat;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.common.network.ClientMessageOffhandBolt;
import com.ssscript.taczfixes.common.network.ClientMessageOffhandCancelReload;
import com.ssscript.taczfixes.common.network.ClientMessageOffhandFireSelect;
import com.ssscript.taczfixes.common.network.ClientMessageOffhandItemAttack;
import com.ssscript.taczfixes.common.network.ClientMessageOffhandMelee;
import com.ssscript.taczfixes.common.network.ClientMessageOffhandReload;
import com.ssscript.taczfixes.common.network.ClientMessageOffhandShoot;
import com.ssscript.taczfixes.common.network.ClientMessageOffhandShieldState;
import com.ssscript.taczfixes.common.network.ClientMessageOffhandUseState;
import com.ssscript.taczfixes.client.mixin.MixinMinecraftStartUseInvoker;
import com.ssscript.taczfixes.client.mixin.MixinItemInHandRendererHeightAccessor;

import it.unimi.dsi.fastutil.Pair;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.tacz.guns.compat.playeranimator.AnimationName;
import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;
import com.tacz.guns.compat.playeranimator.animation.AnimationManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.Mod;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShieldItem;

@net.neoforged.fml.common.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = {Dist.CLIENT})
public final class DualWieldClient {
    private static boolean lastLeftDown;
    private static boolean lastOffhandItemUseDown;
    private static boolean lastOffhandUseActive;
    private static boolean lastOffhandShieldRaised;
    private static boolean lastMainItemUseDown;
    private static boolean mainItemUseRepeat;
    private static int mainItemUseCooldown;
    private static long lastOffhandItemAttackMs;

    private static boolean lastRightDown;
    private static boolean lastLeftShootSuccess;
    private static boolean lastRightShootSuccess;
    private static boolean controllerShootDown;
    private static boolean controllerAimDown;
    private static boolean waitShootReleaseAfterFocusChange;
    private static boolean waitAimReleaseAfterFocusChange;
    private static boolean focusAimToggle;
    private static boolean lastFocusAimPhysicalDown;
    private static boolean lastFocusRequested;
    private static boolean focusInputChangedThisTick;
    private static boolean waitLeftReleaseAfterScreen;
    private static boolean waitRightReleaseAfterScreen;
    private static boolean dualWasActive;
    private static volatile boolean offhandShotPending;
    private static volatile long offhandShotToken;
    private static float offhandSprintTimeS;
    private static long offhandSprintTimestamp = -1L;
    private static UUID offhandSprintStackId;
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

    static boolean isOffhandShotPending() {
        return offhandShotPending;
    }

    public static boolean isOffhandGunMode(LocalPlayer player) {
        return player != null
                && IGun.getIGunOrNull(player.getOffhandItem()) != null
                && !DualWieldEligibility.isForbiddenDualItem(player.getMainHandItem());
    }

    /**
     * 是否处于双持状态。
     *
     * <p>统一委托 {@link DualWieldEligibility#isDualWielding(net.minecraft.world.entity.LivingEntity)}，
     * 由它统一处理禁入物品、deniedGunIds、allowedGunIds、allowedGunTypes、
     * allowOtherGunTypes，以及"主手枪 + 副手物品"等组合判定。这样可以在客户端与服务端
     * 使用同一份规则，避免出现"服务器拒绝但客户端仍渲染/开火"的不一致。
     */
    public static boolean isDualMode(LocalPlayer player) {
        if (player == null || player.isSpectator() || player.isDeadOrDying()) {
            return false;
        }
        return DualWieldEligibility.isDualWielding(player);
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
        NetworkHandler.CHANNEL.sendToServer(
                new com.ssscript.taczfixes.common.network.ClientMessageOffhandShootState(down));
    }

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (false) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        OffhandCameraController.applyPendingRecoil(player);
        boolean dual = isDualMode(player);
        if (minecraft.screen != null && !(dual && minecraft.mouseHandler.isMouseGrabbed()) && dualWasActive && !OffhandDisplayManager.isPuttingAway() && player != null && !player.isSpectator() && !player.isDeadOrDying()) {
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
            if (lastOffhandShieldRaised) {
                lastOffhandShieldRaised = false;
                NetworkHandler.CHANNEL.sendToServer(new ClientMessageOffhandShieldState(false));
            }
            if (lastOffhandUseActive) {
                lastOffhandUseActive = false;
                player.stopUsingItem();
                NetworkHandler.CHANNEL.sendToServer(new ClientMessageOffhandUseState(false));
            }
            boolean dualEnded = dualWasActive;
            dualWasActive = false;
            DualWieldMelee.reset();
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
                    OffhandDisplayManager.beginPutAway(player, lastDualMainStack, lastDualOffhandStack, player.getMainHandItem(), player.getOffhandItem(), false, false);
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
            if (IGun.getIGunOrNull(player.getOffhandItem()) == null) {
                OffhandDisplayManager.clear();
            } else {
                OffhandDisplayManager.cancelPutAway(player, player.getMainHandItem(), player.getOffhandItem());
            }
            resetFocusAimImmediately(player);
            MainhandMovementAnimation.resetMovementBoundary(player.getMainHandItem());
        }
        dualWasActive = true;
        ItemStack currentMainStack = player.getMainHandItem();
        ItemStack currentOffhandStack = player.getOffhandItem();
        DualWieldStackId.getOrCreate(currentMainStack);
        DualWieldStackId.getOrCreate(currentOffhandStack);
        boolean currentOffhandGun = IGun.getIGunOrNull(currentOffhandStack) != null;
        boolean stackPairChanged = (dualStarted || (sameLogicalStack(lastDualMainStack, currentMainStack) && sameLogicalStack(lastDualOffhandStack, currentOffhandStack))) ? false : true;
        if (stackPairChanged && currentOffhandGun && !OffhandDisplayManager.isPuttingAway()) {
            DualWieldMelee.reset();
            DualReloadAnimationManager.clear();
            MainhandMovementAnimation.resetMovementBoundary(lastDualMainStack);
            cancelPendingOffhandShot();
            waitLeftReleaseAfterScreen = ShootKey.SHOOT_KEY.isDown() || controllerShootDown;
            waitRightReleaseAfterScreen = AimKey.AIM_KEY.isDown() || controllerAimDown;
            resetFocusAimImmediately(player);
            boolean handSwap = taczfixes$isHandSwap(lastDualMainStack, lastDualOffhandStack, currentMainStack, currentOffhandStack);
            if (handSwap) {
                OffhandDisplayManager.beginPutAway(player, lastDualMainStack, lastDualOffhandStack,
                        currentMainStack, currentOffhandStack, true, true);
            } else {
                OffhandDisplayManager.beginPutAway(player, lastDualMainStack, lastDualOffhandStack,
                        currentMainStack, currentOffhandStack, true, false);
            }
        }
        if (currentOffhandGun && OffhandDisplayManager.isPuttingAway()) {
            OffhandDisplayManager.updatePutAwayTarget(currentMainStack, currentOffhandStack, true);
            OffhandDisplayManager.tickPutAway(player);
            lastDualMainStack = currentMainStack.copy();
            lastDualOffhandStack = currentOffhandStack.copy();
            if (currentOffhandGun && OffhandDisplayManager.isPuttingAway()) {
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
        if (minecraft.screen != null && !(dual && minecraft.mouseHandler.isMouseGrabbed())) {
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
            if (lastOffhandUseActive) {
                lastOffhandUseActive = false;
                player.stopUsingItem();
                NetworkHandler.CHANNEL.sendToServer(new ClientMessageOffhandUseState(false));
            }
            if (lastOffhandShieldRaised) {
                lastOffhandShieldRaised = false;
                NetworkHandler.CHANNEL.sendToServer(new ClientMessageOffhandShieldState(false));
            }
            return;
        }
        boolean physicalShootDown = ShootKey.SHOOT_KEY.isDown() || controllerShootDown;
        boolean physicalAimDown = AimKey.AIM_KEY.isDown() || controllerAimDown;
        boolean physicalLeftDown = Minecraft.getInstance().mouseHandler.isLeftPressed();
        waitLeftReleaseAfterScreen = false;
        waitRightReleaseAfterScreen = false;
        ItemStack leftGun = player.getOffhandItem();
        ItemStack rightGun = player.getMainHandItem();
        boolean hasOffhandGun = IGun.getIGunOrNull(leftGun) != null;
        boolean hasMainGun = IGun.getIGunOrNull(rightGun) != null;
        boolean mainGunWithItem = hasMainGun && !hasOffhandGun;
        if (hasOffhandGun) {
            updateOffhandSprint(player, leftGun);
        } else {
            offhandSprintTimeS = 0.0f;
            offhandSprintTimestamp = -1L;
            offhandSprintStackId = null;
        }
        if (!DualFocusAimKey.FOCUS_AIM_KEY.isDown() && hasMainGun && !hasOffhandGun
                && !leftGun.isEmpty() && !(leftGun.getItem() instanceof ShieldItem)) {
            if (physicalLeftDown && !lastOffhandItemUseDown
                    && (Minecraft.getInstance().gameMode == null || !Minecraft.getInstance().gameMode.isDestroying())) {
                if (isAttackOnly(leftGun, player)) {
                    useItemOrAttack(player, InteractionHand.OFF_HAND, "tick-offhand");
                } else {
                    startOffhandUse(player);
                }
            }
            lastOffhandItemUseDown = physicalLeftDown;
        } else {
            lastOffhandItemUseDown = false;
        }
        if (hasOffhandGun && !hasMainGun) {
            if (Minecraft.getInstance().mouseHandler.isRightPressed()
                    && !InteractKey.INTERACT_KEY.isDown() && !DualFocusAimKey.FOCUS_AIM_KEY.isDown()) {
                if (mainItemUseCooldown > 0) {
                    mainItemUseCooldown--;
                } else if (lastMainItemUseDown && mainItemUseRepeat && !player.isUsingItem()) {
                    mainItemUseRepeat = useMainItemOrAttack(player);
                    mainItemUseCooldown = mainItemUseRepeat ? 3 : 0;
                }
            } else {
                lastMainItemUseDown = false;
                mainItemUseRepeat = false;
                mainItemUseCooldown = 0;
            }
        } else {
            lastMainItemUseDown = false;
            mainItemUseRepeat = false;
            mainItemUseCooldown = 0;
        }
        updateThirdPersonGunAnimations(minecraft, leftGun, rightGun);
        if (hasOffhandGun) {
            leftState.tickManualShotChamberSync(leftGun);
            leftState.tickBoltCompletion(player, leftGun);
        }
        OffhandDisplayManager.synchronizeManualActionCaptureState();
        boolean focusRequested = DualFocusAimKey.FOCUS_AIM_KEY.isDown() && !InteractKey.INTERACT_KEY.isDown();
        focusInputChangedThisTick = focusRequested != lastFocusRequested;
        lastFocusRequested = focusRequested;
        boolean focusTarget = focusRequested && hasMainGun && canUseFocusAim(player, operator, leftState, leftGun, rightGun, DualFocusAimState.isActive());
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
        boolean focusAimLatch = updateFocusAimLatch(focusTarget, physicalAimDown);
        boolean desiredAim = hasMainGun && focusTarget && (mainGunWithItem ? physicalAimDown : focusAimLatch);
        if (hasMainGun && operator.isAim() != desiredAim) {
            operator.aim(desiredAim);
            if (!desiredAim) {
                DualFocusAimState.releasePreservedMainAimAfterInputExit();
            }
        }
        boolean leftDown = hasOffhandGun && !focusTarget && physicalShootDown;
        boolean rightDown = hasMainGun && (focusTarget ? physicalShootDown : physicalAimDown);
        boolean offhandShield = hasMainGun && !hasOffhandGun && leftGun.getItem() instanceof ShieldItem;
        boolean shieldRaised = offhandShield && !focusTarget && physicalLeftDown
                && !player.getCooldowns().isOnCooldown(Items.SHIELD);
        if (shieldRaised != lastOffhandShieldRaised) {
            lastOffhandShieldRaised = shieldRaised;
            if (shieldRaised) {
                startOffhandUse(player);
            } else if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.OFF_HAND
                    && player.getUseItem().getItem() instanceof ShieldItem) {
                player.stopUsingItem();
            }
            NetworkHandler.CHANNEL.sendToServer(new ClientMessageOffhandShieldState(shieldRaised));
        }
        boolean offhandItemUse = hasMainGun && !hasOffhandGun && !leftGun.isEmpty()
                && !(leftGun.getItem() instanceof ShieldItem)
                && !focusRequested && physicalLeftDown;
        if (offhandItemUse != lastOffhandUseActive) {
            lastOffhandUseActive = offhandItemUse;
            if (!offhandItemUse && player.isUsingItem()
                    && player.getUsedItemHand() == InteractionHand.OFF_HAND) {
                player.stopUsingItem();
            }
            NetworkHandler.CHANNEL.sendToServer(new ClientMessageOffhandUseState(offhandItemUse));
        }
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
        if (hasOffhandGun && !focusTarget) {
            tryStartOffhandBolt(player, leftGun, leftState);
        }
        boolean cancelingLeftReload = hasOffhandGun && requestOffhandReloadCancel(leftGun, leftState, leftDown);
        boolean leftContinuous = hasOffhandGun && canContinuouslyShoot(leftGun);
        boolean leftShouldCharge = hasOffhandGun && !cancelingLeftReload && leftDown && (leftContinuous || !lastLeftShootSuccess);
        boolean leftChargeReady = hasOffhandGun && !cancelingLeftReload && leftState.chargeShoot(player, leftGun, leftShouldCharge);
        if (cancelingLeftReload) {
            leftState.resetCharge();
        }
        if (hasOffhandGun && leftChargeReady && ((leftContinuous || !lastLeftShootSuccess) && shootOffhand(player, leftGun, leftState.getChargeProgress()))) {
            lastLeftShootSuccess = true;
        }
        boolean rightContinuous = hasMainGun && canContinuouslyShoot(rightGun);
        boolean rightShouldCharge = hasMainGun && rightDown && (rightContinuous || !lastRightShootSuccess);
        boolean rightChargeReady = hasMainGun && operator.chargeShoot(rightShouldCharge);
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
        if (IGun.getIGunOrNull(stack) == null) {
            return false;
        }
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

    private static void applyOffhandItemBob(PoseStack poseStack, LocalPlayer player, float partialTick) {
        float delta = player.walkDist - player.walkDistO;
        float walk = -(player.walkDist + delta * partialTick);
        float bob = Mth.lerp(partialTick, player.oBob, player.bob);
        float phase = walk * (float) Math.PI;
        poseStack.translate(Mth.sin(phase) * bob * 0.5f, -Math.abs(Mth.cos(phase) * bob), 0.0f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(phase) * bob * 3.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(Math.abs(Mth.cos(phase - 0.2f) * bob) * 5.0f));
    }

    private static void applyInverseVanillaBob(PoseStack poseStack, LocalPlayer player, float partialTick) {
        float delta = player.walkDist - player.walkDistO;
        float walk = -(player.walkDist + delta * partialTick);
        float bob = Mth.lerp(partialTick, player.oBob, player.bob);
        float phase = walk * (float) Math.PI;
        float x = Mth.sin(phase) * bob * 0.5f;
        float y = -Math.abs(Mth.cos(phase) * bob);
        poseStack.mulPose(Axis.XP.rotationDegrees(-Math.abs(Mth.cos(phase - 0.2f) * bob) * 5.0f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-Mth.sin(phase) * bob * 3.0f));
        poseStack.translate(-x, -y, 0.0d);
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
        if (!(renderStack.getItem() instanceof IGun)) {
            applyOffhandItemBob(event.getPoseStack(), player, event.getPartialTick());
            event.setCanceled(false);
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        boolean cancelOriginalBob = IGun.getIGunOrNull(player.getMainHandItem()) == null;
        poseStack.pushPose();
        if (cancelOriginalBob) {
            applyInverseVanillaBob(poseStack, player, event.getPartialTick());
        }
        boolean rendered;
        try {
            rendered = DualFirstPersonRenderer.renderOffhand(player, renderStack, poseStack, event.getMultiBufferSource(), event.getPackedLight(), event.getPartialTick());
        } finally {
            poseStack.popPose();
        }
        if (rendered) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void taczfixes$dualItemMouse(InputEvent.MouseButton.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !isDualMode(player) || InteractKey.INTERACT_KEY.isDown()
                || DualFocusAimKey.FOCUS_AIM_KEY.isDown()
                || !Minecraft.getInstance().mouseHandler.isMouseGrabbed()) {
            return;
        }
        if (event.getAction() == 0) {
            if (event.getButton() == 0) {
                lastOffhandItemUseDown = false;
            } else if (event.getButton() == 1) {
                lastMainItemUseDown = false;
            }
            return;
        }
        if (event.getAction() != 1) {
            return;
        }
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        boolean mainGun = IGun.getIGunOrNull(main) != null;
        boolean offGun = IGun.getIGunOrNull(off) != null;
        if (event.getButton() == 1 && offGun && !mainGun) {
            lastMainItemUseDown = true;
            if (isAttackOnly(main, player)) {
                mainItemUseRepeat = false;
                mainItemUseCooldown = 0;
                attackMainHand(player);
            } else {
                mainItemUseRepeat = useMainItemOrAttack(player);
                mainItemUseCooldown = mainItemUseRepeat ? 4 : 0;
            }
            return;
        }
        if (event.getButton() == 0 && mainGun && !offGun && !off.isEmpty() && !lastOffhandItemUseDown) {
            lastOffhandItemUseDown = true;
            if (isAttackOnly(off, player)) {
                useItemOrAttack(player, InteractionHand.OFF_HAND, "mouse-offhand");
            } else {
                startOffhandUse(player);
            }
        }
    }

    private static ItemInHandRenderer offhandRenderer;

    public static void setOffhandRenderer(ItemInHandRenderer renderer) {
        offhandRenderer = renderer;
    }

    private static void primeOffhandShieldRaise() {
        if (offhandRenderer != null) {
            MixinItemInHandRendererHeightAccessor accessor = (MixinItemInHandRendererHeightAccessor) offhandRenderer;
            accessor.taczfixes$setOOffHandHeight(0.0F);
            accessor.taczfixes$setOffHandHeight(0.0F);
        }
    }

    public static boolean shouldKeepOffhandUse(LocalPlayer player) {
        if (player == null || !player.isUsingItem()
                || player.getUsedItemHand() != InteractionHand.OFF_HAND
                || !isDualMode(player)) {
            return false;
        }
        if (IGun.getIGunOrNull(player.getMainHandItem()) == null
                || IGun.getIGunOrNull(player.getOffhandItem()) != null
                || player.getOffhandItem().isEmpty()
                || DualFocusAimKey.FOCUS_AIM_KEY.isDown()) {
            return false;
        }
        if (player.getOffhandItem().is(Items.SHIELD)
                && player.getCooldowns().isOnCooldown(Items.SHIELD)) {
            return false;
        }
        if ((!ShootKey.SHOOT_KEY.isDown() && !Minecraft.getInstance().mouseHandler.isLeftPressed())
                || player.getUseItemRemainingTicks() <= 1) {
            return false;
        }
        return true;
    }

    private static boolean isAttackOnly(ItemStack stack, LocalPlayer player) {
        if (stack.isEmpty() || stack.getUseDuration(player) > 0 || stack.getItem() instanceof net.minecraft.world.item.BlockItem) {
            return false;
        }
        return stack.getItem() instanceof net.minecraft.world.item.SwordItem
                || stack.getItem() instanceof net.minecraft.world.item.DiggerItem
                || isLrMeleeWeapon(stack);
    }

    private static boolean isLrMeleeWeapon(ItemStack stack) {
        try {
            return Class.forName("me.xjqsh.lrtactical.api.item.IMeleeWeapon")
                    .isInstance(stack.getItem());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void attackMainHand(LocalPlayer player) {
        net.minecraft.world.phys.HitResult hit = Minecraft.getInstance().hitResult;
        if (hit instanceof net.minecraft.world.phys.EntityHitResult entityHit) {
            Minecraft.getInstance().gameMode.attack(player, entityHit.getEntity());
        } else {
            player.swing(InteractionHand.MAIN_HAND, true);
        }
    }

    private static boolean useMainItemOrAttack(LocalPlayer player) {
        net.minecraft.client.multiplayer.MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
        if (gameMode == null) {
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return false;
        }
        if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            return true;
        }
        net.minecraft.world.phys.HitResult hit = Minecraft.getInstance().hitResult;
        if (hit instanceof net.minecraft.world.phys.EntityHitResult entityHit) {
            net.minecraft.world.InteractionResult result =
                    gameMode.interactAt(player, entityHit.getEntity(), entityHit, InteractionHand.MAIN_HAND);
            if (!result.consumesAction()) {
                result = gameMode.interact(player, entityHit.getEntity(), InteractionHand.MAIN_HAND);
            }
            if (result.consumesAction()) {
                player.swing(InteractionHand.MAIN_HAND, true);
                return true;
            }
        } else if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit
                && !player.level().getBlockState(blockHit.getBlockPos()).isAir()) {
            if (gameMode.useItemOn(player, InteractionHand.MAIN_HAND, blockHit).consumesAction()) {
                player.swing(InteractionHand.MAIN_HAND, true);
                return true;
            }
        }
        if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem)) {
            net.minecraft.world.InteractionResult result = gameMode.useItem(player, InteractionHand.MAIN_HAND);
            boolean consume = result.consumesAction();
            boolean overridden = !consume && result != net.minecraft.world.InteractionResult.FAIL
                    && stack.getUseDuration(player) > 0;
            if (consume || overridden) {
                if (stack.getUseDuration(player) > 0 && !player.isUsingItem()) {
                    player.startUsingItem(InteractionHand.MAIN_HAND);
                }
                return true;
            }
        }
        attackMainHand(player);
        return false;
    }

    private static void startOffhandUse(LocalPlayer player) {
        ItemStack stack = player.getOffhandItem();
        if (stack.getUseDuration(player) > 0) {
            if (!player.isUsingItem()) {
                if (stack.getItem() instanceof ShieldItem) {
                    primeOffhandShieldRaise();
                }
                player.startUsingItem(InteractionHand.OFF_HAND);
            }
            return;
        }
        useItemOrAttack(player, InteractionHand.OFF_HAND, "offhand-use");
    }

    private static void useItemOrAttack(LocalPlayer player, InteractionHand hand, String source) {
        if (Minecraft.getInstance().gameMode == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (hand == InteractionHand.OFF_HAND && stack.getItem() instanceof net.minecraft.world.item.BlockItem) {
            net.minecraft.world.phys.HitResult hit = Minecraft.getInstance().hitResult;
            if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit
                    && !player.level().getBlockState(blockHit.getBlockPos()).isAir()) {
                Minecraft.getInstance().gameMode.useItemOn(player, hand, blockHit);
                player.swing(hand, true);
                return;
            }
        }
        if (hand == InteractionHand.OFF_HAND && stack.getUseDuration(player) > 0) {
            if (Minecraft.getInstance().getConnection() != null) {
                Minecraft.getInstance().getConnection().send(
                        new net.minecraft.network.protocol.game.ServerboundUseItemPacket(hand, 0, player.getYRot(), player.getXRot()));
            }
            if (!player.isUsingItem()) {
                player.startUsingItem(hand);
            }
            return;
        }
        net.minecraft.world.InteractionResult result = stack.getItem() instanceof net.minecraft.world.item.BlockItem
                ? net.minecraft.world.InteractionResult.FAIL
                : Minecraft.getInstance().gameMode.useItem(player, hand);
        boolean consume = result.consumesAction();
        boolean overridden = !consume && result != net.minecraft.world.InteractionResult.FAIL
                && stack.getUseDuration(player) > 0;
        if (consume || overridden) {
            if (stack.getUseDuration(player) > 0 && !player.isUsingItem()) {
                player.startUsingItem(hand);
            }
            return;
        }
        net.minecraft.world.phys.HitResult hit = Minecraft.getInstance().hitResult;
        if (hit instanceof net.minecraft.world.phys.EntityHitResult entityHit) {
            if (hand == InteractionHand.OFF_HAND) {
                NetworkHandler.CHANNEL.sendToServer(
                        new ClientMessageOffhandItemAttack(entityHit.getEntity().getId()));
                player.swing(InteractionHand.OFF_HAND, true);
            } else {
                Minecraft.getInstance().gameMode.attack(player, entityHit.getEntity());
            }
        } else {
            player.swing(hand);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void blockVanillaInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!isDualMode(player) || InteractKey.INTERACT_KEY.isDown()) {
            return;
        }
        if (Minecraft.getInstance().screen != null && !Minecraft.getInstance().mouseHandler.isMouseGrabbed()) {
            return;
        }
        ItemStack main = player != null ? player.getMainHandItem() : ItemStack.EMPTY;
        ItemStack off = player != null ? player.getOffhandItem() : ItemStack.EMPTY;
        boolean mainGun = IGun.getIGunOrNull(main) != null;
        boolean offGun = IGun.getIGunOrNull(off) != null;
        if (event.isAttack() || (event.isUseItem() && mainGun != offGun)) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
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
            // burst 连发中途入水/被方块阻挡时, 服务器那几发已被 shootOnce 拦截; 客户端预测不会重新检查,
            // 这里取消事件以跳过本发的火光/音效/动画(事件被取消后 TaCZ 的 onGunFire 与音效动画都不会执行)
            if (com.ssscript.taczfixes.common.util.UnderwaterShooting.isBlocked(player, firedStack)
                    || com.ssscript.taczfixes.common.util.GunBlocking.isFireDisabled(player, firedStack)) {
                event.setCanceled(true);
                return;
            }
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
    static void playOffhandThirdPersonPlayerAnimation(LocalPlayer player, ItemStack stack, String upperAnimation, String lieAnimation) {
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
        if (first == null || second == null) {
            return false;
        }
        if (first.isEmpty() || second.isEmpty()) {
            return first.isEmpty() && second.isEmpty();
        }
        UUID firstId = DualWieldStackId.get(first);
        UUID secondId = DualWieldStackId.get(second);
        if (firstId != null && secondId != null) {
            return firstId.equals(secondId);
        }
        IGun firstGun = IGun.getIGunOrNull(first);
        IGun secondGun = IGun.getIGunOrNull(second);
        if (firstGun != null || secondGun != null) {
            return firstGun != null && secondGun != null && first.getItem() == second.getItem()
                    && firstGun.getGunId(first).equals(secondGun.getGunId(second));
        }
        return ItemStack.isSameItemSameComponents(first, second);
    }

    /** 是否为主副手互换(上一帧的主/副手正好是这一帧的副/主手)。 */
    private static boolean taczfixes$isHandSwap(ItemStack previousMain, ItemStack previousOffhand,
                                                ItemStack currentMain, ItemStack currentOffhand) {
        return sameSwapStack(previousMain, currentOffhand)
                && sameSwapStack(previousOffhand, currentMain)
                && !sameSwapStack(previousMain, previousOffhand);
    }

    private static boolean sameSwapStack(ItemStack first, ItemStack second) {
        if (first == null || second == null) {
            return false;
        }
        if (first.isEmpty() || second.isEmpty()) {
            return first.isEmpty() && second.isEmpty();
        }
        UUID firstId = DualWieldStackId.get(first);
        UUID secondId = DualWieldStackId.get(second);
        if (firstId != null && secondId != null) {
            return firstId.equals(secondId);
        }
        return ItemStack.isSameItemSameComponents(first, second);
    }

    private static void updateOffhandSprint(LocalPlayer player, ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        GunData gunData = gun == null ? null
                : (GunData) TimelessAPI.getClientGunIndex(gun.getGunId(stack))
                        .map(index -> index.getGunData()).orElse(null);
        UUID stackId = DualWieldStackId.get(stack);
        long now = System.currentTimeMillis();
        if (gunData == null || stackId == null || !stackId.equals(offhandSprintStackId)) {
            offhandSprintTimeS = 0.0f;
            offhandSprintTimestamp = now;
            offhandSprintStackId = stackId;
            return;
        }
        if (offhandSprintTimestamp < 0L || now < offhandSprintTimestamp) {
            offhandSprintTimestamp = now;
        }
        float elapsed = (now - offhandSprintTimestamp) / 1000.0f;
        offhandSprintTimestamp = now;
        float maxSprintTime = Math.max(0.0f, gunData.getSprintTime());
        if (player.isSprinting() && !player.isVisuallySwimming()) {
            offhandSprintTimeS = Math.min(maxSprintTime, offhandSprintTimeS + elapsed);
        } else {
            offhandSprintTimeS = Math.max(0.0f, offhandSprintTimeS - elapsed);
        }
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
        if (state.isDrawing() || state.isReloading() || state.isBolting() || state.isManualShotAwaitingChamberSync() || state.isServerManualActionBoltReady() || !state.canBeginBoltCycle() || coolDown >= 50 || isGlobalShootInputBlocked(player) || IGunOperator.fromLivingEntity(player).getSynSprintTime() > 0.0f || offhandSprintTimeS > 0.0f || (gunData = gunIndex.getGunData()) == null) {
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
        // 耐久为 0: disable 播放 dry_fire, remove 静默拦截(消失由服务端同步)
        com.ssscript.taczfixes.common.data.GunTaczFixesData.DurabilityConfig durabilityCfg =
                com.ssscript.taczfixes.common.util.DurabilityStorage.config(stack);
        if (durabilityCfg != null
                && com.ssscript.taczfixes.common.util.DurabilityStorage.getMax(stack) > 0
                && com.ssscript.taczfixes.common.util.DurabilityStorage.get(stack) <= 0) {
            String durabilityAction = com.ssscript.taczfixes.common.util.DurabilityStorage.damageAction(durabilityCfg);
            if (!"none".equals(durabilityAction)) {
                if (!"remove".equals(durabilityAction)) {
                    SoundPlayManager.playDryFireSound(player, display);
                }
                return false;
            }
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
        return com.tacz.guns.util.time.GunTime.nowMillis() - LocalPlayerDataHolder.clientClickButtonTimestamp < 50;
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
        ClientGunLightManager.add(pos, light.fire);
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
        // burst 连发中途入水/被方块阻挡: 服务器那几发已被 shootOnce 拦截, 这里同步取消客户端剩余连发的火光/音效/动画
        LocalPlayer currentPlayer = Minecraft.getInstance().player;
        ItemStack liveOffhand = currentPlayer == null ? ItemStack.EMPTY : currentPlayer.getOffhandItem();
        if (currentPlayer == null
                || com.ssscript.taczfixes.common.util.UnderwaterShooting.isBlocked(currentPlayer, liveOffhand)
                || com.ssscript.taczfixes.common.util.GunBlocking.isFireDisabled(currentPlayer, liveOffhand)) {
            cancelShotVisualReservation(reservation);
            return;
        }
        OffhandCameraController.recordShot(sourcePlayer, recoilStack, recoilGunData);
        DynamicCrosshair.onShot(sourcePlayer, recoilStack);
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