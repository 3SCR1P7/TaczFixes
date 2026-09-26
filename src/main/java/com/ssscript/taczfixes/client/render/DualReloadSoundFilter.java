package com.ssscript.taczfixes.client.render;

import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.AnimationSoundChannelContent;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import com.tacz.guns.api.client.animation.ObjectAnimationSoundChannel;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.sound.GunSoundInstance;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.config.common.GunConfig;
import com.tacz.guns.sound.SoundManager;
import com.ssscript.taczfixes.common.util.DualWieldBalance;
import com.ssscript.taczfixes.client.mixin.MixinObjectAnimationAccessor;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DualReloadSoundFilter {
    private static final Map<AnimationController, ReloadSession> SESSIONS = Collections.synchronizedMap(new WeakHashMap());
    private static final ThreadLocal<Deque<ReloadCapture>> CAPTURES = new ThreadLocal<>();
    private static GunSoundInstance offhandLegacySound;

    private DualReloadSoundFilter() {
    }

    public static void triggerNativeReload(LuaAnimationStateMachine<GunAnimationStateContext> stateMachine, String input, int overlayTrack) {
        if (stateMachine == null || !stateMachine.isInitialized()) {
            return;
        }
        AnimationController controller = stateMachine.getAnimationController();
        ReloadSession session = getOrCreateSession(controller, 1.0d);
        session.acceptFutureReloadClips = true;
        if (overlayTrack >= 0) {
            session.overlayTracks.add(Integer.valueOf(overlayTrack));
        }
        Deque<ReloadCapture> captures = CAPTURES.get();
        if (captures == null) {
            captures = new ArrayDeque();
            CAPTURES.set(captures);
        }
        captures.push(new ReloadCapture(controller, session));
        try {
            stateMachine.trigger(input);
            captures.pop();
            if (captures.isEmpty()) {
                CAPTURES.remove();
            }
        } catch (Throwable th) {
            captures.pop();
            if (captures.isEmpty()) {
                CAPTURES.remove();
            }
            throw th;
        }
    }

    public static ObjectAnimation filterPrototype(AnimationController controller, int track, String animationName, ObjectAnimation prototype) {
        if (controller == null || track < 0 || prototype == null) {
            return prototype;
        }
        ReloadSession session = SESSIONS.get(controller);
        ReloadCapture capture = getCurrentCapture();
        boolean capturedNow = (capture == null || capture.controller != controller || capture.session.overlayTracks.contains(Integer.valueOf(track))) ? false : true;
        if (capturedNow) {
            session = capture.session;
            session.getOrCreateTrack(track);
        }
        if (session == null || session.overlayTracks.contains(Integer.valueOf(track))) {
            return prototype;
        }
        ReloadTrack reloadTrack = session.nativeReloadTracks.get(Integer.valueOf(track));
        if (reloadTrack == null) {
            return prototype;
        }
        String resolvedName = resolveAnimationName(animationName, prototype);
        boolean explicitReloadClip = isReloadClipName(resolvedName);
        boolean chainedReloadClip = session.acceptFutureReloadClips && isCurrentRunnerFromReloadChain(controller, track, reloadTrack);
        if (!capturedNow && !explicitReloadClip && (!session.acceptFutureReloadClips || !chainedReloadClip || isClearlyNonReloadClip(resolvedName))) {
            return prototype;
        }
        reloadTrack.filteredClipNames.add(resolvedName);
        reloadTrack.filteredClipNames.add(prototype.name);
        ObjectAnimation soundOnly = new ObjectAnimation(prototype);
        scaleSoundTimeline(soundOnly, session.timelineScale);
        soundOnly.getChannels().clear();
        return soundOnly;
    }

    private static void scaleSoundTimeline(ObjectAnimation animation, double timelineScale) {
        ObjectAnimationSoundChannel soundChannel = animation.getSoundChannel();
        if (soundChannel != null && soundChannel.content != null) {
            AnimationSoundChannelContent copiedContent = new AnimationSoundChannelContent(soundChannel.content);
            if (copiedContent.keyframeTimeS != null) {
                for (int index = 0; index < copiedContent.keyframeTimeS.length; index++) {
                    double[] dArr = copiedContent.keyframeTimeS;
                    int i = index;
                    dArr[i] = dArr[i] * timelineScale;
                }
            }
            soundChannel.content = copiedContent;
        }
        MixinObjectAnimationAccessor accessor = (MixinObjectAnimationAccessor) animation;
        accessor.dualWield$setMaxEndTimeS(animation.getMaxEndTimeS() * ((float) timelineScale));
    }

    public static void disableFutureFiltering(AnimationController controller) {
        ReloadSession session;
        if (controller != null && (session = SESSIONS.get(controller)) != null) {
            session.acceptFutureReloadClips = false;
        }
    }

    public static void endSession(AnimationController controller) {
        if (controller != null) {
            SESSIONS.remove(controller);
        }
    }

    public static void playOffhandLegacyReload(LocalPlayer player, ItemStack stack, GunDisplayInstance display, boolean empty) {
        String str;
        float reloadTimeScale;
        stopOffhandLegacyReload();
        if (player == null || display == null) {
            return;
        }
        if (empty) {
            str = SoundManager.RELOAD_EMPTY_SOUND;
        } else {
            str = SoundManager.RELOAD_TACTICAL_SOUND;
        }
        ResourceLocation sound = display.getSounds(str);
        if (sound == null) {
            return;
        }
        if (DualWieldClient.isDualMode(player)) {
            reloadTimeScale = (float) DualWieldBalance.getReloadTimeScale(stack);
        } else {
            reloadTimeScale = 1.0f;
        }
        offhandLegacySound = SoundPlayManager.playClientSound(player, sound, 1.0f, reloadTimeScale, ((Integer) GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get()).intValue());
    }

    public static void stopOffhandLegacyReload() {
        if (offhandLegacySound != null) {
            offhandLegacySound.setStop();
            offhandLegacySound = null;
        }
    }

    public static void clear() {
        SESSIONS.clear();
        CAPTURES.remove();
        stopOffhandLegacyReload();
    }

    private static ReloadSession getOrCreateSession(AnimationController controller, double reloadTimeScale) {
        ReloadSession session = SESSIONS.get(controller);
        if (session == null) {
            session = new ReloadSession(reloadTimeScale);
            SESSIONS.put(controller, session);
        }
        return session;
    }

    private static ReloadCapture getCurrentCapture() {
        Deque<ReloadCapture> captures = CAPTURES.get();
        if (captures == null || captures.isEmpty()) {
            return null;
        }
        return captures.peek();
    }

    private static String resolveAnimationName(String animationName, ObjectAnimation prototype) {
        if (animationName != null && !animationName.isBlank()) {
            return animationName;
        }
        return prototype.name;
    }

    private static boolean isReloadClipName(String animationName) {
        return animationName.toLowerCase(Locale.ROOT).contains("reload");
    }

    private static boolean isCurrentRunnerFromReloadChain(AnimationController controller, int track, ReloadTrack reloadTrack) {
        ObjectAnimationRunner animation = controller.getAnimation(track);
        while (true) {
            ObjectAnimationRunner runner = animation;
            if (runner != null) {
                ObjectAnimation animation2 = runner.getAnimation();
                if (animation2.getChannels().isEmpty() && reloadTrack.filteredClipNames.contains(animation2.name)) {
                    return true;
                }
                animation = runner.getTransitionTo();
            } else {
                return false;
            }
        }
    }

    private static boolean isClearlyNonReloadClip(String animationName) {
        String name = animationName.toLowerCase(Locale.ROOT);
        return hasActionPrefix(name, "static") || hasActionPrefix(name, "idle") || hasActionPrefix(name, "walk") || hasActionPrefix(name, "run") || hasActionPrefix(name, "sprint") || hasActionPrefix(name, "crawl") || hasActionPrefix(name, "slide") || hasActionPrefix(name, "draw") || hasActionPrefix(name, "put_away") || hasActionPrefix(name, "inspect") || hasActionPrefix(name, "shoot") || hasActionPrefix(name, "bolt") || hasActionPrefix(name, "aim") || hasActionPrefix(name, "sight") || hasActionPrefix(name, "melee") || hasActionPrefix(name, "bayonet") || hasActionPrefix(name, "switch") || hasActionPrefix(name, "fire_select") || hasActionPrefix(name, "charge") || hasActionPrefix(name, "over_heat") || hasActionPrefix(name, "spin") || hasActionPrefix(name, "handle");
    }

    private static boolean hasActionPrefix(String animationName, String prefix) {
        return animationName.equals(prefix) || animationName.startsWith(prefix + "_") || animationName.startsWith(prefix + "-") || animationName.startsWith(prefix + ".");
    }

    private static final class ReloadSession {
        private final double timelineScale;
        private final Map<Integer, ReloadTrack> nativeReloadTracks = new HashMap();
        private final Set<Integer> overlayTracks = new HashSet();
        private boolean acceptFutureReloadClips = true;

        private ReloadSession(double reloadTimeScale) {
            double d;
            if (Double.isFinite(reloadTimeScale) && reloadTimeScale > 0.0d) {
                d = reloadTimeScale;
            } else {
                d = 1.0d;
            }
            this.timelineScale = d;
        }

        private ReloadTrack getOrCreateTrack(int track) {
            return this.nativeReloadTracks.computeIfAbsent(Integer.valueOf(track), ignored -> {
                return new ReloadTrack();
            });
        }
    }

    private static final class ReloadTrack {
        private final Set<String> filteredClipNames = new HashSet();

        private ReloadTrack() {
        }
    }

    private static final class ReloadCapture {
        private final AnimationController controller;
        private final ReloadSession session;

        private ReloadCapture(AnimationController controller, ReloadSession session) {
            this.controller = controller;
            this.session = session;
        }
    }
}
