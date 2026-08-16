package com.ssscript.taczfixes.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.ssscript.taczfixes.TaczFixesMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class RefitPresetStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<ResourceLocation, Map<String, ResourceLocation>> PRESETS = new HashMap<>();
    private static boolean loaded;

    private RefitPresetStorage() {
    }

    public static Map<String, ResourceLocation> get(ResourceLocation gunId) {
        ensureLoaded();
        Map<String, ResourceLocation> preset = PRESETS.get(gunId);
        return preset == null ? null : new LinkedHashMap<>(preset);
    }

    public static void save(ResourceLocation gunId, Map<String, ResourceLocation> preset) {
        ensureLoaded();
        PRESETS.put(gunId, new LinkedHashMap<>(preset));
        writeFile();
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path file = getFile();
        if (!Files.exists(file)) return;
        try {
            String json = Files.readString(file);
            Map<String, Map<String, String>> raw = GSON.fromJson(json,
                    new TypeToken<Map<String, Map<String, String>>>() {
                    }.getType());
            if (raw == null) return;
            for (Map.Entry<String, Map<String, String>> entry : raw.entrySet()) {
                ResourceLocation gunId = ResourceLocation.tryParse(entry.getKey());
                if (gunId == null || entry.getValue() == null) continue;
                Map<String, ResourceLocation> slots = new LinkedHashMap<>();
                for (Map.Entry<String, String> slot : entry.getValue().entrySet()) {
                    ResourceLocation id = ResourceLocation.tryParse(slot.getValue());
                    if (id != null) slots.put(slot.getKey(), id);
                }
                PRESETS.put(gunId, slots);
            }
        } catch (Exception e) {
            TaczFixesMod.LOGGER.warn("taczfixes: failed to load refit presets", e);
        }
    }

    private static void writeFile() {
        try {
            Path file = getFile();
            Files.createDirectories(file.getParent());
            Map<String, Map<String, String>> out = new TreeMap<>();
            for (Map.Entry<ResourceLocation, Map<String, ResourceLocation>> entry : PRESETS.entrySet()) {
                Map<String, String> slots = new LinkedHashMap<>();
                for (Map.Entry<String, ResourceLocation> slot : entry.getValue().entrySet()) {
                    slots.put(slot.getKey(), slot.getValue().toString());
                }
                out.put(entry.getKey().toString(), slots);
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