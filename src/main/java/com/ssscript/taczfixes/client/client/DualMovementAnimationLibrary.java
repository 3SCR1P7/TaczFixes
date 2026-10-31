package com.ssscript.taczfixes.client.client;

import com.tacz.guns.api.client.animation.Animations;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.animation.bedrock.BedrockAnimationFile;
import com.ssscript.taczfixes.TaczFixesMod;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DualMovementAnimationLibrary {
    private static volatile Map<String, ObjectAnimation> cachedMain;
    private static volatile Map<String, ObjectAnimation> cachedOffhand;
    private static final ResourceLocation MAIN_MOVEMENT =
            ResourceLocation.fromNamespaceAndPath(TaczFixesMod.MOD_ID, "right_movement");
    private static final ResourceLocation OFFHAND_MOVEMENT =
            ResourceLocation.fromNamespaceAndPath(TaczFixesMod.MOD_ID, "left_movement");
    private static final Set<String> BONE_ALLOWLIST = Set.of("root");
    private static final Set<String> MOVEMENT_CLIPS = Set.of(
            "run_start", "run", "run_hold", "run_end",
            "walk_aiming", "walk_forward", "walk_sideway", "walk_backward");
    private static final Set<String> REQUIRED_CLIPS = Set.of(
            "run_start", "run", "run_end",
            "walk_forward", "walk_sideway", "walk_backward");

    private DualMovementAnimationLibrary() {
    }

    public static Map<String, ObjectAnimation> getMainPrototypes() {
        Map<String, ObjectAnimation> current = cachedMain;
        if (current != null) {
            return current;
        }
        synchronized (DualMovementAnimationLibrary.class) {
            if (cachedMain != null) {
                return cachedMain;
            }
            cachedMain = loadPrototypes(MAIN_MOVEMENT, "main");
            return cachedMain;
        }
    }

    public static Map<String, ObjectAnimation> getOffhandPrototypes() {
        Map<String, ObjectAnimation> current = cachedOffhand;
        if (current != null) {
            return current;
        }
        synchronized (DualMovementAnimationLibrary.class) {
            if (cachedOffhand != null) {
                return cachedOffhand;
            }
            cachedOffhand = loadPrototypes(OFFHAND_MOVEMENT, "offhand");
            return cachedOffhand;
        }
    }

    public static boolean isMovementClip(String animationName) {
        return animationName != null && MOVEMENT_CLIPS.contains(animationName);
    }

    public static void invalidate() {
        synchronized (DualMovementAnimationLibrary.class) {
            cachedMain = null;
            cachedOffhand = null;
        }
    }

    private static Map<String, ObjectAnimation> loadPrototypes(ResourceLocation location, String side) {
        BedrockAnimationFile animationFile = ClientAssetsManager.INSTANCE.getBedrockAnimations(location);
        if (animationFile == null) {
            TaczFixesMod.LOGGER.error("Missing {} movement animation {}", side, location);
            return Map.of();
        }
        Map<String, ObjectAnimation> replacements = new HashMap<>();
        List<ObjectAnimation> animations = Animations.createAnimationFromBedrock(animationFile);
        for (ObjectAnimation animation : animations) {
            if (!MOVEMENT_CLIPS.contains(animation.name)) {
                continue;
            }
            animation.getChannels().keySet().removeIf(node -> !BONE_ALLOWLIST.contains(node));
            if (animation.getChannels().containsKey("root")) {
                replacements.put(animation.name, animation);
            }
        }
        if (!replacements.keySet().containsAll(REQUIRED_CLIPS)) {
            TaczFixesMod.LOGGER.error("{} movement animation is missing one or more required clips", side);
            return Map.of();
        }
        if (!replacements.containsKey("run_hold")) {
            replacements.put("run_hold", replacements.get("run"));
        }
        if (!replacements.containsKey("walk_aiming")) {
            replacements.put("walk_aiming", replacements.get("walk_forward"));
        }
        return Map.copyOf(replacements);
    }
}
