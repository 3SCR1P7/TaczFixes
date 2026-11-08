package com.ssscript.taczfixes.common.config;

import com.google.gson.Gson;
import com.ssscript.taczfixes.TaczFixesMod;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-authoritative config synchronisation.
 *
 * <p>The server snapshots its own values for the gameplay configs that must follow the server
 * config file and sends them to every joining player. The client stores its current local values,
 * then applies the server values in memory through {@link ModConfigSpec.ConfigValue#set(Object)}
 * so every existing {@code Config.X.get()} call site automatically reads the server value. No config
 * file is written on the client, and the original local values are restored on disconnect.
 *
 * <p>The client-side GUI/visual configs stay local; see {@link #shouldSync(String, List)} for the
 * exact policy.
 */
public final class ConfigSync {
    private static final Gson GSON = new Gson();
    private static final String KEY_SEPARATOR = "\u0000";
    private static final int MAX_SYNC_ENTRIES = 4096;

    /** Specs that fully follow the server config. */
    private static final Set<String> SYNCED_SPECS = Set.of(
            "gun_level",
            "limb_damage_multiplier",
            "inaccuracy_modifier",
            "bullet_ricochet",
            "recoil_modifier",
            "recoil_knockback",
            "gun_spell",
            "blocking",
            "auto_craft",
            "compat",
            "gun_enchantment",
            "aiming_stamina",
            "stamina",
            "dual_wield");

    /** Individual entries that stay local (client GUI / visual settings). */
    private static final Set<String> EXCLUDED_LEAVES = Set.of(
            "gun_translucent_render",
            "auto_aim_when_peeking",
            "button_offset_x",
            "button_offset_y",
            "button_size",
            "leftGunXOffset",
            "rightGunXOffset",
            "forwardOffsetPistol",
            "forwardOffsetOther");

    private static final String REFIT_SPEC = "refit";
    private static final String VIRTUAL_ATTACHMENTS_LEAF = "virtual_attachments";
    private static final String GUN_PACK_SECTION = "gun_pack";

    private static List<Bound> bounds;
    private static Map<String, Bound> boundsByKey;
    private static final Map<String, Object> CLIENT_ORIGINALS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, Object> CLIENT_SYNCED_VALUES = new java.util.concurrent.ConcurrentHashMap<>();

    public record Entry(String spec, List<String> path, String json) {
    }

    private record Bound(String spec, List<String> path, ModConfigSpec.ConfigValue<?> value) {
    }

    private ConfigSync() {
    }

    /** Whether the given spec entry must follow the server config file. */
    public static boolean shouldSync(String specKey, List<String> path) {
        if (specKey == null || path == null || path.isEmpty()) {
            return false;
        }
        if (REFIT_SPEC.equals(specKey)) {
            // refit: only virtual attachments are server-authoritative, the rest is the refit GUI.
            return path.size() == 2 && VIRTUAL_ATTACHMENTS_LEAF.equals(path.get(1));
        }
        if (!SYNCED_SPECS.contains(specKey)) {
            return false;
        }
        if (path.contains(GUN_PACK_SECTION)) {
            // compat/gun_pack: gun pack manager button layout is client GUI only.
            return false;
        }
        return !EXCLUDED_LEAVES.contains(path.get(path.size() - 1));
    }

    /** Server side: snapshot every synced entry of the current (server) config values. */
    public static List<Entry> snapshot() {
        List<Entry> entries = new ArrayList<>();
        for (Bound bound : bounds()) {
            Object value;
            try {
                value = bound.value().get();
            } catch (RuntimeException exception) {
                continue;
            }
            if (value == null) {
                continue;
            }
            entries.add(new Entry(bound.spec(), List.copyOf(bound.path()), GSON.toJson(value)));
        }
        if (entries.size() > MAX_SYNC_ENTRIES) {
            TaczFixesMod.LOGGER.error("Too many synced config entries ({}), skipping config sync", entries.size());
            return List.of();
        }
        return entries;
    }

    /** Client side: keep the local values, then apply the server values in memory. */
    public static void applyServerValues(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        int failures = 0;
        for (Entry entry : entries) {
            try {
                Bound bound = boundsByKey().get(key(entry.spec(), entry.path()));
                if (bound == null) {
                    failures++;
                    continue;
                }
                Object parsed = deserialize(entry.json(), bound.value());
                if (parsed == null) {
                    failures++;
                    continue;
                }
                String key = key(entry.spec(), entry.path());
                if (!CLIENT_ORIGINALS.containsKey(key)) {
                    CLIENT_ORIGINALS.put(key, copyValue(bound.value().get()));
                }
                CLIENT_SYNCED_VALUES.put(key, parsed);
                setValue(bound.value(), parsed);
            } catch (RuntimeException exception) {
                failures++;
            }
        }
        if (failures > 0) {
            TaczFixesMod.LOGGER.error("Failed to apply {} synced server config entries", failures);
        }
    }

    /** Client side: restore the local config values that were overridden on login. */
    public static void restoreClientLocalValues() {
        if (!CLIENT_ORIGINALS.isEmpty()) {
            for (Map.Entry<String, Object> entry : new java.util.ArrayList<>(CLIENT_ORIGINALS.entrySet())) {
                try {
                    Bound bound = boundsByKey().get(entry.getKey());
                    if (bound != null) {
                        setValue(bound.value(), entry.getValue());
                    }
                } catch (RuntimeException ignored) {
                }
            }
        }
        CLIENT_ORIGINALS.clear();
        CLIENT_SYNCED_VALUES.clear();
    }

    /**
     * Client side: re-apply the server values after the local config was reloaded from disk.
     * Does nothing on the server, where no overrides are stored.
     */
    public static void reapplyClientOverridesIfNeeded() {
        if (CLIENT_SYNCED_VALUES.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : new java.util.ArrayList<>(CLIENT_SYNCED_VALUES.entrySet())) {
            try {
                Bound bound = boundsByKey().get(entry.getKey());
                if (bound != null) {
                    setValue(bound.value(), entry.getValue());
                }
            } catch (RuntimeException ignored) {
            }
        }
    }

    /**
     * Client side: when the config GUI is saved, temporarily write the saved local values back so the
     * server-authoritative values are never persisted into the client config files, then re-apply them.
     */
    public static void saveAllFromConfigScreen() {
        if (CLIENT_SYNCED_VALUES.isEmpty()) {
            Config.saveAll();
            return;
        }
        for (Map.Entry<String, Object> entry : new java.util.ArrayList<>(CLIENT_ORIGINALS.entrySet())) {
            try {
                Bound bound = boundsByKey().get(entry.getKey());
                if (bound != null) {
                    setValue(bound.value(), entry.getValue());
                }
            } catch (RuntimeException ignored) {
            }
        }
        try {
            Config.saveAll();
        } catch (RuntimeException exception) {
            TaczFixesMod.LOGGER.error("Failed to save local config values", exception);
        } finally {
            for (Map.Entry<String, Object> entry : new java.util.ArrayList<>(CLIENT_SYNCED_VALUES.entrySet())) {
                try {
                    Bound bound = boundsByKey().get(entry.getKey());
                    if (bound != null) {
                        setValue(bound.value(), entry.getValue());
                    }
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private static synchronized List<Bound> bounds() {
        if (bounds != null) {
            return bounds;
        }
        List<Bound> collected = new ArrayList<>();
        for (Field field : Config.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !ModConfigSpec.ConfigValue.class.isAssignableFrom(field.getType())) {
                continue;
            }
            Object fieldValue;
            try {
                fieldValue = field.get(null);
            } catch (IllegalAccessException ignored) {
                continue;
            }
            if (!(fieldValue instanceof ModConfigSpec.ConfigValue<?> value)) {
                continue;
            }
            List<String> path;
            try {
                path = value.getPath();
            } catch (RuntimeException exception) {
                continue;
            }
            if (path == null || path.isEmpty()) {
                continue;
            }
            // Every synced spec pushes its own key as the root category, so the first path element
            // identifies the spec. Values of other specs are ignored here.
            String specKey = path.get(0);
            if (!Config.SPECS.containsKey(specKey) || !shouldSync(specKey, path)) {
                continue;
            }
            collected.add(new Bound(specKey, List.copyOf(path), value));
        }
        bounds = List.copyOf(collected);
        return bounds;
    }

    private static synchronized Map<String, Bound> boundsByKey() {
        if (boundsByKey != null) {
            return boundsByKey;
        }
        Map<String, Bound> collected = new LinkedHashMap<>();
        for (Bound bound : bounds()) {
            collected.put(key(bound.spec(), bound.path()), bound);
        }
        boundsByKey = Map.copyOf(collected);
        return boundsByKey;
    }

    private static String key(String spec, List<String> path) {
        return spec + KEY_SEPARATOR + String.join(KEY_SEPARATOR, path);
    }

    private static Object deserialize(String json, ModConfigSpec.ConfigValue<?> value) {
        Object current = null;
        try {
            current = value.get();
        } catch (RuntimeException ignored) {
        }
        if (current == null) {
            try {
                current = value.getDefault();
            } catch (RuntimeException ignored) {
            }
        }
        if (json == null) {
            return null;
        }
        try {
            if (current instanceof Boolean) {
                return GSON.fromJson(json, Boolean.class);
            }
            if (current instanceof Integer) {
                return GSON.fromJson(json, Integer.class);
            }
            if (current instanceof Long) {
                return GSON.fromJson(json, Long.class);
            }
            if (current instanceof Double) {
                return GSON.fromJson(json, Double.class);
            }
            if (current instanceof String) {
                return GSON.fromJson(json, String.class);
            }
            if (current instanceof Enum<?> enumeration) {
                String name = GSON.fromJson(json, String.class);
                return name == null ? null : parseEnum(enumeration.getDeclaringClass(), name);
            }
            if (current instanceof List<?>) {
                return GSON.fromJson(json, List.class);
            }
        } catch (RuntimeException exception) {
            return null;
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parseEnum(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumClass, name);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setValue(ModConfigSpec.ConfigValue<?> value, Object raw) {
        ((ModConfigSpec.ConfigValue) value).set(raw);
    }

    private static Object copyValue(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return value;
    }
}
