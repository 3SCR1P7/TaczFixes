package com.ssscript.taczfixes.client.render;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.util.DualWieldBalance;
import com.ssscript.taczfixes.common.util.DualWieldStackId;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/render/DualReloadAnimationManager.class */
public final class DualReloadAnimationManager {
    private static final long HAND_STAGGER_MS = 100;
    private static final long MAX_VISUAL_DURATION_MS = 120000;
    private static final long MAX_DRAW_DURATION_MS = 10000;
    private static final float DRAW_TRANSITION_SECONDS = 0.08f;
    private static ReloadVisual mainVisual;
    private static ReloadVisual offhandVisual;
    private static final Map<GunAnimationStateContext, Integer> OVERLAY_TRACKS = new WeakHashMap();
    private static long cycleAnchorTimestamp = -1;

    private DualReloadAnimationManager() {
    }

    public static void beginMainReload(LocalPlayer player, ItemStack stack, GunData gunData, float durationSeconds, LuaAnimationStateMachine<GunAnimationStateContext> stateMachine, String input) {
        if (player == null || stack == null || stack.isEmpty() || gunData == null) {
            return;
        }
        GunDisplayInstance display = (GunDisplayInstance) TimelessAPI.getGunDisplay(stack).orElse(null);
        mainVisual = beginVisual(mainVisual, InteractionHand.MAIN_HAND, stack, display, gunData.getPutAwayTime(), durationSeconds);
        triggerNativeReload(mainVisual, stateMachine, input);
    }

    public static void beginOffhandReload(LocalPlayer player, ItemStack stack, GunData gunData, float durationSeconds) {
        LuaAnimationStateMachine<GunAnimationStateContext> animationStateMachine;
        if (player == null || stack == null || stack.isEmpty() || gunData == null) {
            return;
        }
        GunDisplayInstance display = OffhandDisplayManager.getOrCreate(stack);
        mainCycleAnchor();
        offhandVisual = beginVisual(offhandVisual, InteractionHand.OFF_HAND, stack, display, gunData.getPutAwayTime(), durationSeconds);
        if (display == null) {
            animationStateMachine = null;
        } else {
            animationStateMachine = display.getAnimationStateMachine();
        }
        LuaAnimationStateMachine<GunAnimationStateContext> stateMachine = animationStateMachine;
        triggerNativeReload(offhandVisual, stateMachine, "reload");
    }

    public static void tick(LocalPlayer player) {
        if (player == null) {
            clear();
            return;
        }
        long now = System.currentTimeMillis();
        mainVisual = tickVisual(player, mainVisual, now);
        offhandVisual = tickVisual(player, offhandVisual, now);
        if (mainVisual == null && offhandVisual == null) {
            cycleAnchorTimestamp = -1L;
        }
    }

    public static void clear() {
        removeOverlay(mainVisual);
        removeOverlay(offhandVisual);
        mainVisual = null;
        offhandVisual = null;
        cycleAnchorTimestamp = -1L;
        DualReloadSoundFilter.clear();
    }

    public static boolean isActive() {
        return (mainVisual == null && offhandVisual == null) ? false : true;
    }

    public static boolean isHandActive(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? mainVisual != null : offhandVisual != null;
    }

    public static boolean isModelActive(BedrockAnimatedModel model) {
        if (model == null) {
            return false;
        }
        return hasModel(mainVisual, model) || hasModel(offhandVisual, model);
    }

    private static boolean hasModel(ReloadVisual visual, BedrockAnimatedModel model) {
        return (visual == null || visual.display == null || visual.display.getGunModel() != model) ? false : true;
    }

    public static void markHandReloadCompleted(InteractionHand hand) {
        ReloadVisual visual = hand == InteractionHand.MAIN_HAND ? mainVisual : offhandVisual;
        if (visual == null) {
            return;
        }
        markCompletion(visual, System.currentTimeMillis());
    }

    public static void clearHand(InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            removeOverlay(mainVisual);
            mainVisual = null;
        } else {
            removeOverlay(offhandVisual);
            offhandVisual = null;
        }
        if (mainVisual == null && offhandVisual == null) {
            cycleAnchorTimestamp = -1L;
        }
    }

    private static ReloadVisual beginVisual(ReloadVisual previous, InteractionHand hand, ItemStack stack, GunDisplayInstance display, float putAwaySeconds, float durationSeconds) {
        removeOverlay(previous);
        long now = System.currentTimeMillis();
        long anchor = mainCycleAnchor();
        long stagger = hand == InteractionHand.MAIN_HAND ? HAND_STAGGER_MS : 0L;
        double reloadTimeScale = DualWieldBalance.getReloadTimeScale(stack);
        long durationMillis = sanitizeDurationMillis(durationSeconds, reloadTimeScale);
        UUID stackId = DualWieldStackId.get(stack);
        IGun gun = IGun.getIGunOrNull(stack);
        ResourceLocation gunId = gun == null ? null : gun.getGunId(stack);
        ReloadVisual visual = new ReloadVisual(hand, display, stackId, gunId, reloadTimeScale, sanitizePutAwaySeconds(putAwaySeconds), anchor + stagger, safeAdd(now, durationMillis), safeAdd(now, MAX_VISUAL_DURATION_MS));
        visual.putAwayArmed = armPutAway(visual, now);
        return visual;
    }

    private static ReloadVisual tickVisual(LocalPlayer player, ReloadVisual visual, long now) {
        ItemStack itemStackM_21206_;
        boolean zIsReloading;
        ObjectAnimationRunner animation;
        if (visual == null) {
            return null;
        }
        if (visual.hand == InteractionHand.MAIN_HAND) {
            itemStackM_21206_ = player.getMainHandItem();
        } else {
            itemStackM_21206_ = player.getOffhandItem();
        }
        ItemStack currentStack = itemStackM_21206_;
        if (!visual.matches(currentStack)) {
            removeOverlay(visual);
            return null;
        }
        if (!visual.putAwayArmed) {
            visual.putAwayArmed = armPutAway(visual, now);
        }
        if (!visual.putAwayStarted && now >= visual.putAwayTimestamp) {
            AnimationController controller = getController(visual.display);
            if (controller == null || visual.track < 0) {
                animation = null;
            } else {
                animation = controller.getAnimation(visual.track);
            }
            ObjectAnimationRunner runner = animation;
            if (runner != null) {
                runner.run();
            }
            visual.putAwayStarted = true;
        }
        if (visual.hand == InteractionHand.MAIN_HAND) {
            zIsReloading = IGunOperator.fromLivingEntity(player).getSynReloadState().getStateType().isReloading();
        } else {
            zIsReloading = OffhandDisplayManager.getClientState().isReloading();
        }
        boolean reloading = zIsReloading;
        if (reloading) {
            visual.sawReloadingState = true;
        }
        boolean authorityFinished = visual.sawReloadingState && !reloading;
        boolean fallbackFinished = now >= visual.fallbackEndTimestamp && !reloading;
        boolean timedOut = now >= visual.hardEndTimestamp;
        if (authorityFinished || fallbackFinished || timedOut) {
            markCompletion(visual, now);
        }
        long drawStagger = visual.hand == InteractionHand.OFF_HAND ? HAND_STAGGER_MS : 0L;
        if (!visual.drawStarted && visual.completionTimestamp >= 0 && now >= safeAdd(visual.completionTimestamp, drawStagger)) {
            visual.drawStarted = true;
            playDraw(visual, now);
        }
        if (visual.drawStarted && (isOverlayStopped(visual) || now >= visual.drawHardEndTimestamp)) {
            removeOverlay(visual);
            return null;
        }
        return visual;
    }

    private static void markCompletion(ReloadVisual visual, long now) {
        if (visual == null || visual.completionTimestamp >= 0) {
            return;
        }
        visual.completionTimestamp = now;
        DualReloadSoundFilter.disableFutureFiltering(getController(visual.display));
    }

    private static boolean armPutAway(ReloadVisual visual, long now) {
        float fMin;
        AnimationController controller = getController(visual.display);
        int track = getOverlayTrack(visual.display);
        if (controller == null || track < 0 || !controller.containPrototype("put_away")) {
            return false;
        }
        visual.track = track;
        controller.runAnimation(track, "put_away", ObjectAnimation.PlayType.PLAY_ONCE_HOLD, 0.0f);
        controller.setBlending(track, false);
        ObjectAnimationRunner runner = controller.getAnimation(track);
        if (runner != null) {
            float endSeconds = Math.max(runner.getAnimation().getMaxEndTimeS(), 0.0f);
            if (visual.putAwaySeconds > 0.0f) {
                fMin = Math.min(visual.putAwaySeconds, endSeconds);
            } else {
                fMin = endSeconds;
            }
            float playedSeconds = fMin;
            runner.setProgressNs((long) (Math.max(endSeconds - playedSeconds, 0.0f) * 1.0E9f));
            if (now < visual.putAwayTimestamp) {
                runner.pause();
                return true;
            }
            visual.putAwayStarted = true;
            return true;
        }
        return true;
    }

    private static void playDraw(ReloadVisual visual, long now) {
        AnimationController controller = getController(visual.display);
        int track = visual.track >= 0 ? visual.track : getOverlayTrack(visual.display);
        if (controller == null || track < 0 || !controller.containPrototype("draw")) {
            removeOverlay(visual);
            return;
        }
        visual.track = track;
        visual.drawHardEndTimestamp = safeAdd(now, MAX_DRAW_DURATION_MS);
        controller.runAnimation(track, "draw", ObjectAnimation.PlayType.PLAY_ONCE_STOP, DRAW_TRANSITION_SECONDS);
        controller.setBlending(track, false);
    }

    private static boolean isOverlayStopped(ReloadVisual visual) {
        ObjectAnimationRunner runner;
        AnimationController controller = getController(visual.display);
        return controller == null || visual.track < 0 || (runner = controller.getAnimation(visual.track)) == null || runner.isStopped();
    }

    private static int getOverlayTrack(GunDisplayInstance display) {
        LuaAnimationStateMachine<GunAnimationStateContext> stateMachine;
        GunAnimationStateContext context;
        if (display == null || (stateMachine = display.getAnimationStateMachine()) == null || !stateMachine.isInitialized() || (context = stateMachine.getContext()) == null) {
            return -1;
        }
        Integer cachedTrack = OVERLAY_TRACKS.get(context);
        if (cachedTrack != null) {
            return cachedTrack.intValue();
        }
        try {
            int trackLine = context.addTrackLine();
            int track = context.getAsSingletonTrack(trackLine);
            OVERLAY_TRACKS.put(context, Integer.valueOf(track));
            return track;
        } catch (RuntimeException exception) {
            TaczFixesMod.LOGGER.error("Failed to reserve dual reload animation track", exception);
            return -1;
        }
    }

    private static AnimationController getController(GunDisplayInstance display) {
        LuaAnimationStateMachine<GunAnimationStateContext> animationStateMachine;
        if (display == null) {
            animationStateMachine = null;
        } else {
            animationStateMachine = display.getAnimationStateMachine();
        }
        LuaAnimationStateMachine<GunAnimationStateContext> stateMachine = animationStateMachine;
        if (stateMachine == null) {
            return null;
        }
        return stateMachine.getAnimationController();
    }

    private static void removeOverlay(ReloadVisual visual) {
        if (visual == null) {
            return;
        }
        AnimationController controller = getController(visual.display);
        DualReloadSoundFilter.endSession(controller);
        if (controller != null && visual.track >= 0) {
            controller.removeAnimation(visual.track);
        }
        if (visual.hand == InteractionHand.OFF_HAND) {
            DualReloadSoundFilter.stopOffhandLegacyReload();
        }
    }

    private static void triggerNativeReload(ReloadVisual visual, LuaAnimationStateMachine<GunAnimationStateContext> stateMachine, String input) {
        if (visual == null || stateMachine == null || input == null) {
            return;
        }
        DualReloadSoundFilter.triggerNativeReload(stateMachine, input, visual.track, visual.reloadTimeScale);
    }

    private static long mainCycleAnchor() {
        long now = System.currentTimeMillis();
        if (cycleAnchorTimestamp < 0 || now - cycleAnchorTimestamp > 300) {
            cycleAnchorTimestamp = now;
        }
        return cycleAnchorTimestamp;
    }

    private static long sanitizeDurationMillis(float durationSeconds, double reloadTimeScale) {
        double d;
        if (!Float.isFinite(durationSeconds) || durationSeconds <= 0.0f) {
            return 1L;
        }
        if (Double.isFinite(reloadTimeScale) && reloadTimeScale > 0.0d) {
            d = reloadTimeScale;
        } else {
            d = 1.0d;
        }
        double safeTimeScale = d;
        long duration = (long) ((durationSeconds * 1000.0d) / safeTimeScale);
        return Math.min(Math.max(duration, 1L), MAX_VISUAL_DURATION_MS);
    }

    private static float sanitizePutAwaySeconds(float putAwaySeconds) {
        if (!Float.isFinite(putAwaySeconds) || putAwaySeconds <= 0.0f) {
            return 0.0f;
        }
        return Math.min(putAwaySeconds, 120.0f);
    }

    private static long safeAdd(long base, long delta) {
        if (delta > Long.MAX_VALUE - base) {
            return Long.MAX_VALUE;
        }
        return base + delta;
    }

    /* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/render/DualReloadAnimationManager$ReloadVisual.class */
    private static final class ReloadVisual {
        private final InteractionHand hand;
        private final GunDisplayInstance display;
        private final UUID stackId;
        private final ResourceLocation gunId;
        private final double reloadTimeScale;
        private final float putAwaySeconds;
        private final long putAwayTimestamp;
        private final long fallbackEndTimestamp;
        private final long hardEndTimestamp;
        private boolean putAwayArmed;
        private boolean putAwayStarted;
        private boolean sawReloadingState;
        private boolean drawStarted;
        private int track = -1;
        private long completionTimestamp = -1;
        private long drawHardEndTimestamp = Long.MAX_VALUE;

        private ReloadVisual(InteractionHand hand, GunDisplayInstance display, UUID stackId, ResourceLocation gunId, double reloadTimeScale, float putAwaySeconds, long putAwayTimestamp, long fallbackEndTimestamp, long hardEndTimestamp) {
            this.hand = hand;
            this.display = display;
            this.stackId = stackId;
            this.gunId = gunId;
            this.reloadTimeScale = reloadTimeScale;
            this.putAwaySeconds = putAwaySeconds;
            this.putAwayTimestamp = putAwayTimestamp;
            this.fallbackEndTimestamp = fallbackEndTimestamp;
            this.hardEndTimestamp = hardEndTimestamp;
        }

        private boolean matches(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            UUID currentStackId = DualWieldStackId.get(stack);
            if (this.stackId != null && currentStackId != null) {
                return this.stackId.equals(currentStackId);
            }
            IGun gun = IGun.getIGunOrNull(stack);
            return (gun == null || this.gunId == null || !this.gunId.equals(gun.getGunId(stack))) ? false : true;
        }
    }
}
