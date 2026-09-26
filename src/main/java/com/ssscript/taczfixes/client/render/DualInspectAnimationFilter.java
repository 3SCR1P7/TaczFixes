package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationChannelContent;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.AnimationListenerSupplier;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.ObjectAnimationChannel;
import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import com.tacz.guns.api.client.animation.interpolator.CustomInterpolator;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.util.math.MathUtil;
import com.ssscript.taczfixes.client.mixin.MixinAnimationControllerAccessor;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public final class DualInspectAnimationFilter {
    private static final long INSPECT_START_GRACE_MS = 250;
    private static final long INSPECT_CONTINUATION_GRACE_MS = 100;
    private static final ThreadLocal<Integer> FILTER_DEPTH = ThreadLocal.withInitial(() -> {
        return 0;
    });
    private static final ThreadLocal<BedrockAnimatedModel> FILTER_TARGET = new ThreadLocal<>();
    private static final Map<BedrockAnimatedModel, InspectSession> ACTIVE_INSPECTS = new IdentityHashMap();
    private static final Map<BedrockAnimatedModel, RuntimePlan> THIRD_PERSON_ANCHOR_PLANS = new WeakHashMap();
    private static final float OUTWARD_YAW_RAD = (float) Math.toRadians(7.0d);
    private static final String[] FORBIDDEN_TOKENS = {"magazine", "magzine", "mag", "clip", "loader", "bullet", "bullets", "round", "cartridge", "shell", "ammo"};

    private DualInspectAnimationFilter() {
    }

    public static void triggerFiltered(LuaAnimationStateMachine<GunAnimationStateContext> stateMachine) {
        triggerFiltered(stateMachine, null);
    }

    public static void triggerFiltered(LuaAnimationStateMachine<GunAnimationStateContext> stateMachine, BedrockAnimatedModel target) {
        if (stateMachine == null || !stateMachine.isInitialized()) {
            return;
        }
        BedrockAnimatedModel previousTarget = FILTER_TARGET.get();
        FILTER_DEPTH.set(Integer.valueOf(FILTER_DEPTH.get().intValue() + 1));
        if (target != null) {
            FILTER_TARGET.set(target);
            beginInspect(target, stateMachine.getAnimationController());
        }
        try {
            stateMachine.trigger("inspect");
            int depth = FILTER_DEPTH.get().intValue() - 1;
            if (depth <= 0) {
                FILTER_DEPTH.remove();
            } else {
                FILTER_DEPTH.set(Integer.valueOf(depth));
            }
            if (previousTarget == null) {
                FILTER_TARGET.remove();
            } else {
                FILTER_TARGET.set(previousTarget);
            }
        } catch (Throwable th) {
            int depth2 = FILTER_DEPTH.get().intValue() - 1;
            if (depth2 <= 0) {
                FILTER_DEPTH.remove();
            } else {
                FILTER_DEPTH.set(Integer.valueOf(depth2));
            }
            if (previousTarget == null) {
                FILTER_TARGET.remove();
            } else {
                FILTER_TARGET.set(previousTarget);
            }
            throw th;
        }
    }

    public static ObjectAnimation filterPrototype(AnimationController controller, int track, ObjectAnimation.PlayType playType, String animationName, ObjectAnimation prototype, AnimationListenerSupplier listenerSupplier) {
        if (prototype != null && (listenerSupplier instanceof BedrockAnimatedModel)) {
            BedrockAnimatedModel animatedModel = (BedrockAnimatedModel) listenerSupplier;
            if (isTargetModel(animatedModel) && shouldFilter(controller, track, playType, animationName, animatedModel)) {
                ObjectAnimation filtered = new ObjectAnimation(prototype);
                BedrockPart modelRoot = animatedModel.getRootNode();
                if (modelRoot == null) {
                    return filtered;
                }
                Map<String, BedrockPart> nodesByName = new LinkedHashMap<>();
                Map<BedrockPart, BedrockPart> parents = new IdentityHashMap<>();
                indexTree(modelRoot, null, nodesByName, parents);
                FilterPlan plan = createFilterPlan(animatedModel, modelRoot, nodesByName, parents);
                BedrockPart holdingHandEndpoint = findHoldingHandEndpoint(nodesByName, parents);
                addExternalAmmunitionFollowers(prototype, plan, nodesByName, parents, holdingHandEndpoint);
                applyFilterPlan(filtered, plan, nodesByName, parents, holdingHandEndpoint);
                hideSupportHands(filtered, modelRoot, parents, plan.gunAnchor, plan.installedMagazine);
                hideDetachedInspectProps(filtered, prototype, nodesByName, parents);
                recordInspectClip(animatedModel, controller, track, animationName, prototype, createRuntimePlan(plan, parents));
                return filtered;
            }
        }
        return prototype;
    }

    public static synchronized void applyRuntimeRigidFollowers(BedrockAnimatedModel model) {
        RuntimePlan plan = getRunningPlan(model);
        if (plan != null) {
            applyRuntimePlan(plan);
        }
    }

    public static synchronized void applyThirdPersonGripTether(BedrockAnimatedModel model, PoseStack poseStack, ItemDisplayContext transformType) {
        BedrockPart modelRoot;
        List<BedrockPart> thirdPersonHandPath;
        if (model == null || poseStack == null) {
            return;
        }
        if (transformType != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND && transformType != ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            return;
        }
        RuntimePlan plan = getRunningPlan(model);
        boolean inspectActive = plan != null || isInspectActive(model);
        boolean reloadActive = DualReloadAnimationManager.isModelActive(model);
        if ((!inspectActive && !reloadActive) || (modelRoot = model.getRootNode()) == null || !(model instanceof BedrockGunModel)) {
            return;
        }
        BedrockGunModel gunModel = (BedrockGunModel) model;
        if (plan == null) {
            plan = getOrCreateThirdPersonAnchorPlan(model, modelRoot);
        }
        if (plan == null || plan.gunCarrier == null || (thirdPersonHandPath = gunModel.getThirdPersonHandOriginPath()) == null || thirdPersonHandPath.isEmpty()) {
            return;
        }
        Matrix4f anchorBindWorld = computePathMatrix(thirdPersonHandPath, false);
        Matrix4f carrierBindWorld = computeWorldMatrix(plan.gunCarrier, plan.parents, false);
        Matrix4f carrierCurrentWorld = computeWorldMatrix(plan.gunCarrier, plan.parents, true);
        if (!isFinite(anchorBindWorld) || !isFinite(carrierBindWorld) || !isFinite(carrierCurrentWorld)) {
            return;
        }
        float bindDeterminant = carrierBindWorld.determinant();
        float currentDeterminant = carrierCurrentWorld.determinant();
        if (!Float.isFinite(bindDeterminant) || !Float.isFinite(currentDeterminant) || Math.abs(bindDeterminant) < 1.0E-8f || Math.abs(currentDeterminant) < 1.0E-8f) {
            return;
        }
        Vector3f anchorBindPosition = anchorBindWorld.getTranslation(new Vector3f());
        Vector3f anchorInCarrier = new Matrix4f(carrierBindWorld).invert().transformPosition(new Vector3f(anchorBindPosition));
        Vector3f anchorCurrentPosition = new Matrix4f(carrierCurrentWorld).transformPosition(new Vector3f(anchorInCarrier));
        Vector3f delta = new Vector3f(anchorBindPosition).sub(anchorCurrentPosition);
        if (!isFinite(anchorInCarrier) || !isFinite(anchorCurrentPosition) || !isFinite(delta)) {
            return;
        }
        poseStack.translate(delta.x(), delta.y(), delta.z());
    }

    private static RuntimePlan getOrCreateThirdPersonAnchorPlan(BedrockAnimatedModel model, BedrockPart modelRoot) {
        RuntimePlan cached = THIRD_PERSON_ANCHOR_PLANS.get(model);
        if (cached != null && cached.gunCarrier != null && isAncestor(modelRoot, cached.gunCarrier, cached.parents)) {
            return cached;
        }
        Map<String, BedrockPart> nodesByName = new LinkedHashMap<>();
        Map<BedrockPart, BedrockPart> parents = new IdentityHashMap<>();
        indexTree(modelRoot, null, nodesByName, parents);
        FilterPlan filterPlan = createFilterPlan(model, modelRoot, nodesByName, parents);
        RuntimePlan created = createRuntimePlan(filterPlan, parents);
        if (created != null) {
            THIRD_PERSON_ANCHOR_PLANS.put(model, created);
        }
        return created;
    }

    public static void applyOffhandCarrierMirror(BedrockAnimatedModel model) {
        RuntimePlan plan = getRunningPlan(model);
        BedrockPart root = model == null ? null : model.getRootNode();
        if (plan == null || root == null) {
            return;
        }
        List<BedrockPart> controlPath = pathFromAncestor(root, plan.gunCarrier, plan.parents);
        for (int index = 1; index < controlPath.size(); index++) {
            mirrorLocalAnimationDelta(controlPath.get(index));
        }
    }

    public static void applyOffhandRootMirror(BedrockAnimatedModel model) {
        BedrockPart root = model == null ? null : model.getRootNode();
        if (root != null) {
            mirrorLocalAnimationDelta(root);
        }
    }

    public static boolean isOffhandCarrierPathPart(BedrockAnimatedModel model, BedrockPart part) {
        RuntimePlan plan = getRunningPlan(model);
        BedrockPart root = model == null ? null : model.getRootNode();
        if (plan == null || root == null || part == null || part == root) {
            return false;
        }
        return pathFromAncestor(root, plan.gunCarrier, plan.parents).contains(part);
    }

    public static void applyOffhandOutwardAngle(BedrockAnimatedModel model) {
        float blend = getInspectBlend(model);
        BedrockPart root = model == null ? null : model.getRootNode();
        if (root == null || blend <= 0.0f) {
            return;
        }
        Quaternionf outward = new Quaternionf();
        MathUtil.toQuaternion(0.0f, OUTWARD_YAW_RAD * blend, 0.0f, outward);
        root.additionalQuaternion.mul(outward);
    }

    public static synchronized void clearOffhandInspect(BedrockAnimatedModel model) {
        if (model == null) {
            ACTIVE_INSPECTS.clear();
        } else {
            ACTIVE_INSPECTS.remove(model);
        }
    }

    public static synchronized boolean isInspectActive(BedrockAnimatedModel model) {
        InspectSession session = getActiveSession(model);
        if (session == null) {
            return false;
        }
        if (session.clipNames.isEmpty()) {
            return true;
        }
        return hasActiveInspectRunnerWithinWindow(session);
    }

    private static boolean hasActiveInspectRunnerWithinWindow(InspectSession session) {
        return com.ssscript.taczfixes.common.util.PausableClock.millis() <= session.continuationUntilMs && hasActiveInspectRunner(session);
    }

    private static synchronized void beginInspect(BedrockAnimatedModel model, AnimationController controller) {
        long now = com.ssscript.taczfixes.common.util.PausableClock.millis();
        ACTIVE_INSPECTS.put(model, new InspectSession(controller, now + INSPECT_START_GRACE_MS));
        discardExpiredSessions(now);
    }

    private static synchronized void recordInspectClip(BedrockAnimatedModel model, AnimationController controller, int track, String animationName, ObjectAnimation prototype, RuntimePlan runtimePlan) {
        long jCeil;
        InspectSession session = getActiveSession(model);
        if (session == null || session.controller != controller) {
            return;
        }
        if (track >= 0) {
            session.tracks.add(Integer.valueOf(track));
        }
        registerClipName(session, animationName, runtimePlan);
        registerClipName(session, prototype.name, runtimePlan);
        float seconds = prototype.getMaxEndTimeS();
        if (Float.isFinite(seconds)) {
            jCeil = (long) Math.ceil(Math.max(seconds, 0.25f) * 1000.0f);
        } else {
            jCeil = 1000;
        }
        long durationMs = jCeil;
        long now = com.ssscript.taczfixes.common.util.PausableClock.millis();
        long boundedDurationMs = Math.min(durationMs, 120000L);
        session.continuationUntilMs = Math.max(session.continuationUntilMs, now + boundedDurationMs + INSPECT_CONTINUATION_GRACE_MS);
        session.expiresAtMs = Math.max(now + Math.min(boundedDurationMs + 5000, 120000L), session.continuationUntilMs);
    }

    private static void registerClipName(InspectSession session, String clipName, RuntimePlan runtimePlan) {
        if (clipName == null || clipName.isEmpty()) {
            return;
        }
        session.clipNames.add(clipName);
        if (runtimePlan != null) {
            session.runtimePlans.put(clipName, runtimePlan);
        }
    }

    private static synchronized float getInspectBlend(BedrockAnimatedModel model) {
        InspectSession session = getActiveSession(model);
        if (session == null) {
            return 0.0f;
        }
        float result = 0.0f;
        for (ObjectAnimationRunner runner : getCurrentRunners(session.controller)) {
            result = Math.max(result, inspectRunnerBlend(runner, session.clipNames));
        }
        return result;
    }

    private static synchronized RuntimePlan getRunningPlan(BedrockAnimatedModel model) {
        InspectSession session = getActiveSession(model);
        if (session == null) {
            return null;
        }
        RuntimePlan currentPlan = null;
        for (ObjectAnimationRunner runner : getCurrentRunners(session.controller)) {
            if (runner != null) {
                ObjectAnimationRunner transition = runner.getTransitionTo();
                boolean activeTransition = isActiveTransition(runner, transition);
                if (activeTransition) {
                    RuntimePlan transitionPlan = session.runtimePlans.get(transition.getAnimation().name);
                    if (transitionPlan != null) {
                        return transitionPlan;
                    }
                    if (currentPlan == null) {
                        currentPlan = session.runtimePlans.get(runner.getAnimation().name);
                    }
                }
                if (!runner.isStopped() && currentPlan == null) {
                    currentPlan = session.runtimePlans.get(runner.getAnimation().name);
                }
            }
        }
        return currentPlan;
    }

    private static InspectSession getActiveSession(BedrockAnimatedModel model) {
        if (model == null) {
            return null;
        }
        InspectSession session = ACTIVE_INSPECTS.get(model);
        long now = com.ssscript.taczfixes.common.util.PausableClock.millis();
        if (session != null && (now > session.expiresAtMs || (!session.clipNames.isEmpty() && now > session.continuationUntilMs && !hasActiveInspectRunner(session)))) {
            ACTIVE_INSPECTS.remove(model);
            return null;
        }
        return session;
    }

    private static void discardExpiredSessions(long now) {
        ACTIVE_INSPECTS.entrySet().removeIf(entry -> {
            return now > ((InspectSession) entry.getValue()).expiresAtMs;
        });
    }

    private static List<ObjectAnimationRunner> getCurrentRunners(AnimationController controller) {
        if (controller == null) {
            return Collections.emptyList();
        }
        return ((MixinAnimationControllerAccessor) controller).dualWield$getCurrentRunners();
    }

    private static boolean hasActiveInspectRunner(InspectSession session) {
        for (ObjectAnimationRunner runner : getCurrentRunners(session.controller)) {
            if (runner != null) {
                ObjectAnimationRunner transition = runner.getTransitionTo();
                boolean activeTransition = isActiveTransition(runner, transition);
                boolean currentInspect = session.clipNames.contains(runner.getAnimation().name);
                boolean transitionInspect = transition != null && session.clipNames.contains(transition.getAnimation().name);
                boolean activeCurrent = currentInspect && (!(runner.isStopped() || runner.isHolding()) || activeTransition);
                boolean activeTransitionInspect = transitionInspect && activeTransition;
                if (activeCurrent || activeTransitionInspect) {
                    return true;
                }
            }
        }
        return false;
    }

    private static float inspectRunnerBlend(ObjectAnimationRunner runner, Set<String> clipNames) {
        ObjectAnimation animation;
        if (runner == null) {
            return 0.0f;
        }
        ObjectAnimationRunner transition = runner.getTransitionTo();
        boolean activeTransition = isActiveTransition(runner, transition);
        if (activeTransition && clipNames.contains(transition.getAnimation().name)) {
            runner = transition;
            animation = transition.getAnimation();
        } else {
            if (runner.isStopped() && !activeTransition) {
                return 0.0f;
            }
            animation = runner.getAnimation();
            if (!clipNames.contains(animation.name)) {
                return 0.0f;
            }
        }
        float duration = animation.getMaxEndTimeS();
        if (!Float.isFinite(duration) || duration <= 0.05f) {
            return 0.0f;
        }
        float time = Math.max(0.0f, Math.min(duration, runner.getProgressNs() / 1.0E9f));
        float ramp = Math.min(0.35f, Math.max(0.12f, duration * 0.12f));
        return outwardEnvelope(time, duration, Math.min(ramp, duration * 0.5f));
    }

    private static boolean isActiveTransition(ObjectAnimationRunner runner, ObjectAnimationRunner transition) {
        return (runner == null || !runner.isTransitioning() || transition == null || transition.isStopped()) ? false : true;
    }

    private static boolean isTargetModel(BedrockAnimatedModel model) {
        if (FILTER_TARGET.get() == model) {
            return true;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !DualWieldClient.isDualMode(player)) {
            return false;
        }
        if (OffhandDisplayManager.isActiveModel(model)) {
            return true;
        }
        BedrockAnimatedModel mainModel = (BedrockAnimatedModel) TimelessAPI.getGunDisplay(player.getMainHandItem()).map((v0) -> {
            return v0.getGunModel();
        }).orElse(null);
        return mainModel == model;
    }

    private static synchronized boolean shouldFilter(AnimationController controller, int track, ObjectAnimation.PlayType playType, String animationName, BedrockAnimatedModel model) {
        boolean directInspectTrigger = FILTER_DEPTH.get().intValue() > 0 && (FILTER_TARGET.get() == null || FILTER_TARGET.get() == model);
        boolean namedInspect = normalize(animationName).contains("inspect");
        boolean directInspectClip = directInspectTrigger && playType != ObjectAnimation.PlayType.LOOP;
        InspectSession session = getActiveSession(model);
        if ((directInspectTrigger || namedInspect) && (session == null || session.controller != controller)) {
            beginInspect(model, controller);
            session = getActiveSession(model);
        }
        if (directInspectClip || namedInspect) {
            return true;
        }
        if (session == null || session.controller != controller || track < 0) {
            return false;
        }
        if (isCompetingActionAnimation(animationName)) {
            ACTIVE_INSPECTS.remove(model);
            return false;
        }
        boolean oneShot = playType != ObjectAnimation.PlayType.LOOP;
        if (session.tracks.contains(Integer.valueOf(track))) {
            if (!oneShot) {
                session.tracks.remove(Integer.valueOf(track));
                if (session.tracks.isEmpty()) {
                    ACTIVE_INSPECTS.remove(model);
                    return false;
                }
                return false;
            }
            return true;
        }
        if (oneShot) {
            return session.clipNames.isEmpty() || hasActiveInspectRunner(session) || com.ssscript.taczfixes.common.util.PausableClock.millis() <= session.continuationUntilMs;
        }
        return false;
    }

    private static boolean isCompetingActionAnimation(String animationName) {
        String normalized = normalize(animationName);
        return normalized.contains("shoot") || normalized.contains("shot") || normalized.contains("fire") || hasAnimationWord(animationName, "aim") || hasAnimationWord(animationName, "ads") || normalized.contains("reload") || normalized.contains("bolt") || normalized.contains("pump") || normalized.contains("pomp") || normalized.contains("cock") || normalized.contains("chamber") || normalized.contains("melee") || normalized.contains("putaway") || normalized.startsWith("draw");
    }

    private static boolean hasAnimationWord(String animationName, String word) {
        if (animationName == null || animationName.isEmpty()) {
            return false;
        }
        String separated = animationName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        return separated.equals(word) || separated.startsWith(word + "_") || separated.endsWith("_" + word) || separated.contains("_" + word + "_");
    }

    private static FilterPlan createFilterPlan(BedrockAnimatedModel model, BedrockPart modelRoot, Map<String, BedrockPart> nodesByName, Map<BedrockPart, BedrockPart> parents) {
        BedrockPart bedrockPartLowestCommonAncestor;
        BedrockPart installedMagazine = findInstalledMagazine(nodesByName);
        BedrockPart gunAnchor = findGunAnchor(model, modelRoot, installedMagazine, parents);
        if (gunAnchor == null) {
            return new FilterPlan(modelRoot, null, installedMagazine, null, Collections.emptySet());
        }
        if (installedMagazine == null) {
            bedrockPartLowestCommonAncestor = modelRoot;
        } else {
            bedrockPartLowestCommonAncestor = lowestCommonAncestor(gunAnchor, installedMagazine, parents);
        }
        BedrockPart commonCarrier = bedrockPartLowestCommonAncestor;
        if (commonCarrier == null) {
            commonCarrier = modelRoot;
        }
        Set<BedrockPart> freezeSubtrees = Collections.newSetFromMap(new IdentityHashMap());
        addIndependentBranch(freezeSubtrees, installedMagazine, gunAnchor, parents);
        BedrockPart gunMotionCarrier = selectStableGunCarrier(commonCarrier, gunAnchor, installedMagazine, parents);
        if (gunMotionCarrier == null) {
            gunMotionCarrier = commonCarrier;
        }
        return new FilterPlan(commonCarrier, gunAnchor, installedMagazine, gunMotionCarrier, freezeSubtrees);
    }

    private static void applyFilterPlan(ObjectAnimation filtered, FilterPlan plan, Map<String, BedrockPart> nodesByName, Map<BedrockPart, BedrockPart> parents, BedrockPart holdingHandEndpoint) {
        Set<String> frozenNames = new HashSet<>();
        for (BedrockPart frozenRoot : plan.freezeSubtrees) {
            collectSubtreeNames(frozenRoot, frozenNames);
        }
        Iterator it = new ArrayList(filtered.getChannels().keySet()).iterator();
        while (it.hasNext()) {
            String nodeName = (String) it.next();
            BedrockPart part = nodesByName.get(nodeName);
            boolean filteredOperation = isFilteredOperationPath(part, plan.commonCarrier, plan.gunAnchor, parents);
            if (!isHoldingEndpointControlPath(part, holdingHandEndpoint, parents) && (frozenNames.contains(nodeName) || filteredOperation)) {
                List<ObjectAnimationChannel> channels = (List) filtered.getChannels().get(nodeName);
                if (channels != null) {
                    channels.removeIf(channel -> {
                        return channel.type == ObjectAnimationChannel.ChannelType.TRANSLATION || channel.type == ObjectAnimationChannel.ChannelType.ROTATION;
                    });
                    if (channels.isEmpty()) {
                        filtered.getChannels().remove(nodeName);
                    }
                }
            }
        }
    }

    private static RuntimePlan createRuntimePlan(FilterPlan plan, Map<BedrockPart, BedrockPart> parents) {
        if (plan.gunMotionCarrier == null) {
            return null;
        }
        Matrix4f bindGunWorld = computeWorldMatrix(plan.gunMotionCarrier, parents, false);
        List<RigidFollower> followers = new ArrayList<>();
        for (BedrockPart target : plan.freezeSubtrees) {
            BedrockPart parent = parents.get(target);
            followers.add(new RigidFollower(target, parent, new Matrix4f(bindGunWorld), computeWorldMatrix(target, parents, false)));
        }
        return new RuntimePlan(plan.gunMotionCarrier, new IdentityHashMap(parents), followers);
    }

    private static void applyRuntimePlan(RuntimePlan plan) {
        Matrix4f matrix4fComputeWorldMatrix;
        Matrix4f currentGunWorld = computeWorldMatrix(plan.gunCarrier, plan.parents, true);
        float currentGunDeterminant = currentGunWorld.determinant();
        if (!isFinite(currentGunWorld) || !Float.isFinite(currentGunDeterminant) || Math.abs(currentGunDeterminant) < 1.0E-8f) {
            hideRuntimeFollowers(plan);
            return;
        }
        for (RigidFollower follower : plan.followers) {
            if (follower.parent == null) {
                matrix4fComputeWorldMatrix = new Matrix4f();
            } else {
                matrix4fComputeWorldMatrix = computeWorldMatrix(follower.parent, plan.parents, true);
            }
            Matrix4f currentParentWorld = matrix4fComputeWorldMatrix;
            float parentDeterminant = currentParentWorld.determinant();
            float bindGunDeterminant = follower.bindGunWorld.determinant();
            if (Float.isFinite(parentDeterminant) && Float.isFinite(bindGunDeterminant) && Math.abs(parentDeterminant) >= 1.0E-8f && Math.abs(bindGunDeterminant) >= 1.0E-8f) {
                Matrix4f desiredWorld = new Matrix4f(currentGunWorld).mul(new Matrix4f(follower.bindGunWorld).invert()).mul(follower.bindTargetWorld);
                Matrix4f desiredLocal = new Matrix4f(currentParentWorld).invert().mul(desiredWorld);
                applyFollowerLocalMatrix(follower.target, desiredLocal);
            }
        }
    }

    private static void applyFollowerLocalMatrix(BedrockPart target, Matrix4f desiredLocal) {
        float currentXScale = target.xScale;
        float currentYScale = target.yScale;
        float currentZScale = target.zScale;
        applyLocalMatrix(target, desiredLocal);
        target.xScale = currentXScale;
        target.yScale = currentYScale;
        target.zScale = currentZScale;
    }

    private static void hideRuntimeFollowers(RuntimePlan plan) {
        for (RigidFollower follower : plan.followers) {
            follower.target.xScale = 0.0f;
            follower.target.yScale = 0.0f;
            follower.target.zScale = 0.0f;
        }
    }

    private static Matrix4f computeLocalMatrix(BedrockPart part, boolean animated) {
        Matrix4f result = new Matrix4f();
        if (animated) {
            result.translate(part.offsetX, part.offsetY, part.offsetZ);
        }
        result.translate(part.x / 16.0f, part.y / 16.0f, part.z / 16.0f);
        result.rotateZ(part.zRot);
        result.rotateY(part.yRot);
        result.rotateX(part.xRot);
        if (animated) {
            result.rotate(part.additionalQuaternion);
            result.scale(part.xScale, part.yScale, part.zScale);
        }
        return result;
    }

    private static Matrix4f computeWorldMatrix(BedrockPart part, Map<BedrockPart, BedrockPart> parents, boolean animated) {
        List<BedrockPart> path = new ArrayList<>();
        BedrockPart bedrockPart = part;
        while (true) {
            BedrockPart current = bedrockPart;
            if (current == null) {
                break;
            }
            path.add(current);
            bedrockPart = parents.get(current);
        }
        Collections.reverse(path);
        Matrix4f result = new Matrix4f();
        Iterator<BedrockPart> it = path.iterator();
        while (it.hasNext()) {
            result.mul(computeLocalMatrix(it.next(), animated));
        }
        return result;
    }

    private static Matrix4f computePathMatrix(List<BedrockPart> path, boolean animated) {
        Matrix4f result = new Matrix4f();
        if (path == null) {
            return result;
        }
        for (BedrockPart part : path) {
            if (part != null) {
                result.mul(computeLocalMatrix(part, animated));
            }
        }
        return result;
    }

    private static void applyLocalMatrix(BedrockPart target, Matrix4f desiredLocal) {
        if (!isFinite(desiredLocal)) {
            return;
        }
        Vector3f translation = desiredLocal.getTranslation(new Vector3f());
        Vector3f scale = desiredLocal.getScale(new Vector3f());
        if (!isFinite(translation) || !isFinite(scale) || scale.x() < 1.0E-5f || scale.y() < 1.0E-5f || scale.z() < 1.0E-5f) {
            return;
        }
        Quaternionf desiredRotation = desiredLocal.getUnnormalizedRotation(new Quaternionf()).normalize();
        Quaternionf staticInverse = staticRotation(target).conjugate();
        Quaternionf additional = staticInverse.mul(desiredRotation).normalize();
        if (!isFinite(additional)) {
            return;
        }
        target.offsetX = translation.x() - (target.x / 16.0f);
        target.offsetY = translation.y() - (target.y / 16.0f);
        target.offsetZ = translation.z() - (target.z / 16.0f);
        target.additionalQuaternion.set(additional);
        target.xScale = scale.x();
        target.yScale = scale.y();
        target.zScale = scale.z();
    }

    private static Quaternionf staticRotation(BedrockPart part) {
        return new Quaternionf().rotateZ(part.zRot).rotateY(part.yRot).rotateX(part.xRot);
    }

    private static void mirrorLocalAnimationDelta(BedrockPart part) {
        part.offsetX = -part.offsetX;
        Quaternionf staticRotation = staticRotation(part);
        Quaternionf parentSpaceDelta = new Quaternionf(staticRotation).mul(part.additionalQuaternion).mul(new Quaternionf(staticRotation).conjugate());
        parentSpaceDelta.set(parentSpaceDelta.x(), -parentSpaceDelta.y(), -parentSpaceDelta.z(), parentSpaceDelta.w());
        Quaternionf reflectedAdditional = new Quaternionf(staticRotation).conjugate().mul(parentSpaceDelta).mul(staticRotation);
        if (reflectedAdditional.lengthSquared() > 1.0E-12f && isFinite(reflectedAdditional)) {
            part.additionalQuaternion.set(reflectedAdditional.normalize());
        }
    }

    private static boolean isFinite(Matrix4f matrix) {
        return Float.isFinite(matrix.m00()) && Float.isFinite(matrix.m01()) && Float.isFinite(matrix.m02()) && Float.isFinite(matrix.m03()) && Float.isFinite(matrix.m10()) && Float.isFinite(matrix.m11()) && Float.isFinite(matrix.m12()) && Float.isFinite(matrix.m13()) && Float.isFinite(matrix.m20()) && Float.isFinite(matrix.m21()) && Float.isFinite(matrix.m22()) && Float.isFinite(matrix.m23()) && Float.isFinite(matrix.m30()) && Float.isFinite(matrix.m31()) && Float.isFinite(matrix.m32()) && Float.isFinite(matrix.m33());
    }

    private static boolean isFinite(Vector3f vector) {
        return Float.isFinite(vector.x()) && Float.isFinite(vector.y()) && Float.isFinite(vector.z());
    }

    private static boolean isFinite(Quaternionf quaternion) {
        return Float.isFinite(quaternion.x()) && Float.isFinite(quaternion.y()) && Float.isFinite(quaternion.z()) && Float.isFinite(quaternion.w());
    }

    private static float outwardEnvelope(float time, float duration, float ramp) {
        float alpha;
        if (time < ramp) {
            alpha = time / ramp;
        } else if (time > duration - ramp) {
            alpha = (duration - time) / ramp;
        } else {
            return 1.0f;
        }
        float alpha2 = Math.max(0.0f, Math.min(1.0f, alpha));
        return alpha2 * alpha2 * (3.0f - (2.0f * alpha2));
    }

    private static void addExternalAmmunitionFollowers(ObjectAnimation prototype, FilterPlan plan, Map<String, BedrockPart> nodesByName, Map<BedrockPart, BedrockPart> parents, BedrockPart holdingHandEndpoint) {
        BedrockPart carrier;
        if (prototype == null || plan.gunAnchor == null || plan.gunMotionCarrier == null) {
            return;
        }
        for (String nodeName : prototype.getChannels().keySet()) {
            BedrockPart part = nodesByName.get(nodeName);
            if (part != null && !isAncestor(plan.gunMotionCarrier, part, parents) && !isAncestor(part, plan.gunMotionCarrier, parents) && !isAncestor(part, holdingHandEndpoint, parents) && hasAmmunitionOperationPath(part, plan.commonCarrier, parents) && !hasDetachedPropPath(part, plan.commonCarrier, parents) && (carrier = findExternalAmmunitionCarrier(part, plan.gunAnchor, parents, holdingHandEndpoint)) != null && !isAncestor(plan.gunMotionCarrier, carrier, parents) && !isAncestor(carrier, plan.gunMotionCarrier, parents) && !isAncestor(carrier, holdingHandEndpoint, parents)) {
                addOutermostFollower(plan.freezeSubtrees, carrier, parents);
            }
        }
    }

    private static boolean hasAmmunitionOperationPath(BedrockPart part, BedrockPart stop, Map<BedrockPart, BedrockPart> parents) {
        BedrockPart bedrockPart = part;
        while (true) {
            BedrockPart current = bedrockPart;
            if (current != null && current != stop) {
                if (!isAmmunitionOperationName(current.name)) {
                    bedrockPart = parents.get(current);
                } else {
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    private static boolean hasDetachedPropPath(BedrockPart part, BedrockPart stop, Map<BedrockPart, BedrockPart> parents) {
        BedrockPart bedrockPart = part;
        while (true) {
            BedrockPart current = bedrockPart;
            if (current != null && current != stop) {
                if (!isClearlyDetachedPropName(current.name)) {
                    bedrockPart = parents.get(current);
                } else {
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    private static BedrockPart findExternalAmmunitionCarrier(BedrockPart member, BedrockPart gunAnchor, Map<BedrockPart, BedrockPart> parents, BedrockPart holdingHandEndpoint) {
        BedrockPart branchPoint = lowestCommonAncestor(member, gunAnchor, parents);
        if (branchPoint == null || isAncestor(member, gunAnchor, parents)) {
            return null;
        }
        BedrockPart carrier = member;
        BedrockPart bedrockPart = parents.get(member);
        while (true) {
            BedrockPart parent = bedrockPart;
            if (parent == null || parent == branchPoint || isAncestor(parent, holdingHandEndpoint, parents) || isAncestor(parent, gunAnchor, parents) || countRenderableGeometry(parent, member) > 0) {
                break;
            }
            carrier = parent;
            bedrockPart = parents.get(parent);
        }
        return carrier;
    }

    private static void addOutermostFollower(Set<BedrockPart> followers, BedrockPart candidate, Map<BedrockPart, BedrockPart> parents) {
        Iterator it = new ArrayList(followers).iterator();
        while (it.hasNext()) {
            BedrockPart existing = (BedrockPart) it.next();
            if (isAncestor(existing, candidate, parents)) {
                return;
            }
            if (isAncestor(candidate, existing, parents)) {
                followers.remove(existing);
            }
        }
        followers.add(candidate);
    }

    private static void addIndependentBranch(Set<BedrockPart> result, BedrockPart member, BedrockPart gunAnchor, Map<BedrockPart, BedrockPart> parents) {
        BedrockPart branchPoint;
        if (member == null || gunAnchor == null || (branchPoint = lowestCommonAncestor(member, gunAnchor, parents)) == null || isAncestor(member, gunAnchor, parents)) {
            return;
        }
        BedrockPart carrier = member;
        BedrockPart bedrockPart = parents.get(member);
        while (true) {
            BedrockPart parent = bedrockPart;
            if (parent == null || parent == branchPoint || isAncestor(parent, gunAnchor, parents) || countRenderableGeometry(parent, member) > 0) {
                break;
            }
            carrier = parent;
            bedrockPart = parents.get(parent);
        }
        if (!isAncestor(carrier, gunAnchor, parents)) {
            result.add(carrier);
        }
    }

    private static BedrockPart selectStableGunCarrier(BedrockPart ancestor, BedrockPart descendant, BedrockPart installedMagazine, Map<BedrockPart, BedrockPart> parents) {
        List<BedrockPart> path = pathFromAncestor(ancestor, descendant, parents);
        BedrockPart deepestGeometryCarrier = null;
        int bestGeometry = -1;
        for (int index = 1; index < path.size(); index++) {
            BedrockPart part = path.get(index);
            int geometry = countRenderableGeometry(part, installedMagazine);
            if (geometry > 0 && geometry >= bestGeometry) {
                deepestGeometryCarrier = part;
                bestGeometry = geometry;
            }
        }
        if (deepestGeometryCarrier == null && !path.isEmpty()) {
            return path.get(0);
        }
        return deepestGeometryCarrier;
    }

    private static int countRenderableGeometry(BedrockPart part, BedrockPart excludedSubtree) {
        if (part == null || part == excludedSubtree || isClearlyDetachedPropName(part.name)) {
            return 0;
        }
        int result = (isPureHandGeometryName(part.name) || isGeometryExcludedBranchName(part.name)) ? 0 : part.cubes.size();
        ObjectListIterator it = part.children.iterator();
        while (it.hasNext()) {
            BedrockPart child = (BedrockPart) it.next();
            result += countRenderableGeometry(child, excludedSubtree);
        }
        return result;
    }

    private static boolean isFilteredOperationPath(BedrockPart part, BedrockPart stop, BedrockPart gunAnchor, Map<BedrockPart, BedrockPart> parents) {
        if (part == null || isAncestor(part, gunAnchor, parents)) {
            return false;
        }
        BedrockPart bedrockPart = part;
        while (true) {
            BedrockPart current = bedrockPart;
            if (current != null && current != stop) {
                if (!isForbiddenName(current.name)) {
                    bedrockPart = parents.get(current);
                } else {
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    private static List<BedrockPart> pathFromAncestor(BedrockPart ancestor, BedrockPart descendant, Map<BedrockPart, BedrockPart> parents) {
        if (ancestor == null || descendant == null || !isAncestor(ancestor, descendant, parents)) {
            return Collections.emptyList();
        }
        List<BedrockPart> reversed = new ArrayList<>();
        BedrockPart bedrockPart = descendant;
        while (true) {
            BedrockPart current = bedrockPart;
            if (current == null || current == ancestor) {
                break;
            }
            reversed.add(current);
            bedrockPart = parents.get(current);
        }
        reversed.add(ancestor);
        Collections.reverse(reversed);
        return reversed;
    }

    private static BedrockPart lowestCommonAncestor(BedrockPart first, BedrockPart second, Map<BedrockPart, BedrockPart> parents) {
        if (first == null || second == null) {
            return null;
        }
        Set<BedrockPart> ancestors = Collections.newSetFromMap(new IdentityHashMap());
        BedrockPart bedrockPart = first;
        while (true) {
            BedrockPart current = bedrockPart;
            if (current == null) {
                break;
            }
            ancestors.add(current);
            bedrockPart = parents.get(current);
        }
        BedrockPart bedrockPart2 = second;
        while (true) {
            BedrockPart current2 = bedrockPart2;
            if (current2 != null) {
                if (!ancestors.contains(current2)) {
                    bedrockPart2 = parents.get(current2);
                } else {
                    return current2;
                }
            } else {
                return null;
            }
        }
    }

    private static boolean isAncestor(BedrockPart ancestor, BedrockPart descendant, Map<BedrockPart, BedrockPart> parents) {
        if (ancestor == null || descendant == null) {
            return false;
        }
        BedrockPart bedrockPart = descendant;
        while (true) {
            BedrockPart current = bedrockPart;
            if (current != null) {
                if (current != ancestor) {
                    bedrockPart = parents.get(current);
                } else {
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    private static BedrockPart findNodeNormalized(Map<String, BedrockPart> nodesByName, String normalizedName) {
        for (Map.Entry<String, BedrockPart> entry : nodesByName.entrySet()) {
            if (normalize(entry.getKey()).equals(normalizedName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static BedrockPart findHoldingHandEndpoint(Map<String, BedrockPart> nodes, Map<BedrockPart, BedrockPart> parents) {
        String[] candidates = {"righthandpos", "righthandposition", "handrightpos", "handrightposition", "rhandpos", "rhandposition", "mainhandpos", "mainhandposition", "holdinghandpos", "holdinghandposition", "rightarmpos", "rightarmposition"};
        for (String candidate : candidates) {
            BedrockPart found = findNodeNormalized(nodes, candidate);
            if (found != null) {
                return found;
            }
        }
        for (BedrockPart part : nodes.values()) {
            String name = normalize(part.name);
            if (!name.equals("pos") && !name.equals("position")) {
                continue;
            }
            BedrockPart current = parents.get(part);
            while (current != null) {
                if (isHoldingHandControlName(normalize(current.name))) {
                    return part;
                }
                current = parents.get(current);
            }
        }
        return null;
    }

    private static boolean isHoldingHandControlName(String normalizedName) {
        return normalizedName.equals("righthand") || normalizedName.equals("handright") || normalizedName.equals("rhand") || normalizedName.equals("mainhand") || normalizedName.equals("holdinghand") || normalizedName.equals("rightarm");
    }

    private static boolean isHoldingEndpointControlPath(BedrockPart part, BedrockPart holdingHandEndpoint, Map<BedrockPart, BedrockPart> parents) {
        if (part == null || holdingHandEndpoint == null) {
            return false;
        }
        BedrockPart holdingControl = null;
        BedrockPart bedrockPart = parents.get(holdingHandEndpoint);
        while (true) {
            BedrockPart current = bedrockPart;
            if (current == null) {
                break;
            }
            if (!isHoldingHandControlName(normalize(current.name))) {
                bedrockPart = parents.get(current);
            } else {
                holdingControl = current;
                break;
            }
        }
        if (holdingControl == null) {
            return part == holdingHandEndpoint;
        }
        return pathFromAncestor(holdingControl, holdingHandEndpoint, parents).contains(part);
    }

    private static BedrockPart findInstalledMagazine(Map<String, BedrockPart> nodesByName) {
        BedrockPart installed = findNodeNormalized(nodesByName, "magazine");
        if (installed == null) {
            installed = findNodeNormalized(nodesByName, "magzine");
        }
        if (installed == null) {
            installed = findNodeNormalized(nodesByName, "mag");
        }
        if (installed == null) {
            installed = findNodeNormalized(nodesByName, "magandbullet");
        }
        if (installed == null) {
            installed = findNodeNormalized(nodesByName, "magazineandbullet");
        }
        if (installed == null) {
            installed = findNodeNormalized(nodesByName, "magandammo");
        }
        if (installed == null) {
            installed = findNodeNormalized(nodesByName, "magazineandammo");
        }
        return installed;
    }

    private static BedrockPart findGunAnchor(BedrockAnimatedModel model, BedrockPart modelRoot, BedrockPart installedMagazine, Map<BedrockPart, BedrockPart> parents) {
        BedrockPart muzzleFallback = null;
        if (model instanceof BedrockGunModel) {
            BedrockGunModel gunModel = (BedrockGunModel) model;
            List<BedrockPart> muzzlePath = gunModel.getMuzzleFlashPosPath();
            if (muzzlePath != null && !muzzlePath.isEmpty()) {
                BedrockPart muzzle = muzzlePath.get(muzzlePath.size() - 1);
                if (isAncestor(modelRoot, muzzle, parents)) {
                    if (hasRenderableCarrierBelowRoot(modelRoot, muzzle, installedMagazine, parents)) {
                        return muzzle;
                    }
                    muzzleFallback = muzzle;
                }
            }
        }
        List<BedrockPart> constraintPath = model.getConstraintPath();
        if (constraintPath != null && !constraintPath.isEmpty()) {
            BedrockPart constraint = constraintPath.get(constraintPath.size() - 1);
            if (isAncestor(modelRoot, constraint, parents) && hasRenderableCarrierBelowRoot(modelRoot, constraint, installedMagazine, parents)) {
                return constraint;
            }
        }
        BedrockPart magazineGunAnchor = findMagazineGunAncestor(modelRoot, installedMagazine, parents);
        if (magazineGunAnchor != null) {
            return magazineGunAnchor;
        }
        BedrockPart geometryAnchor = findBestGunGeometryAnchor(modelRoot, installedMagazine);
        if (geometryAnchor != null) {
            return geometryAnchor;
        }
        BedrockPart rightHand = model.getNode("righthand_pos");
        if (isAncestor(modelRoot, rightHand, parents) && hasRenderableCarrierBelowRoot(modelRoot, rightHand, installedMagazine, parents)) {
            return rightHand;
        }
        return muzzleFallback;
    }

    private static boolean hasRenderableCarrierBelowRoot(BedrockPart modelRoot, BedrockPart endpoint, BedrockPart installedMagazine, Map<BedrockPart, BedrockPart> parents) {
        List<BedrockPart> path = pathFromAncestor(modelRoot, endpoint, parents);
        for (int index = 1; index < path.size(); index++) {
            if (countRenderableGeometry(path.get(index), installedMagazine) > 0) {
                return true;
            }
        }
        return false;
    }

    private static BedrockPart findBestGunGeometryAnchor(BedrockPart modelRoot, BedrockPart installedMagazine) {
        GeometryCandidate best = new GeometryCandidate(null, 0, -1);
        ObjectListIterator it = modelRoot.children.iterator();
        while (it.hasNext()) {
            BedrockPart child = (BedrockPart) it.next();
            collectBestGunGeometry(child, installedMagazine, 1, best);
        }
        return best.part;
    }

    private static void collectBestGunGeometry(BedrockPart part, BedrockPart installedMagazine, int depth, GeometryCandidate best) {
        if (part == null || part == installedMagazine || isGeometryExcludedBranchName(part.name)) {
            return;
        }
        int geometry = countRenderableGeometry(part, installedMagazine);
        if (geometry > 0 && (geometry > best.geometry || (geometry == best.geometry && depth > best.depth))) {
            best.part = part;
            best.geometry = geometry;
            best.depth = depth;
        }
        ObjectListIterator it = part.children.iterator();
        while (it.hasNext()) {
            BedrockPart child = (BedrockPart) it.next();
            collectBestGunGeometry(child, installedMagazine, depth + 1, best);
        }
    }

    private static BedrockPart findMagazineGunAncestor(BedrockPart modelRoot, BedrockPart installedMagazine, Map<BedrockPart, BedrockPart> parents) {
        if (installedMagazine == null) {
            return null;
        }
        List<BedrockPart> path = pathFromAncestor(modelRoot, installedMagazine, parents);
        GeometryCandidate best = new GeometryCandidate(null, 0, -1);
        for (int index = 1; index + 1 < path.size(); index++) {
            BedrockPart part = path.get(index);
            int geometry = countRenderableGeometry(part, installedMagazine);
            if (geometry > 0 && (geometry > best.geometry || (geometry == best.geometry && index > best.depth))) {
                best.part = part;
                best.geometry = geometry;
                best.depth = index;
            }
        }
        return best.part;
    }

    private static void indexTree(BedrockPart part, BedrockPart parent, Map<String, BedrockPart> nodesByName, Map<BedrockPart, BedrockPart> parents) {
        if (part == null) {
            return;
        }
        if (part.name != null) {
            nodesByName.put(part.name, part);
        }
        if (parent != null) {
            parents.put(part, parent);
        }
        ObjectListIterator it = part.children.iterator();
        while (it.hasNext()) {
            BedrockPart child = (BedrockPart) it.next();
            indexTree(child, part, nodesByName, parents);
        }
    }

    private static void collectSubtreeNames(BedrockPart part, Set<String> names) {
        if (part == null) {
            return;
        }
        if (part.name != null) {
            names.add(part.name);
        }
        ObjectListIterator it = part.children.iterator();
        while (it.hasNext()) {
            BedrockPart child = (BedrockPart) it.next();
            collectSubtreeNames(child, names);
        }
    }

    private static void hideSupportHands(ObjectAnimation filtered, BedrockPart modelRoot, Map<BedrockPart, BedrockPart> parents, BedrockPart gunAnchor, BedrockPart installedMagazine) {
        Set<BedrockPart> allParts = Collections.newSetFromMap(new IdentityHashMap());
        allParts.add(modelRoot);
        allParts.addAll(parents.keySet());
        for (BedrockPart part : allParts) {
            if (isSafeSupportHideCandidate(part, gunAnchor, installedMagazine, parents)) {
                BedrockPart parent = parents.get(part);
                if (!isSafeSupportHideCandidate(parent, gunAnchor, installedMagazine, parents)) {
                    forceHiddenScale(filtered, part.name);
                }
            }
        }
    }

    private static boolean isSafeSupportHideCandidate(BedrockPart part, BedrockPart gunAnchor, BedrockPart installedMagazine, Map<BedrockPart, BedrockPart> parents) {
        return (part == null || part.name == null || !isSupportHandName(part.name) || isAmmunitionOperationName(part.name) || isAncestor(part, gunAnchor, parents) || isAncestor(part, installedMagazine, parents) || !hasOnlySupportDescendants(part)) ? false : true;
    }

    private static boolean hasOnlySupportDescendants(BedrockPart part) {
        ObjectListIterator it = part.children.iterator();
        while (it.hasNext()) {
            BedrockPart child = (BedrockPart) it.next();
            if (child.name == null || !isSupportHandName(child.name) || !hasOnlySupportDescendants(child)) {
                return false;
            }
        }
        return true;
    }

    private static void hideDetachedInspectProps(ObjectAnimation filtered, ObjectAnimation prototype, Map<String, BedrockPart> nodesByName, Map<BedrockPart, BedrockPart> parents) {
        Set<String> detachedNodes = new HashSet<>();
        for (String channelNode : prototype.getChannels().keySet()) {
            BedrockPart bedrockPart = nodesByName.get(channelNode);
            while (true) {
                BedrockPart current = bedrockPart;
                if (current == null) {
                    break;
                }
                if (current.name != null && isClearlyDetachedPropName(current.name)) {
                    detachedNodes.add(current.name);
                    break;
                }
                bedrockPart = parents.get(current);
            }
        }
        for (String nodeName : detachedNodes) {
            forceHiddenScale(filtered, nodeName);
        }
    }

    private static void forceHiddenScale(ObjectAnimation animation, String nodeName) {
        ObjectAnimationChannel hiddenScale = new ObjectAnimationChannel(ObjectAnimationChannel.ChannelType.SCALE);
        hiddenScale.node = nodeName;
        hiddenScale.content = new AnimationChannelContent();
        hiddenScale.content.keyframeTimeS = new float[]{0.0f};
        hiddenScale.content.values = new float[][]{new float[]{0.0f, 0.0f, 0.0f}};
        hiddenScale.content.lerpModes = new AnimationChannelContent.LerpMode[]{AnimationChannelContent.LerpMode.LINEAR};
        hiddenScale.interpolator = new CustomInterpolator();
        hiddenScale.interpolator.compile(hiddenScale.content);
        ArrayList arrayList = new ArrayList();
        arrayList.add(hiddenScale);
        animation.getChannels().put(nodeName, arrayList);
    }

    private static boolean isClearlyDetachedPropName(String name) {
        String normalized = normalize(name);
        return normalized.contains("additionalmag") || normalized.contains("sparemag") || normalized.contains("newmag") || normalized.contains("magout") || normalized.contains("ammoout") || normalized.equals("loader") || normalized.equals("loaderandround") || normalized.startsWith("speedloader") || normalized.startsWith("reloader");
    }

    private static boolean isGeometryExcludedBranchName(String name) {
        if (name == null || isClearlyDetachedPropName(name)) {
            return name != null;
        }
        String normalized = normalize(name);
        if (normalized.endsWith("leftmag") || normalized.endsWith("rightmag")) {
            return true;
        }
        String[] tokens = name.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        for (String token : tokens) {
            String base = token.replaceFirst("[0-9]+$", "");
            if (base.equals("magazine") || base.equals("magzine") || base.equals("mag") || base.equals("clip") || base.equals("loader") || base.equals("bullet") || base.equals("bullets") || base.equals("round") || base.equals("cartridge") || base.equals("shell") || base.equals("ammo") || base.equals("flash")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isForbiddenName(String name) {
        String normalized = normalize(name);
        if (isSupportHandName(name)) {
            return true;
        }
        if (name == null) {
            return false;
        }
        String[] tokens = name.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        for (String token : tokens) {
            for (String forbidden : FORBIDDEN_TOKENS) {
                if (token.equals(forbidden) || token.startsWith(forbidden)) {
                    return true;
                }
            }
        }
        return normalized.contains("magazine") || normalized.contains("magzine") || normalized.contains("lefthand") || normalized.contains("supporthand");
    }

    private static boolean isAmmunitionOperationName(String name) {
        if (name == null) {
            return false;
        }
        String normalized = normalize(name);
        if (normalized.contains("magazine") || normalized.contains("magzine")) {
            return true;
        }
        String[] tokens = name.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        for (String token : tokens) {
            for (String ammunition : FORBIDDEN_TOKENS) {
                if (token.equals(ammunition) || token.startsWith(ammunition)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isSupportHandName(String name) {
        String normalized = normalize(name);
        return normalized.contains("lefthand") || normalized.contains("supporthand") || normalized.contains("handsupport") || normalized.equals("lhand");
    }

    private static boolean isPureHandGeometryName(String name) {
        String normalized = normalize(name);
        return normalized.equals("righthand") || normalized.equals("righthandpos") || normalized.equals("handright") || normalized.equals("rhand") || normalized.equals("lefthand") || normalized.equals("lefthandpos") || normalized.equals("supporthand") || normalized.equals("handsupport") || normalized.equals("lhand");
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static final class FilterPlan {
        private final BedrockPart commonCarrier;
        private final BedrockPart gunAnchor;
        private final BedrockPart installedMagazine;
        private final BedrockPart gunMotionCarrier;
        private final Set<BedrockPart> freezeSubtrees;

        private FilterPlan(BedrockPart commonCarrier, BedrockPart gunAnchor, BedrockPart installedMagazine, BedrockPart gunMotionCarrier, Set<BedrockPart> freezeSubtrees) {
            this.commonCarrier = commonCarrier;
            this.gunAnchor = gunAnchor;
            this.installedMagazine = installedMagazine;
            this.gunMotionCarrier = gunMotionCarrier;
            this.freezeSubtrees = freezeSubtrees;
        }
    }

    private static final class GeometryCandidate {
        private BedrockPart part;
        private int geometry;
        private int depth;

        private GeometryCandidate(BedrockPart part, int geometry, int depth) {
            this.part = part;
            this.geometry = geometry;
            this.depth = depth;
        }
    }

    private static final class RuntimePlan {
        private final BedrockPart gunCarrier;
        private final Map<BedrockPart, BedrockPart> parents;
        private final List<RigidFollower> followers;

        private RuntimePlan(BedrockPart gunCarrier, Map<BedrockPart, BedrockPart> parents, List<RigidFollower> followers) {
            this.gunCarrier = gunCarrier;
            this.parents = parents;
            this.followers = followers;
        }
    }

    private static final class RigidFollower {
        private final BedrockPart target;
        private final BedrockPart parent;
        private final Matrix4f bindGunWorld;
        private final Matrix4f bindTargetWorld;

        private RigidFollower(BedrockPart target, BedrockPart parent, Matrix4f bindGunWorld, Matrix4f bindTargetWorld) {
            this.target = target;
            this.parent = parent;
            this.bindGunWorld = bindGunWorld;
            this.bindTargetWorld = bindTargetWorld;
        }
    }

    private static final class InspectSession {
        private final AnimationController controller;
        private final Set<Integer> tracks = new HashSet();
        private final Set<String> clipNames = new HashSet();
        private final Map<String, RuntimePlan> runtimePlans = new HashMap();
        private long continuationUntilMs;
        private long expiresAtMs;

        private InspectSession(AnimationController controller, long expiresAtMs) {
            this.controller = controller;
            this.expiresAtMs = expiresAtMs;
        }
    }
}
