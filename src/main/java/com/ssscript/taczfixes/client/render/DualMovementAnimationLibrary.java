package com.ssscript.taczfixes.client.render;

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
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/render/DualMovementAnimationLibrary.class */
public final class DualMovementAnimationLibrary {
    private static final String ASSET_PREFIX = "dual_";
    private static volatile Map<String, ObjectAnimation> cachedPrototypes;
    private static final ResourceLocation MOVEMENT_ANIMATION = new ResourceLocation(TaczFixesMod.MOD_ID, "dual_wield_movement");
    private static final Set<String> BONE_ALLOWLIST = Set.of("root");
    private static final Set<String> MOVEMENT_CLIPS = Set.of("run_start", "run", "run_hold", "run_end", "walk_aiming", "walk_forward", "walk_sideway", "walk_backward");

    private DualMovementAnimationLibrary() {
    }

    public static Map<String, ObjectAnimation> getPrototypes() {
        Map<String, ObjectAnimation> current = cachedPrototypes;
        if (current != null) {
            return current;
        }
        synchronized (DualMovementAnimationLibrary.class) {
            Map<String, ObjectAnimation> current2 = cachedPrototypes;
            if (current2 != null) {
                return current2;
            }
            BedrockAnimationFile animationFile = ClientAssetsManager.INSTANCE.getBedrockAnimations(MOVEMENT_ANIMATION);
            if (animationFile == null) {
                return Map.of();
            }
            Map<String, ObjectAnimation> loaded = loadPrototypes(animationFile);
            cachedPrototypes = loaded;
            return loaded;
        }
    }

    public static boolean isMovementClip(String animationName) {
        return animationName != null && MOVEMENT_CLIPS.contains(animationName);
    }

    public static void invalidate() {
        synchronized (DualMovementAnimationLibrary.class) {
            cachedPrototypes = null;
        }
    }

    private static Map<String, ObjectAnimation> loadPrototypes(BedrockAnimationFile animationFile) {
        Map<String, ObjectAnimation> replacements = new HashMap<>();
        List<ObjectAnimation> animations = Animations.createAnimationFromBedrock(animationFile);
        for (ObjectAnimation animation : animations) {
            if (animation.name.startsWith(ASSET_PREFIX)) {
                String nativeName = animation.name.substring(ASSET_PREFIX.length());
                if (MOVEMENT_CLIPS.contains(nativeName)) {
                    animation.getChannels().keySet().removeIf(node -> {
                        return !BONE_ALLOWLIST.contains(node);
                    });
                    if (animation.getChannels().containsKey("root")) {
                        replacements.put(nativeName, animation);
                    }
                }
            }
        }
        if (!replacements.keySet().containsAll(MOVEMENT_CLIPS)) {
            TaczFixesMod.LOGGER.error("Dual movement animation is missing one or more required clips");
            return Map.of();
        }
        return Map.copyOf(replacements);
    }
}
