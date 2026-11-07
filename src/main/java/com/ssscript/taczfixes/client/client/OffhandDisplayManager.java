package com.ssscript.taczfixes.client.client;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.client.animation.statemachine.LuaStateMachineFactory;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.vmlib.LuaAnimationConstant;
import com.tacz.guns.api.vmlib.LuaGunAnimationConstant;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import com.tacz.guns.resource.manager.ScriptManager;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.client.client.DualRenderContext;
import com.ssscript.taczfixes.common.util.DualWieldStackId;
import com.ssscript.taczfixes.client.mixin.MixinGunDisplayInstanceAccessor;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.luaj.vm2.LuaTable;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import java.util.concurrent.CompletableFuture;

@OnlyIn(Dist.CLIENT)
@net.neoforged.fml.common.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = {Dist.CLIENT})
@SuppressWarnings({"unchecked", "deprecation", "removal", "rawtypes"})
public final class OffhandDisplayManager {
    private static final long MAX_PUT_AWAY_TIME_MS = 60000;
    private static GunDisplayInstance display;
    private static OffhandGunAnimationContext context;
    private static OffhandMovementAnimation movementAnimation;
    private static ResourceLocation activeGunId;
    private static ResourceLocation activeDisplayId;
    private static UUID activeStackId;
    private static ItemStack activeStack;
    private static UUID putAwayMainStackId;
    private static BedrockGunModel putAwayMainModel;
    private static boolean resumeDualAfterPutAway;
    private static boolean restartMainAfterPutAway;
    private static boolean keepOffhandDisplay;
    private static boolean capturingManualActionTrack;
    private static boolean manualActionCapturePending;
    private static boolean manualActionHoldingArmMotionSeen;
    private static boolean manualActionSupportArmMotionSeen;
    private static final ScriptManager OFFHAND_SCRIPTS = new ScriptManager(new FileToIdConverter("scripts", ".lua"), List.of(new LuaAnimationConstant(), new LuaGunAnimationConstant()));
    private static final ClientOffhandState CLIENT_STATE = new ClientOffhandState();
    private static long putAwayEndTimestamp = -1;
    private static ItemStack putAwayMainStack = ItemStack.EMPTY;
    private static ItemStack putAwayNextMainStack = ItemStack.EMPTY;
    private static ItemStack putAwayNextOffhandStack = ItemStack.EMPTY;
    private static final Set<Integer> MANUAL_ACTION_TRACKS = new HashSet();
    private static ManualActionArmMode manualActionArmMode = ManualActionArmMode.UNDECIDED;

    private enum ManualActionArmMode {
        UNDECIDED,
        HOLDING,
        SUPPORT
    }

    private OffhandDisplayManager() {
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(OFFHAND_SCRIPTS);
        event.registerReloadListener((barrier, resourceManager, preparationsProfiler, reloadProfiler, backgroundExecutor, gameExecutor) ->
                CompletableFuture.completedFuture(null).thenCompose(barrier::wait).thenRunAsync(() -> {
                    DualMovementAnimationLibrary.invalidate();
                    clear();
                }, gameExecutor));
    }

    public static ClientOffhandState getClientState() {
        return CLIENT_STATE;
    }

    public static GunDisplayInstance getOrCreate(ItemStack stack) {
        if (isPuttingAway() && !sameLogicalStack(activeStack, stack)) {
            return null;
        }
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            clear();
            return null;
        }
        return ensureDisplay(stack, true);
    }

    private static GunDisplayInstance ensureDisplay(ItemStack stack, boolean playDraw) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return null;
        }
        ResourceLocation gunId = gun.getGunId(stack);
        ResourceLocation requestedDisplayId = gun.getGunDisplayId(stack);
        ResourceLocation resolvedDisplayId = resolveDisplayId(gunId, requestedDisplayId);
        if (matchesActiveDisplay(gunId, resolvedDisplayId, stack)) {
            activeStack = stack;
            activeStackId = DualWieldStackId.get(stack);
            return display;
        }
        releaseDisplay();
        GunDisplay rawDisplay = ClientAssetsManager.INSTANCE.getGunDisplay(resolvedDisplayId);
        if (rawDisplay == null) {
            return null;
        }
        try {
            GunDisplayInstance newDisplay = GunDisplayInstance.create(resolvedDisplayId, rawDisplay);
            replaceWithIndependentStateMachine(newDisplay, rawDisplay);
            LuaAnimationStateMachine<GunAnimationStateContext> stateMachine = newDisplay.getAnimationStateMachine();
            BedrockGunModel model = newDisplay.getGunModel();
            if (stateMachine == null || model == null) {
                throw new IllegalStateException("Lazy display assets are not available: " + resolvedDisplayId);
            }
            OffhandGunAnimationContext newContext = new OffhandGunAnimationContext(CLIENT_STATE, newDisplay);
            newContext.updateItem(stack, 0.0f);
            stateMachine.setContext(newContext);
            OffhandMovementAnimation newMovementAnimation = OffhandMovementAnimation.install(stateMachine.getAnimationController());
            stateMachine.initialize();
            display = newDisplay;
            context = newContext;
            movementAnimation = newMovementAnimation;
            activeGunId = gunId;
            activeDisplayId = resolvedDisplayId;
            activeStackId = DualWieldStackId.get(stack);
            activeStack = stack;
            if (playDraw) {
                startDrawAnimation();
            }
            return display;
        } catch (RuntimeException exception) {
            TaczFixesMod.LOGGER.error("Failed to create independent offhand display for {}", gunId, exception);
            releaseDisplay();
            return null;
        }
    }

    private static boolean matchesActiveDisplay(ResourceLocation gunId, ResourceLocation resolvedDisplayId, ItemStack stack) {
        return display != null && gunId.equals(activeGunId) && resolvedDisplayId.equals(activeDisplayId) && sameLogicalStack(activeStack, stack);
    }

    /** 按 TaCZ 的 draw 机制启动独立副手状态机: trigger("draw") + 掏枪音效 + 客户端掏枪计时。 */
    private static void startDrawAnimation() {
        if (display == null || context == null) {
            return;
        }
        LuaAnimationStateMachine<GunAnimationStateContext> stateMachine = display.getAnimationStateMachine();
        if (stateMachine == null) {
            return;
        }
        stateMachine.trigger("draw");
        if (Minecraft.getInstance().player != null) {
            SoundPlayManager.playDrawSound(Minecraft.getInstance().player, display);
        }
        if (activeGunId != null) {
            TimelessAPI.getClientGunIndex(activeGunId).map(index -> {
                return index.getGunData();
            }).ifPresent(gunData -> {
                CLIENT_STATE.beginDraw(gunData.getDrawTime());
            });
        }
    }

    public static void updateAnimation(ItemStack stack, float partialTick) {
        updateAnimation(stack, partialTick, true);
    }

    /** firstPersonCorrections: 第一人称视角模型专用的检视镜像/外倾修正, 第三人称不应套用。 */
    private static void updateAnimation(ItemStack stack, float partialTick, boolean firstPersonCorrections) {
        GunDisplayInstance currentDisplay = getOrCreate(stack);
        if (currentDisplay == null || context == null) {
            return;
        }
        BedrockGunModel model = currentDisplay.getGunModel();
        LuaAnimationStateMachine<GunAnimationStateContext> stateMachine = currentDisplay.getAnimationStateMachine();
        if (model == null || stateMachine == null) {
            clear();
            return;
        }
        model.cleanAnimationTransform();
        context.updateItem(stack, partialTick);
        if (movementAnimation != null) {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            boolean thirdPersonSprint = (player == null || minecraft.options.getCameraType().isFirstPerson() || player.isMovingSlowly() || !player.isSprinting()) ? false : true;
            boolean movementBlocked = CLIENT_STATE.isDrawing() || CLIENT_STATE.isReloading() || CLIENT_STATE.isBolting() || DualFocusAimState.isPoseVisible() || isPuttingAway() || DualReloadAnimationManager.isHandActive(InteractionHand.OFF_HAND) || thirdPersonSprint;
            movementAnimation.update(stateMachine, movementBlocked);
        }
        stateMachine.update();
        if (firstPersonCorrections) {
            DualInspectAnimationFilter.applyOffhandOutwardAngle(model);
            DualInspectAnimationFilter.applyOffhandRootMirror(model);
            DualInspectAnimationFilter.applyOffhandCarrierMirror(model);
        }
    }

    static void updateThirdPersonAnimation(ItemStack stack, float partialTick) {
        GunDisplayInstance currentDisplay = getOrCreate(stack);
        BedrockGunModel model = currentDisplay == null ? null : currentDisplay.getGunModel();
        if (model == null) {
            return;
        }
        model.cleanCameraAnimationTransform();
        updateAnimation(stack, partialTick, false);
        model.cleanCameraAnimationTransform();
    }

    public static void triggerNative(String input) {
        LuaAnimationStateMachine<GunAnimationStateContext> animationStateMachine;
        if (!"inspect".equals(input) && display != null) {
            DualInspectAnimationFilter.clearOffhandInspect(display.getGunModel());
        }
        if (display == null) {
            animationStateMachine = null;
        } else {
            animationStateMachine = display.getAnimationStateMachine();
        }
        LuaAnimationStateMachine<GunAnimationStateContext> stateMachine = animationStateMachine;
        if (stateMachine != null && stateMachine.isInitialized()) {
            boolean manualActionInput = "blot".equals(input);
            if (manualActionInput) {
                MANUAL_ACTION_TRACKS.clear();
                manualActionHoldingArmMotionSeen = false;
                manualActionSupportArmMotionSeen = false;
                manualActionArmMode = ManualActionArmMode.UNDECIDED;
                capturingManualActionTrack = true;
                manualActionCapturePending = true;
            }
            try {
                stateMachine.trigger(input);
                if (manualActionInput) {
                    capturingManualActionTrack = false;
                    latchManualActionArmMode();
                }
            } catch (Throwable th) {
                if (manualActionInput) {
                    capturingManualActionTrack = false;
                    latchManualActionArmMode();
                }
                throw th;
            }
        }
    }

    public static void triggerFilteredInspect() {
        LuaAnimationStateMachine<GunAnimationStateContext> animationStateMachine;
        if (display == null) {
            animationStateMachine = null;
        } else {
            animationStateMachine = display.getAnimationStateMachine();
        }
        LuaAnimationStateMachine<GunAnimationStateContext> stateMachine = animationStateMachine;
        DualInspectAnimationFilter.triggerFiltered(stateMachine, display == null ? null : display.getGunModel());
    }

    public static boolean beginPutAway(LocalPlayer player, ItemStack previousMainStack, ItemStack previousOffhandStack, ItemStack nextMainStack, ItemStack nextOffhandStack, boolean resumeDual) {
        LuaAnimationStateMachine<GunAnimationStateContext> animationStateMachine;
        BedrockGunModel bedrockGunModel;
        long j;
        if (isPutAwayActive()) {
            updatePutAwayTarget(nextMainStack, nextOffhandStack, resumeDual);
            return true;
        }
        ItemStack safePreviousOffhand = copyOrEmpty(previousOffhandStack);
        boolean offhandUnchanged = resumeDual && sameLogicalStack(safePreviousOffhand, nextOffhandStack);
        boolean offhandSwitchedAhead = !sameLogicalStack(activeStack, safePreviousOffhand) && sameLogicalStack(activeStack, nextOffhandStack);
        if (!offhandSwitchedAhead && !sameLogicalStack(activeStack, safePreviousOffhand)) {
            getOrCreate(safePreviousOffhand);
        }
        if (display == null) {
            animationStateMachine = null;
        } else {
            animationStateMachine = display.getAnimationStateMachine();
        }
        LuaAnimationStateMachine<GunAnimationStateContext> stateMachine = animationStateMachine;
        long offhandPutAwayTime = offhandUnchanged || offhandSwitchedAhead ? 0L : startOffhandPutAway(stateMachine, safePreviousOffhand);
        putAwayMainStack = copyOrEmpty(previousMainStack);
        putAwayMainStackId = DualWieldStackId.get(putAwayMainStack);
        if (putAwayMainStack.isEmpty()) {
            bedrockGunModel = null;
        } else {
            bedrockGunModel = (BedrockGunModel) TimelessAPI.getGunDisplay(putAwayMainStack).map((v0) -> {
                return v0.getGunModel();
            }).orElse(null);
        }
        putAwayMainModel = bedrockGunModel;
        putAwayNextMainStack = copyOrEmpty(nextMainStack);
        putAwayNextOffhandStack = copyOrEmpty(nextOffhandStack);
        resumeDualAfterPutAway = resumeDual;
        keepOffhandDisplay = offhandUnchanged || offhandSwitchedAhead;
        boolean mainUnchanged = resumeDual && sameLogicalStack(putAwayMainStack, putAwayNextMainStack);
        restartMainAfterPutAway = !mainUnchanged && requiresManualMainCycle(putAwayMainStack, putAwayNextMainStack);
        GunItemRendererWrapper mainRenderer = getGunRenderer(putAwayMainStack);
        long mainPutAwayTime = mainUnchanged ? 0L : getMainPutAwayTime(mainRenderer, putAwayMainStack);
        if (restartMainAfterPutAway && player != null && mainRenderer != null) {
            try {
                IClientPlayerGunOperator.fromLocalPlayer(player).draw(putAwayMainStack);
                mainRenderer.tryExit(putAwayMainStack, mainPutAwayTime);
            } catch (RuntimeException exception) {
                TaczFixesMod.LOGGER.error("Failed to start compensated main-hand put-away animation", exception);
                restartMainAfterPutAway = false;
                mainPutAwayTime = 0;
            }
        }
        long putAwayTime = keepOffhandDisplay && !restartMainAfterPutAway ? 0L : Math.max(offhandPutAwayTime, mainPutAwayTime);
        DualMuzzleFlashState.clearMuzzlePositions();
        if (putAwayTime <= 0) {
            finishPutAway(player);
            return false;
        }
        long currentTime = com.tacz.guns.util.time.GunTime.nowMillis();
        if (putAwayTime > Long.MAX_VALUE - currentTime) {
            j = Long.MAX_VALUE;
        } else {
            j = currentTime + putAwayTime;
        }
        putAwayEndTimestamp = j;
        return true;
    }

    public static void updatePutAwayTarget(ItemStack nextMainStack, ItemStack nextOffhandStack, boolean resumeDual) {
        if (!isPutAwayActive()) {
            return;
        }
        putAwayNextMainStack = copyOrEmpty(nextMainStack);
        putAwayNextOffhandStack = copyOrEmpty(nextOffhandStack);
        resumeDualAfterPutAway = resumeDual;
    }

    public static void tickPutAway(LocalPlayer player) {
        if (OffhandHandSwap.isActive()) {
            OffhandHandSwap.tick(player);
            return;
        }
        if (isPutAwayActive() && com.tacz.guns.util.time.GunTime.nowMillis() >= putAwayEndTimestamp) {
            finishPutAway(player);
        }
    }

    private static boolean isPutAwayActive() {
        return putAwayEndTimestamp >= 0;
    }

    public static boolean isPuttingAway() {
        return isPutAwayActive() || OffhandHandSwap.isActive();
    }

    public static ItemStack getPutAwayStack() {
        if (OffhandHandSwap.isActive()) {
            return OffhandHandSwap.getOffhandRenderStack();
        }
        return (!isPutAwayActive() || activeStack == null) ? ItemStack.EMPTY : activeStack;
    }

    /** F 交换左右手: 显式的交换动画调度器入口 (副手 put_away -> 双方 draw)。 */
    public static boolean beginHandSwap(LocalPlayer player, ItemStack previousMainStack, ItemStack previousOffhandStack,
                                        ItemStack nextMainStack, ItemStack nextOffhandStack) {
        return OffhandHandSwap.begin(player, previousMainStack, previousOffhandStack, nextMainStack, nextOffhandStack);
    }

    public static boolean isHandSwapActive() {
        return OffhandHandSwap.isActive();
    }

    public static ItemStack getHandSwapOffhandRenderStack() {
        return OffhandHandSwap.getOffhandRenderStack();
    }

    public static boolean shouldSuppressMainHandRender(ItemStack stack) {
        return OffhandHandSwap.shouldSuppressMainRender(stack);
    }

    public static boolean isHoldingMainHandDrawSound() {
        return OffhandHandSwap.isHoldingMainDrawSound();
    }

    static long getGunPutAwayTimeMillis(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return 0L;
        }
        ResourceLocation gunId = gun.getGunId(stack);
        float putAwaySeconds = ((Float) TimelessAPI.getCommonGunIndex(gunId).map(index -> {
            return index.getGunData();
        }).map(gunData -> {
            return Float.valueOf(gunData.getPutAwayTime());
        }).orElse(Float.valueOf(0.0f))).floatValue();
        return clampPutAwayTime(putAwaySeconds);
    }

    /**
     * 为离开副手的枪启动 put_away: 完全复刻 TaCZ 的 tryExit 机制
     * (context.setPutAwayTime -> trigger("put_away") -> exit() -> setExitingTime).
     */
    static boolean startHandSwapOffhandPutAway(LocalPlayer player, ItemStack leavingStack, long putAwayMillis, long keepMillis) {
        if (leavingStack == null || leavingStack.isEmpty() || IGun.getIGunOrNull(leavingStack) == null || putAwayMillis <= 0L) {
            releaseDisplay();
            return false;
        }
        boolean reused = display != null && sameLogicalStack(activeStack, leavingStack)
                && display.getAnimationStateMachine() != null && display.getAnimationStateMachine().isInitialized();
        if (!reused) {
            releaseDisplay();
            if (ensureDisplay(leavingStack, false) == null) {
                return false;
            }
        }
        LuaAnimationStateMachine<GunAnimationStateContext> stateMachine = display == null ? null : display.getAnimationStateMachine();
        if (stateMachine == null || context == null) {
            releaseDisplay();
            return false;
        }
        try {
            CLIENT_STATE.resetCharge();
            ItemStack renderStack = leavingStack.copy();
            activeStack = renderStack;
            activeStackId = DualWieldStackId.get(renderStack);
            context.updateItem(renderStack, 0.0f);
            context.setPutAwayTime(putAwayMillis / 1000.0f);
            if (!reused) {
                // 新创建的显示没有播过 draw, 先进入 draw 状态, 让 put_away 转换与 TaCZ 一致。
                stateMachine.trigger("draw");
            }
            stateMachine.trigger("put_away");
            stateMachine.exit();
            stateMachine.setExitingTime(Math.max(keepMillis, putAwayMillis) + 50L);
            if (player != null) {
                SoundPlayManager.playPutAwaySound(player, display);
            }
            return true;
        } catch (RuntimeException exception) {
            TaczFixesMod.LOGGER.error("Failed to start hand-swap offhand put-away", exception);
            releaseDisplay();
            return false;
        }
    }

    /** 交换结束: 副手按当前真实副手物品重建显示并播放 draw (没有枪则隐藏)。 */
    static void finishHandSwapOffhand(LocalPlayer player, ItemStack enteringOffhandStack) {
        clear();
        if (enteringOffhandStack == null || enteringOffhandStack.isEmpty() || IGun.getIGunOrNull(enteringOffhandStack) == null) {
            return;
        }
        getOrCreate(enteringOffhandStack);
    }

    /** 交换结束: 主手进入的枪补一次 draw (主手 put_away 由 TaCZ/SBM 自己完成)。 */
    static void finishHandSwapMain(LocalPlayer player, ItemStack enteringMainStack) {
        if (player == null || enteringMainStack == null || enteringMainStack.isEmpty() || IGun.getIGunOrNull(enteringMainStack) == null) {
            return;
        }
        GunItemRendererWrapper renderer = getGunRenderer(enteringMainStack);
        if (renderer == null) {
            return;
        }
        try {
            renderer.tryInit(enteringMainStack, player, 0.0f);
        } catch (RuntimeException exception) {
            TaczFixesMod.LOGGER.error("Failed to start main-hand draw after hand swap", exception);
        }
        // draw 音效也被延迟到这一刻播放, 与可见的掏枪动画保持同步。
        TimelessAPI.getGunDisplay(enteringMainStack).ifPresent(display -> SoundPlayManager.playDrawSound(player, display));
    }

    private static long startOffhandPutAway(LuaAnimationStateMachine<GunAnimationStateContext> stateMachine, ItemStack previousOffhandStack) {
        if (stateMachine == null || !stateMachine.isInitialized() || context == null || activeStack == null || activeStack.isEmpty() || activeGunId == null || !sameLogicalStack(activeStack, previousOffhandStack)) {
            return 0L;
        }
        float putAwaySeconds = ((Float) TimelessAPI.getCommonGunIndex(activeGunId).map(index -> {
            return index.getGunData();
        }).map(gunData -> {
            return Float.valueOf(gunData.getPutAwayTime());
        }).orElse(Float.valueOf(0.0f))).floatValue();
        long putAwayTime = clampPutAwayTime(putAwaySeconds);
        if (putAwayTime <= 0) {
            return 0L;
        }
        activeStack = previousOffhandStack.copy();
        activeStackId = DualWieldStackId.get(activeStack);
        try {
            CLIENT_STATE.resetCharge();
            context.updateItem(activeStack, 0.0f);
            context.setPutAwayTime(putAwayTime / 1000.0f);
            stateMachine.trigger("put_away");
            stateMachine.exit();
            stateMachine.setExitingTime(putAwayTime + 50);
            if (Minecraft.getInstance().player != null) {
                SoundPlayManager.playPutAwaySound(Minecraft.getInstance().player, display);
            }
            return putAwayTime;
        } catch (RuntimeException exception) {
            TaczFixesMod.LOGGER.error("Failed to start independent offhand put-away animation for {}", activeGunId, exception);
            return 0L;
        }
    }

    private static long getMainPutAwayTime(GunItemRendererWrapper renderer, ItemStack stack) {
        if (renderer == null || stack == null || stack.isEmpty()) {
            return 0L;
        }
        try {
            return Math.min(Math.max(renderer.getPutAwayTime(stack), 0L), MAX_PUT_AWAY_TIME_MS);
        } catch (RuntimeException exception) {
            TaczFixesMod.LOGGER.error("Failed to resolve main-hand put-away time", exception);
            return 0L;
        }
    }

    private static long clampPutAwayTime(float seconds) {
        if (!Float.isFinite(seconds) || seconds <= 0.0f) {
            return 0L;
        }
        long rawTime = Math.max((long) (seconds * 1000.0f), 0L);
        return Math.min(rawTime, MAX_PUT_AWAY_TIME_MS);
    }

    private static void finishPutAway(LocalPlayer player) {
        GunItemRendererWrapper renderer;
        ItemStack nextMainStack = putAwayNextMainStack.copy();
        ItemStack nextOffhandStack = putAwayNextOffhandStack.copy();
        boolean resumeDual = resumeDualAfterPutAway;
        boolean restartMain = restartMainAfterPutAway;
        boolean reuseOffhandDisplay = resumeDual
                && sameLogicalStack(activeStack, nextOffhandStack) && display != null && context != null;
        if (keepOffhandDisplay && reuseOffhandDisplay) {
            // 副手未变化或渲染已提前切换到新枪: 保持当前显示与动画, 不重播收枪/掏枪动画
            clearPutAwayMetadata();
        } else if (reuseOffhandDisplay && restartActiveOffhandDisplay(nextOffhandStack)) {
            clearPutAwayMetadata();
        } else {
            clear();
            if (resumeDual && !nextOffhandStack.isEmpty()) {
                getOrCreate(nextOffhandStack);
            }
        }
        if (restartMain && player != null && !nextMainStack.isEmpty() && (renderer = getGunRenderer(nextMainStack)) != null) {
            try {
                renderer.tryInit(nextMainStack, player, 0.0f);
            } catch (RuntimeException exception) {
                TaczFixesMod.LOGGER.error("Failed to restart compensated main-hand draw animation", exception);
            }
        }
    }

    private static boolean restartActiveOffhandDisplay(ItemStack nextOffhandStack) {
        LuaAnimationStateMachine<GunAnimationStateContext> animationStateMachine;
        if (display == null) {
            animationStateMachine = null;
        } else {
            animationStateMachine = display.getAnimationStateMachine();
        }
        LuaAnimationStateMachine<GunAnimationStateContext> stateMachine = animationStateMachine;
        if (stateMachine == null || context == null) {
            return false;
        }
        try {
            activeStack = nextOffhandStack;
            activeStackId = DualWieldStackId.get(nextOffhandStack);
            context.updateItem(nextOffhandStack, 0.0f);
            stateMachine.setContext(context);
            stateMachine.initialize();
            stateMachine.trigger("draw");
            if (Minecraft.getInstance().player != null) {
                SoundPlayManager.playDrawSound(Minecraft.getInstance().player, display);
            }
            TimelessAPI.getClientGunIndex(activeGunId).map(index -> {
                return index.getGunData();
            }).ifPresent(gunData -> {
                CLIENT_STATE.beginDraw(gunData.getDrawTime());
            });
            DualMuzzleFlashState.invalidateMuzzle(DualRenderContext.HandPhase.OFFHAND);
            return true;
        } catch (RuntimeException exception) {
            TaczFixesMod.LOGGER.error("Failed to restart independent offhand draw animation", exception);
            return false;
        }
    }

    private static boolean requiresManualMainCycle(ItemStack previousStack, ItemStack nextStack) {
        if (sameLogicalStack(previousStack, nextStack)) {
            return true;
        }
        IGun previousGun = IGun.getIGunOrNull(previousStack);
        IGun nextGun = IGun.getIGunOrNull(nextStack);
        if (previousGun == null || nextGun == null) {
            return false;
        }
        ResourceLocation previousGunId = previousGun.getGunId(previousStack);
        ResourceLocation nextGunId = nextGun.getGunId(nextStack);
        ResourceLocation previousDisplayId = previousGun.getGunDisplayId(previousStack);
        ResourceLocation nextDisplayId = nextGun.getGunDisplayId(nextStack);
        return previousGunId.equals(nextGunId) && Objects.equals(previousDisplayId, nextDisplayId);
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

    private static GunItemRendererWrapper getGunRenderer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        try {
            BlockEntityWithoutLevelRenderer customRenderer = IClientItemExtensions.of(stack.getItem()).getCustomRenderer();
            if (!(customRenderer instanceof GunItemRendererWrapper)) {
                return null;
            }
            GunItemRendererWrapper gunRenderer = (GunItemRendererWrapper) customRenderer;
            return gunRenderer;
        } catch (RuntimeException exception) {
            TaczFixesMod.LOGGER.error("Failed to resolve gun item renderer", exception);
            return null;
        }
    }

    private static ItemStack copyOrEmpty(ItemStack stack) {
        return (stack == null || stack.isEmpty()) ? ItemStack.EMPTY : stack.copy();
    }

    public static boolean isPuttingAwayMainStack(ItemStack stack) {
        if (!isPuttingAway() || stack == null || stack.isEmpty() || putAwayMainStack.isEmpty()) {
            return false;
        }
        UUID stackId = DualWieldStackId.get(stack);
        if (stackId != null && putAwayMainStackId != null) {
            return stackId.equals(putAwayMainStackId);
        }
        return ItemStack.isSameItemSameComponents(stack, putAwayMainStack);
    }

    public static boolean isActiveModel(BedrockAnimatedModel model) {
        return (model == null || display == null || display.getGunModel() != model) ? false : true;
    }

    public static boolean canStabilizeManualActionArm(BedrockAnimatedModel model) {
        if (CLIENT_STATE.isBolting() && isActiveManualActionBolt()) {
            return isActiveModel(model) && (capturingManualActionTrack || manualActionCapturePending || !MANUAL_ACTION_TRACKS.isEmpty());
        }
        resetManualActionCapture();
        return false;
    }

    public static boolean shouldStabilizeManualActionArm(BedrockAnimatedModel model, int track) {
        if (!canStabilizeManualActionArm(model)) {
            return false;
        }
        if (track >= 0 && (capturingManualActionTrack || manualActionCapturePending)) {
            MANUAL_ACTION_TRACKS.add(Integer.valueOf(track));
        }
        return track >= 0 && MANUAL_ACTION_TRACKS.contains(Integer.valueOf(track));
    }

    public static boolean isManualActionArmMotionActive(BedrockAnimatedModel model) {
        return model != null && CLIENT_STATE.isBolting() && isActiveModel(model) && isActiveManualActionBolt();
    }

    public static void recordManualActionArmMotion(BedrockAnimatedModel model, boolean holdingArmDynamic, boolean supportArmDynamic) {
        if (!canStabilizeManualActionArm(model)) {
            return;
        }
        manualActionHoldingArmMotionSeen |= holdingArmDynamic;
        manualActionSupportArmMotionSeen |= supportArmDynamic;
        if (!capturingManualActionTrack) {
            latchManualActionArmMode();
        }
    }

    public static boolean shouldUseInwardHoldingArmForManualAction(BedrockAnimatedModel model) {
        return isManualActionArmMotionActive(model) && manualActionArmMode != ManualActionArmMode.SUPPORT;
    }

    private static void latchManualActionArmMode() {
        if (manualActionArmMode != ManualActionArmMode.UNDECIDED) {
            return;
        }
        if (manualActionHoldingArmMotionSeen) {
            manualActionArmMode = ManualActionArmMode.HOLDING;
        } else if (manualActionSupportArmMotionSeen) {
            manualActionArmMode = ManualActionArmMode.SUPPORT;
        }
    }

    private static boolean isActiveManualActionBolt() {
        if (activeGunId == null) {
            return false;
        }
        return ((Boolean) TimelessAPI.getCommonGunIndex(activeGunId).map(index -> {
            GunData gunData = index.getGunData();
            return Boolean.valueOf((gunData == null || gunData.getBolt() != Bolt.MANUAL_ACTION || isShotgunGunData(gunData, index.getType())) ? false : true);
        }).orElse(false)).booleanValue();
    }

    private static boolean isShotgunGunData(GunData gunData, String declaredType) {
        BulletData bulletData = gunData.getBulletData();
        if (bulletData != null && bulletData.getBulletAmount() > 1) {
            return true;
        }
        ResourceLocation ammoId = gunData.getAmmoId();
        String normalizedAmmo = normalizeAnimationName(ammoId == null ? "" : ammoId.toString());
        boolean shotgunAmmunition = normalizedAmmo.contains("buckshot") || normalizedAmmo.contains("shotshell") || normalizedAmmo.contains("shotgunshell") || normalizedAmmo.contains("12ga") || normalizedAmmo.contains("12g");
        return shotgunAmmunition || normalizeAnimationName(declaredType).contains("shotgun");
    }

    private static String normalizeAnimationName(String value) {
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

    public static void synchronizeManualActionCaptureState() {
        if (!CLIENT_STATE.isBolting()) {
            resetManualActionCapture();
        }
    }

    private static void resetManualActionCapture() {
        MANUAL_ACTION_TRACKS.clear();
        capturingManualActionTrack = false;
        manualActionCapturePending = false;
        manualActionHoldingArmMotionSeen = false;
        manualActionSupportArmMotionSeen = false;
        manualActionArmMode = ManualActionArmMode.UNDECIDED;
    }

    public static void cancelPutAway(LocalPlayer player, ItemStack mainStack, ItemStack offhandStack) {
        GunItemRendererWrapper renderer;
        if (!isPuttingAway()) {
            return;
        }
        boolean restartMain = restartMainAfterPutAway;
        clear();
        if (restartMain && player != null && mainStack != null && !mainStack.isEmpty() && (renderer = getGunRenderer(mainStack)) != null) {
            try {
                renderer.tryInit(mainStack, player, 0.0f);
            } catch (RuntimeException exception) {
                TaczFixesMod.LOGGER.error("Failed to cancel compensated main-hand put-away", exception);
            }
        }
        if (offhandStack != null && !offhandStack.isEmpty()) {
            getOrCreate(offhandStack);
        }
    }

    public static void clear() {
        OffhandHandSwap.reset();
        DualReloadAnimationManager.clear();
        OffhandCameraController.reset();
        OffhandJumpSway.reset();
        DualMuzzleFlashState.clearMuzzlePositions();
        OffhandArmPoseResolver.clear();
        releaseDisplay();
        clearPutAwayMetadata();
        resetManualActionCapture();
        CLIENT_STATE.reset();
    }

    /** 只释放当前独立副手显示 (退出状态机), 不清除其它全局状态与过渡数据。 */
    private static void releaseDisplay() {
        DualInspectAnimationFilter.clearOffhandInspect(display == null ? null : display.getGunModel());
        LuaAnimationStateMachine<GunAnimationStateContext> stateMachine = display == null ? null : display.getAnimationStateMachine();
        if (stateMachine != null && stateMachine.isInitialized()) {
            try {
                stateMachine.exit();
            } catch (RuntimeException exception) {
                TaczFixesMod.LOGGER.error("Failed to exit independent offhand state machine", exception);
            }
        }
        display = null;
        context = null;
        movementAnimation = null;
        activeGunId = null;
        activeDisplayId = null;
        activeStackId = null;
        activeStack = null;
    }

    private static void clearPutAwayMetadata() {
        putAwayEndTimestamp = -1L;
        putAwayMainStack = ItemStack.EMPTY;
        putAwayMainStackId = null;
        putAwayMainModel = null;
        putAwayNextMainStack = ItemStack.EMPTY;
        putAwayNextOffhandStack = ItemStack.EMPTY;
        resumeDualAfterPutAway = false;
        restartMainAfterPutAway = false;
        keepOffhandDisplay = false;
    }

    private static ResourceLocation resolveDisplayId(ResourceLocation gunId, ResourceLocation requestedDisplayId) {
        if (requestedDisplayId != null && !DefaultAssets.DEFAULT_GUN_DISPLAY_ID.equals(requestedDisplayId) && ClientAssetsManager.INSTANCE.getGunDisplay(requestedDisplayId) != null) {
            return requestedDisplayId;
        }
        return (ResourceLocation) TimelessAPI.getCommonGunIndex(gunId).map(index -> {
            return index.getPojo().getDisplay();
        }).orElse(DefaultAssets.DEFAULT_GUN_DISPLAY_ID);
    }

    private static void replaceWithIndependentStateMachine(GunDisplayInstance target, GunDisplay rawDisplay) {
        ResourceLocation scriptId = rawDisplay.getStateMachineLocation();
        if (scriptId == null) {
            scriptId = ResourceLocation.fromNamespaceAndPath("tacz", "default_state_machine");
        }
        LuaTable script = OFFHAND_SCRIPTS.getScript(scriptId);
        if (script == null) {
            throw new IllegalStateException("Independent offhand state machine script is not ready: " + scriptId);
        }
        LuaAnimationStateMachine<GunAnimationStateContext> originalStateMachine = target.getAnimationStateMachine();
        if (originalStateMachine == null) {
            throw new IllegalStateException("Animation state machine could not be loaded");
        }
        AnimationController controller = originalStateMachine.getAnimationController();
        LuaAnimationStateMachine<GunAnimationStateContext> stateMachine = new LuaStateMachineFactory().setController(controller).setLuaScripts(script).build();
        ((MixinGunDisplayInstanceAccessor) target).dualWield$setAnimationStateMachine(stateMachine);
    }
}
