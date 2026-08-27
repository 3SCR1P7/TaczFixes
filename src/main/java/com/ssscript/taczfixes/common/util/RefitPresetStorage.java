package com.ssscript.taczfixes.common.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.ssscript.taczfixes.common.TaczFixesMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 每把枪的多个命名改装方案。
 * 结构：gunId -> (方案名 -> 槽位 -> 配件 id)
 * 兼容旧的单方案格式（gunId -> 槽位 -> 配件 id），加载时迁移为名为 "default" 的方案。
 */
public final class RefitPresetStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String LEGACY_NAME = "default";

    private static final Map<ResourceLocation, Map<String, Map<String, ResourceLocation>>> PRESETS = new HashMap<>();
    private static boolean loaded;

    private RefitPresetStorage() {
    }

    public static List<String> getPresetNames(ResourceLocation gunId) {
        ensureLoaded();
        Map<String, Map<String, ResourceLocation>> presets = PRESETS.get(gunId);
        if (presets == null || presets.isEmpty()) return List.of();
        return new ArrayList<>(presets.keySet());
    }

    public static Map<String, ResourceLocation> getPreset(ResourceLocation gunId, String name) {
        ensureLoaded();
        Map<String, Map<String, ResourceLocation>> presets = PRESETS.get(gunId);
        if (presets == null) return null;
        Map<String, ResourceLocation> preset = presets.get(name);
        return preset == null ? null : new LinkedHashMap<>(preset);
    }

    public static boolean hasPreset(ResourceLocation gunId, String name) {
        ensureLoaded();
        Map<String, Map<String, ResourceLocation>> presets = PRESETS.get(gunId);
        return presets != null && presets.containsKey(name);
    }

    /** 保存（不存在则新建，已存在则覆盖）并写盘。 */
    public static void savePreset(ResourceLocation gunId, String name, Map<String, ResourceLocation> preset) {
        ensureLoaded();
        PRESETS.computeIfAbsent(gunId, k -> new LinkedHashMap<>())
                .put(name, new LinkedHashMap<>(preset));
        writeFile();
    }

    /** 删除指定方案并写盘；方案不存在时无操作。 */
    public static void removePreset(ResourceLocation gunId, String name) {
        ensureLoaded();
        Map<String, Map<String, ResourceLocation>> presets = PRESETS.get(gunId);
        if (presets == null) return;
        if (presets.remove(name) != null) {
            if (presets.isEmpty()) {
                PRESETS.remove(gunId);
            }
            writeFile();
        }
    }

    /** 生成改装码：base64(gunId + name + slots)。 */
    public static String exportCode(ResourceLocation gunId, String name, Map<String, ResourceLocation> preset) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("gunId", gunId.toString());
        out.put("name", name);
        Map<String, String> slots = new LinkedHashMap<>();
        for (Map.Entry<String, ResourceLocation> slot : preset.entrySet()) {
            slots.put(slot.getKey(), slot.getValue().toString());
        }
        out.put("slots", slots);
        String json = GSON.toJson(out);
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** 解析改装码；格式非法返回 null，合法返回 (gunId, name, slots)。 */
    public static ImportedCode importCode(String code) {
        if (code == null || code.isBlank()) return null;
        try {
            String json = new String(Base64.getDecoder().decode(code.trim()), StandardCharsets.UTF_8);
            Map<String, Object> parsed = GSON.fromJson(json,
                    new TypeToken<Map<String, Object>>() {
                    }.getType());
            if (parsed == null) return null;
            Object gunIdObj = parsed.get("gunId");
            Object nameObj = parsed.get("name");
            Object slotsObj = parsed.get("slots");
            if (!(gunIdObj instanceof String) || !(nameObj instanceof String) || !(slotsObj instanceof Map)) return null;
            ResourceLocation gunId = ResourceLocation.tryParse((String) gunIdObj);
            if (gunId == null) return null;
            Map<String, ResourceLocation> slots = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) slotsObj).entrySet()) {
                if (entry.getKey() instanceof String && entry.getValue() instanceof String) {
                    ResourceLocation id = ResourceLocation.tryParse((String) entry.getValue());
                    if (id != null) slots.put((String) entry.getKey(), id);
                }
            }
            return new ImportedCode(gunId, (String) nameObj, slots);
        } catch (Exception e) {
            TaczFixesMod.LOGGER.warn("taczfixes: failed to parse refit preset code", e);
            return null;
        }
    }

    public record ImportedCode(ResourceLocation gunId, String name, Map<String, ResourceLocation> slots) {
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path file = getFile();
        if (!Files.exists(file)) return;
        try {
            String json = Files.readString(file);
            Map<String, Map<String, Object>> raw = GSON.fromJson(json,
                    new TypeToken<Map<String, Map<String, Object>>>() {
                    }.getType());
            if (raw == null) return;
            for (Map.Entry<String, Map<String, Object>> entry : raw.entrySet()) {
                ResourceLocation gunId = ResourceLocation.tryParse(entry.getKey());
                if (gunId == null || entry.getValue() == null) continue;
                Map<String, Map<String, ResourceLocation>> presets = new LinkedHashMap<>();
                for (Map.Entry<String, Object> nameEntry : entry.getValue().entrySet()) {
                    if (nameEntry.getValue() instanceof Map) {
                        Map<String, ResourceLocation> slots = parseSlots((Map<?, ?>) nameEntry.getValue());
                        if (!slots.isEmpty()) presets.put(nameEntry.getKey(), slots);
                    } else if (nameEntry.getValue() instanceof String) {
                        // 旧格式：gunId -> 槽位 -> id，整体视为一个名为 "default" 的方案
                        Map<String, String> single = new HashMap<>();
                        single.put(nameEntry.getKey(), (String) nameEntry.getValue());
                        Map<String, ResourceLocation> slots = parseSlots(single);
                        if (!slots.isEmpty()) presets.put(LEGACY_NAME, slots);
                    }
                }
                if (!presets.isEmpty()) {
                    PRESETS.put(gunId, presets);
                }
            }
        } catch (Exception e) {
            TaczFixesMod.LOGGER.warn("taczfixes: failed to load refit presets", e);
        }
    }

    private static Map<String, ResourceLocation> parseSlots(Map<?, ?> raw) {
        Map<String, ResourceLocation> slots = new LinkedHashMap<>();
        for (Map.Entry<?, ?> slot : raw.entrySet()) {
            if (slot.getKey() instanceof String && slot.getValue() instanceof String) {
                ResourceLocation id = ResourceLocation.tryParse((String) slot.getValue());
                if (id != null) slots.put((String) slot.getKey(), id);
            }
        }
        return slots;
    }

    private static void writeFile() {
        try {
            Path file = getFile();
            Files.createDirectories(file.getParent());
            Map<String, Map<String, Map<String, String>>> out = new TreeMap<>();
            for (Map.Entry<ResourceLocation, Map<String, Map<String, ResourceLocation>>> entry : PRESETS.entrySet()) {
                Map<String, Map<String, String>> presets = new LinkedHashMap<>();
                for (Map.Entry<String, Map<String, ResourceLocation>> preset : entry.getValue().entrySet()) {
                    Map<String, String> slots = new LinkedHashMap<>();
                    for (Map.Entry<String, ResourceLocation> slot : preset.getValue().entrySet()) {
                        slots.put(slot.getKey(), slot.getValue().toString());
                    }
                    presets.put(preset.getKey(), slots);
                }
                out.put(entry.getKey().toString(), presets);
            }
            Files.writeString(file, GSON.toJson(out));
        } catch (Exception e) {
            TaczFixesMod.LOGGER.warn("taczfixes: failed to save refit presets", e);
        }
    }

    private static Path getFile() {
        return FMLPaths.CONFIGDIR.get().resolve("taczfixes").resolve("refit_presets.json");
    }
}
