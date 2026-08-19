package com.ssscript.taczfixes.client.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ssscript.taczfixes.client.util.SwitchedDisplayManager;
import com.ssscript.taczfixes.common.data.AttachmentGroupOffsetManager;
import com.ssscript.taczfixes.common.data.GunPosAlterManager;
import com.tacz.guns.util.ResourceScanner;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class ClientDisplayDataReloadListener implements PreparableReloadListener {
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
                    AttachmentGroupOffsetManager.putAll(result.getKey());
                    GunPosAlterManager.putAll(result.getValue());
                    SwitchedDisplayManager.refresh();
                }, gameExecutor);
    }

    private static java.util.AbstractMap.SimpleImmutableEntry<
            Map<ResourceLocation, Map<String, float[]>>,
            Map<ResourceLocation, Map<String, float[]>>> scan(ResourceManager resourceManager) {
        Map<ResourceLocation, Map<String, float[]>> offsets = new HashMap<>();
        scanGroupOffsets(resourceManager, offsets);
        Map<ResourceLocation, Map<String, float[]>> posAlters = new HashMap<>();
        scanPosAlters(resourceManager, posAlters);
        return new java.util.AbstractMap.SimpleImmutableEntry<>(offsets, posAlters);
    }

    private static void scanGroupOffsets(ResourceManager resourceManager,
                                         Map<ResourceLocation, Map<String, float[]>> result) {
        Map<ResourceLocation, JsonElement> displays =
                ResourceScanner.scanDirectory(resourceManager, "display/attachments", GSON);
        for (Map.Entry<ResourceLocation, JsonElement> entry : displays.entrySet()) {
            JsonElement element = entry.getValue();
            if (element == null || !element.isJsonObject()) continue;
            JsonObject root = element.getAsJsonObject();
            if (!root.has("group_offset")) continue;
            JsonElement groupOffsetElement = root.get("group_offset");
            if (groupOffsetElement == null || !groupOffsetElement.isJsonObject()) continue;
            ResourceLocation attachmentId = parseId(entry.getKey());
            if (attachmentId == null) continue;
            Map<String, float[]> offsets = new HashMap<>();
            for (Map.Entry<String, JsonElement> offsetEntry : groupOffsetElement.getAsJsonObject().entrySet()) {
                JsonElement value = offsetEntry.getValue();
                if (value == null || !value.isJsonArray()) continue;
                JsonArray array = value.getAsJsonArray();
                if (array.size() < 3) continue;
                float[] offset = new float[3];
                boolean valid = true;
                for (int i = 0; i < 3; i++) {
                    JsonElement component = array.get(i);
                    if (component == null || !component.isJsonPrimitive()) {
                        valid = false;
                        break;
                    }
                    offset[i] = (float) component.getAsDouble();
                }
                if (valid) {
                    offsets.put(offsetEntry.getKey(), offset);
                }
            }
            if (!offsets.isEmpty()) {
                result.put(attachmentId, offsets);
            }
        }
    }

    private static void scanPosAlters(ResourceManager resourceManager,
                                      Map<ResourceLocation, Map<String, float[]>> result) {
        Map<ResourceLocation, JsonElement> displays =
                ResourceScanner.scanDirectory(resourceManager, "display/guns", GSON);
        for (Map.Entry<ResourceLocation, JsonElement> entry : displays.entrySet()) {
            JsonElement element = entry.getValue();
            if (element == null || !element.isJsonObject()) continue;
            JsonObject root = element.getAsJsonObject();
            if (!root.has("pos_alter")) continue;
            JsonElement posAlterElement = root.get("pos_alter");
            if (posAlterElement == null || !posAlterElement.isJsonObject()) continue;
            ResourceLocation gunId = parseId(entry.getKey());
            if (gunId == null) continue;
            Map<String, float[]> ranges = new HashMap<>();
            for (Map.Entry<String, JsonElement> rangeEntry : posAlterElement.getAsJsonObject().entrySet()) {
                JsonElement value = rangeEntry.getValue();
                if (value == null || !value.isJsonArray()) continue;
                JsonArray array = value.getAsJsonArray();
                if (array.size() < 2) continue;
                JsonElement minElement = array.get(0);
                JsonElement maxElement = array.get(1);
                if (minElement == null || maxElement == null
                        || !minElement.isJsonPrimitive() || !maxElement.isJsonPrimitive()) continue;
                float min = (float) minElement.getAsDouble();
                float max = (float) maxElement.getAsDouble();
                ranges.put(rangeEntry.getKey(), new float[]{Math.min(min, max), Math.max(min, max)});
            }
            if (!ranges.isEmpty()) {
                result.put(gunId, ranges);
            }
        }
    }

    private static ResourceLocation parseId(ResourceLocation displayLocation) {
        String path = displayLocation.getPath();
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        if (name.endsWith("_display")) {
            name = name.substring(0, name.length() - "_display".length());
        }
        if (name.isEmpty()) return null;
        return new ResourceLocation(displayLocation.getNamespace(), name);
    }
}