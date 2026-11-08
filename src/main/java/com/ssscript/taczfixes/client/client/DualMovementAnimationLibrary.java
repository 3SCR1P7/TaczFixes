package com.ssscript.taczfixes.client.client;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.config.DualMovementAnimation;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.tacz.guns.api.client.animation.Animations;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.animation.bedrock.BedrockAnimationFile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Dual-wield first-person movement animation prototypes.
 *
 * <p>The movement animation preset can be overridden per gun through the gun data field
 * {@code taczfixes.dual_wield.movement_animation} ({@code default_1}, {@code default_2},
 * {@code custom}). When the field is absent or invalid the global config value
 * {@link Config#DUAL_WIELD_MOVEMENT_ANIMATION} is used. {@code custom} means the gun keeps its own
 * movement animations (no replacement prototypes at all).
 *
 * <p>The loaded prototype sets are cached per preset so different guns can use different presets at
 * the same time; main-hand and offhand caches are separate because the two hands use mirrored assets.
 */
@OnlyIn(Dist.CLIENT)
public final class DualMovementAnimationLibrary {
    public static final String PRESET_DEFAULT_1 = "default_1";
    public static final String PRESET_DEFAULT_2 = "default_2";
    public static final String PRESET_CUSTOM = "custom";

    private static final Object LOCK = new Object();
    private static final Map<String, Map<String, ObjectAnimation>> MAIN_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, ObjectAnimation>> OFFHAND_CACHE = new ConcurrentHashMap<>();
    private static final ResourceLocation MAIN_MOVEMENT =
            ResourceLocation.fromNamespaceAndPath(TaczFixesMod.MOD_ID, "right_movement");
    private static final ResourceLocation OFFHAND_MOVEMENT =
            ResourceLocation.fromNamespaceAndPath(TaczFixesMod.MOD_ID, "left_movement");
    private static final ResourceLocation MAIN_MOVEMENT_2 =
            ResourceLocation.fromNamespaceAndPath(TaczFixesMod.MOD_ID, "right_movement_2");
    private static final ResourceLocation OFFHAND_MOVEMENT_2 =
            ResourceLocation.fromNamespaceAndPath(TaczFixesMod.MOD_ID, "left_movement_2");
    private static final Set<String> BONE_ALLOWLIST = Set.of("root");
    private static final Set<String> MOVEMENT_CLIPS = Set.of(
            "run_start", "run", "run_hold", "run_end",
            "walk_aiming", "walk_forward", "walk_sideway", "walk_backward");
    private static final Set<String> REQUIRED_CLIPS = Set.of(
            "run_start", "run", "run_end",
            "walk_forward", "walk_sideway", "walk_backward");

    private DualMovementAnimationLibrary() {
    }

    /**
     * Resolves the movement animation preset for the given gun stack: the per-gun data override when
     * present and valid, otherwise the global config value.
     */
    public static String resolvePreset(ItemStack gunStack) {
        if (gunStack != null && !gunStack.isEmpty()) {
            String perGun = TaczFixesDataManager.resolveMovementAnimation(gunStack);
            if (perGun != null) {
                return perGun;
            }
        }
        return Config.DUAL_WIELD_MOVEMENT_ANIMATION.get() == DualMovementAnimation.DEFAULT_2
                ? PRESET_DEFAULT_2 : PRESET_DEFAULT_1;
    }

    /** Main-hand replacement prototypes for the given gun stack; empty for {@code custom}. */
    public static Map<String, ObjectAnimation> getMainPrototypes(ItemStack gunStack) {
        return getPrototypes(MAIN_CACHE, resolvePreset(gunStack), MAIN_MOVEMENT, MAIN_MOVEMENT_2, "main");
    }

    /** Offhand replacement prototypes for the given gun stack; empty for {@code custom}. */
    public static Map<String, ObjectAnimation> getOffhandPrototypes(ItemStack gunStack) {
        return getPrototypes(OFFHAND_CACHE, resolvePreset(gunStack), OFFHAND_MOVEMENT, OFFHAND_MOVEMENT_2, "offhand");
    }

    /** Names of the movement clips this library may replace (used to restore original offhand prototypes). */
    public static Set<String> movementClipNames() {
        return MOVEMENT_CLIPS;
    }

    public static boolean isMovementClip(String animationName) {
        return animationName != null && MOVEMENT_CLIPS.contains(animationName);
    }

    /** Drops every cached prototype set; called on resource reload. */
    public static void invalidate() {
        synchronized (LOCK) {
            MAIN_CACHE.clear();
            OFFHAND_CACHE.clear();
        }
    }

    private static Map<String, ObjectAnimation> getPrototypes(Map<String, Map<String, ObjectAnimation>> cache,
                                                              String preset, ResourceLocation base,
                                                              ResourceLocation variant, String side) {
        if (PRESET_CUSTOM.equals(preset)) {
            // The gun keeps its own movement animations, no replacement prototypes.
            return Map.of();
        }
        Map<String, ObjectAnimation> cached = cache.get(preset);
        if (cached != null) {
            return cached;
        }
        synchronized (LOCK) {
            cached = cache.get(preset);
            if (cached != null) {
                return cached;
            }
            cached = loadPreset(preset, base, variant, side);
            cache.put(preset, cached);
            return cached;
        }
    }

    /**
     * Loads the selected animation, falling back to the default animation for clips the selected file does not
     * provide (the alternate movement set only overrides the clips it actually defines).
     */
    private static Map<String, ObjectAnimation> loadPreset(String preset, ResourceLocation baseLocation,
                                                           ResourceLocation variantLocation, String side) {
        Map<String, ObjectAnimation> base = loadSingle(baseLocation, side, true);
        if (PRESET_DEFAULT_1.equals(preset)) {
            return base;
        }
        Map<String, ObjectAnimation> variant = loadSingle(variantLocation, side, false);
        if (variant.isEmpty()) {
            return base;
        }
        Map<String, ObjectAnimation> merged = new HashMap<>(base);
        merged.putAll(variant);
        merged.putIfAbsent("run_end", merged.get("run"));
        merged.putIfAbsent("run_hold", merged.get("run"));
        merged.putIfAbsent("walk_aiming", merged.get("walk_forward"));
        if (!merged.keySet().containsAll(REQUIRED_CLIPS)) {
            TaczFixesMod.LOGGER.error("Movement animation variant {} is incomplete, using the default set", variantLocation);
            return base;
        }
        return Map.copyOf(merged);
    }

    private static Map<String, ObjectAnimation> loadSingle(ResourceLocation location, String side, boolean required) {
        BedrockAnimationFile animationFile = ClientAssetsManager.INSTANCE.getBedrockAnimations(location);
        if (animationFile == null) {
            if (required) {
                TaczFixesMod.LOGGER.error("Missing {} movement animation {}", side, location);
            }
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
        if (required && !replacements.keySet().containsAll(REQUIRED_CLIPS)) {
            TaczFixesMod.LOGGER.error("{} movement animation is missing one or more required clips", side);
            return Map.of();
        }
        if (!required) {
            return replacements;
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
