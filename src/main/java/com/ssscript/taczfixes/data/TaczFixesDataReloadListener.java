package com.ssscript.taczfixes.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.util.ResourceScanner;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class TaczFixesDataReloadListener implements PreparableReloadListener {
    private static final Logger LOGGER = LogManager.getLogger("taczfixes");
    private static final Gson GSON = new Gson();

    @Override
    public CompletableFuture<Void> reload(PreparableReloadListener.PreparationBarrier barrier,
                                          ResourceManager resourceManager, ProfilerFiller prepareProfiler,
                                          ProfilerFiller applyProfiler, Executor backgroundExecutor,
                                          Executor gameExecutor) {
        return CompletableFuture.supplyAsync(() -> scan(resourceManager), backgroundExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(result -> {
                    TaczFixesDataManager.putAll(result.gunData);
                    CustomSlotManager.putAllTags(result.allowTags);
                }, gameExecutor);
    }

    private static ScanResult scan(ResourceManager resourceManager) {
        ScanResult result = new ScanResult();
        Map<ResourceLocation, JsonElement> all = ResourceScanner.scanDirectory(resourceManager, "data/guns", GSON);
        for (Map.Entry<ResourceLocation, JsonElement> entry : all.entrySet()) {
            JsonElement element = entry.getValue();
            if (element == null || !element.isJsonObject()) continue;
            JsonObject root = element.getAsJsonObject();
            if (!root.has("taczfixes")) continue;
            try {
                GunTaczFixesData data = GSON.fromJson(root.get("taczfixes"), GunTaczFixesData.class);
                if (data != null) {
                    JsonElement tfElement = root.get("taczfixes");
                    if (tfElement.isJsonObject()) {
                        JsonElement rmElement = tfElement.getAsJsonObject().get("recoil_multiplier");
                        if (rmElement != null && rmElement.isJsonObject()) {
                            collectRecoilModifiers(data, rmElement.getAsJsonObject());
                        }
                    }
                    result.gunData.put(entry.getKey(), data);
                }
            } catch (Exception ex) {
                LOGGER.error("taczfixes: failed to parse taczfixes of gun data {}", entry.getKey(), ex);
            }
        }
        Map<ResourceLocation, JsonElement> tags =
                ResourceScanner.scanDirectory(resourceManager, "tacz_tags/attachments/allow_attachments", GSON);
        for (Map.Entry<ResourceLocation, JsonElement> entry : tags.entrySet()) {
            JsonElement element = entry.getValue();
            if (element == null || !element.isJsonArray()) continue;
            Set<ResourceLocation> ids = new HashSet<>();
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                if (item == null || !item.isJsonPrimitive()) continue;
                ResourceLocation id = ResourceLocation.tryParse(item.getAsString());
                if (id != null) {
                    ids.add(id);
                }
            }
            result.allowTags.put(entry.getKey(), ids);
        }
        return result;
    }

    private static void collectRecoilModifiers(GunTaczFixesData data, JsonObject recoilRoot) {
        if (data.recoil_multiplier == null) return;
        for (Map.Entry<String, JsonElement> modeEntry : recoilRoot.entrySet()) {
            GunTaczFixesData.RecoilConfig config = data.recoil_multiplier.get(modeEntry.getKey());
            if (config == null || !modeEntry.getValue().isJsonObject()) continue;
            JsonObject modeObj = modeEntry.getValue().getAsJsonObject();
            Map<String, GunTaczFixesData.RecoilModifierConfig> mods = new HashMap<>();
            for (Map.Entry<String, JsonElement> modEntry : modeObj.entrySet()) {
                String key = modEntry.getKey();
                if ("window".equals(key) || "pitch_multiplier".equals(key)
                        || "yaw_multiplier".equals(key) || "modifiers".equals(key)) continue;
                if (!modEntry.getValue().isJsonObject()) continue;
                GunTaczFixesData.RecoilModifierConfig mod =
                        parseRecoilModifier(modEntry.getValue().getAsJsonObject());
                if (mod != null) {
                    mods.put(key, mod);
                } else {
                    LOGGER.warn("taczfixes: recoil modifier {} of mode {} ignored, invalid count", key, modeEntry.getKey());
                }
            }
            if (mods.isEmpty()) continue;
            if (config.modifiers == null) {
                config.modifiers = mods;
            } else {
                config.modifiers.putAll(mods);
            }
        }
    }

    private static GunTaczFixesData.RecoilModifierConfig parseRecoilModifier(JsonObject modObj) {
        GunTaczFixesData.RecoilModifierConfig mod = new GunTaczFixesData.RecoilModifierConfig();
        JsonElement countEl = modObj.get("count");
        if (countEl != null) {
            if (countEl.isJsonArray()) {
                JsonArray arr = countEl.getAsJsonArray();
                if (arr.size() < 1) {
                    return null;
                }
                for (JsonElement element : arr) {
                    if (!element.isJsonPrimitive()) {
                        return null;
                    }
                }
                mod.count_start = arr.get(0).getAsInt();
                if (arr.size() >= 2) {
                    mod.count_end = arr.get(1).getAsInt();
                }
            } else if (countEl.isJsonPrimitive()) {
                mod.count = countEl.getAsInt();
            } else {
                return null;
            }
        }
        mod.pitch_multiplier = readDouble(modObj, "pitch_multiplier");
        mod.yaw_multiplier = readDouble(modObj, "yaw_multiplier");
        return mod;
    }

    private static Double readDouble(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (element != null && element.isJsonPrimitive()) {
            return element.getAsDouble();
        }
        return null;
    }

    private static class ScanResult {
        private final Map<ResourceLocation, GunTaczFixesData> gunData = new HashMap<>();
        private final Map<ResourceLocation, Set<ResourceLocation>> allowTags = new HashMap<>();
    }
}
