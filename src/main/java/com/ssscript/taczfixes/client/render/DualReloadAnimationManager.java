package com.ssscript.taczfixes.client.render;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.util.DualWieldStackId;
import com.ssscript.taczfixes.common.util.PausableClock;
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
/* 双持换弹视觉: 播放 put_away, 结束后隐藏手臂, 等待 (feed - put_away) 后播放 draw。 */
public final class DualReloadAnimationManager {
    private static final long MAX_VISUAL_DURATION_MS = 120000;
    private static final long MAX_DRAW_DURATION_MS = 10000;
    private static final float DRAW_TRANSITION_SECONDS = 0.05f;
    private static final Map<GunAnimationStateContext, Integer> OVERLAY_TRACKS = new WeakHashMap();
    private static ReloadVisual mainVisual;
    private static ReloadVisual offhandVisual;

    private DualReloadAnimationManager() {
    }

    public static void beginMainReload(LocalPlayer player, ItemStack stack, GunData gunData, float feedSeconds, LuaAnimationStateMachine<GunAnimationStateContext> stateMachine, String input) {
        if (player == null || stack == null || stack.isEmpty() || gunData == null) {
            return;
        }
        GunDisplayInstance display = (GunDisplayInstance) TimelessAPI.getGunDisplay(stack).orElse(null);
        mainVisual = beginVisual(mainVisual, InteractionHand.MAIN_HAND, stack, display, gunData, feedSeconds);
        triggerNativeReload(mainVisual, stateMachine, input);
    }

    public static void beginOffhandReload(LocalPlayer player, ItemStack stack, GunData gunData, float feedSeconds) {
        LuaAnimationStateMachine<GunAnimationStateContext> animationStateMachine;
        if (player == null || stack == null || stack.isEmpty() || gunData == null) {
            return;
        }
        GunDisplayInstance display = OffhandDisplayManager.getOrCreate(stack);
        offhandVisual = beginVisual(offhandVisual, InteractionHand.OFF_HAND, stack, display, gunData, feedSeconds);
        if (display == null) {
            animationStateMachine = null;
        } else {
            animationStateMachine = display.getAnimationStateMachine();
        }
        triggerNativeReload(offhandVisual, animationStateMachine, "reload");
    }

    public static void tick(LocalPlayer player) {
        if (player == null) {
            clear();
            return;
        }
        long now = PausableClock.millis();
        mainVisual = tickVisual(player, mainVisual, now);
        offhandVisual = tickVisual(player, offhandVisual, now);
    }

    public static void clear() {
        removeOverlay(mainVisual);
        removeOverlay(offhandVisual);
        mainVisual = null;
        offhandVisual = null;
        DualReloadSoundFilter.clear();
    }

    public static void clearHand(InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            removeOverlay(mainVisual);
            mainVisual = null;
        } else {
            removeOverlay(offhandVisual);
            offhandVisual = null;
        }
    }

    public static boolean isActive() {
        return mainVisual != null || offhandVisual != null;
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

    /* put_away 播放结束到 draw 开始之间隐藏该手手臂。 */
    public static boolean areArmsHidden(InteractionHand hand) {
        ReloadVisual visual = hand == InteractionHand.MAIN_HAND ? mainVisual : offhandVisual;
        if (visual == null) {
            return false;
        }
        long elapsed = PausableClock.millis() - visual.startTimestamp;
        return elapsed >= visual.putAwayMillis && elapsed < visual.drawStartTimestamp - visual.startTimestamp;
    }

    public static void markHandReloadCompleted(InteractionHand hand) {
        ReloadVisual visual = hand == InteractionHand.MAIN_HAND ? mainVisual : offhandVisual;
        if (visual != null) {
            DualReloadSoundFilter.disableFutureFiltering(getController(visual.display));
        }
    }

    private static boolean hasModel(ReloadVisual visual, BedrockAnimatedModel model) {
        return (visual == null || visual.display == null || visual.display.getGunModel() != model) ? false : true;
    }

    private static ReloadVisual beginVisual(ReloadVisual previous, InteractionHand hand, ItemStack stack, GunDisplayInstance display, GunData gunData, float feedSeconds) {
        removeOverlay(previous);
        long now = PausableClock.millis();
        IGun gun = IGun.getIGunOrNull(stack);
        ResourceLocation gunId = gun == null ? null : gun.getGunId(stack);
        long putAwayMillis = toMillis(gunData.getPutAwayTime());
        long feedMillis = Math.max(toMillis(feedSeconds), 1L);
        ReloadVisual visual = new ReloadVisual(hand, display, DualWieldStackId.get(stack), gunId, now, putAwayMillis, feedMillis);
        visual.drawStartTimestamp = now + Math.max(feedMillis, putAwayMillis);
        playPutAway(visual);
        return visual;
    }

    private static ReloadVisual tickVisual(LocalPlayer player, ReloadVisual visual, long now) {
        ItemStack currentStack;
        if (visual == null) {
            return null;
        }
        if (visual.hand == InteractionHand.MAIN_HAND) {
            currentStack = player.getMainHandItem();
        } else {
            currentStack = player.getOffhandItem();
        }
        if (!visual.matches(currentStack)) {
            removeOverlay(visual);
            return null;
        }
        if (!visual.drawStarted && now >= visual.drawStartTimestamp) {
            visual.drawStarted = true;
            playDraw(visual);
        }
        if (visual.drawStarted && (isOverlayStopped(visual) || now >= visual.drawHardEndTimestamp)) {
            removeOverlay(visual);
            return null;
        }
        if (!visual.drawStarted && now >= visual.drawHardEndTimestamp) {
            removeOverlay(visual);
            return null;
        }
        if (now - visual.startTimestamp > MAX_VISUAL_DURATION_MS) {
            removeOverlay(visual);
            return null;
        }
        return visual;
    }

    private static void playPutAway(ReloadVisual visual) {
        AnimationController controller = getController(visual.display);
        int track = getOverlayTrack(visual.display);
        if (controller == null || track < 0 || !controller.containPrototype("put_away")) {
            return;
        }
        visual.track = track;
        controller.runAnimation(track, "put_away", ObjectAnimation.PlayType.PLAY_ONCE_HOLD, 0.0f);
        controller.setBlending(track, false);
    }

    private static void playDraw(ReloadVisual visual) {
        AnimationController controller = getController(visual.display);
        int track = visual.track >= 0 ? visual.track : getOverlayTrack(visual.display);
        if (controller == null || track < 0 || !controller.containPrototype("draw")) {
            return;
        }
        visual.track = track;
        visual.drawHardEndTimestamp = PausableClock.millis() + MAX_DRAW_DURATION_MS;
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
        DualReloadSoundFilter.triggerNativeReload(stateMachine, input, visual.track);
    }

    private static long toMillis(float seconds) {
        if (!Float.isFinite(seconds) || seconds <= 0.0f) {
            return 0L;
        }
        return Math.min((long) (seconds * 1000.0f), MAX_VISUAL_DURATION_MS);
    }

    /* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/render/DualReloadAnimationManager$ReloadVisual.class */
    private static final class ReloadVisual {
        private final InteractionHand hand;
        private final GunDisplayInstance display;
        private final UUID stackId;
        private final ResourceLocation gunId;
        private final long startTimestamp;
        private final long putAwayMillis;
        private final long feedMillis;
        private boolean drawStarted;
        private int track = -1;
        private long drawStartTimestamp = Long.MAX_VALUE;
        private long drawHardEndTimestamp = Long.MAX_VALUE;

        private ReloadVisual(InteractionHand hand, GunDisplayInstance display, UUID stackId, ResourceLocation gunId, long startTimestamp, long putAwayMillis, long feedMillis) {
            this.hand = hand;
            this.display = display;
            this.stackId = stackId;
            this.gunId = gunId;
            this.startTimestamp = startTimestamp;
            this.putAwayMillis = putAwayMillis;
            this.feedMillis = feedMillis;
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
