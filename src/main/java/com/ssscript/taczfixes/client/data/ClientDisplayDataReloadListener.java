package com.ssscript.taczfixes.client.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ssscript.taczfixes.common.data.AttachmentGroupOffsetManager;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.data.TaczFixesDataReloadListener;
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
                    AttachmentGroupOffsetManager.putAll(result);
                    TaczFixesDataManager.putAll(TaczFixesDataReloadListener.scanFileSystemGunData());
                    TaczFixesDataManager.syncPosAlterRanges();
                    com.ssscript.taczfixes.client.hud.CustomHudManager.clear();
                }, gameExecutor);
    }

    private static Map<ResourceLocation, Map<String, float[]>> scan(ResourceManager resourceManager) {
        Map<ResourceLocation, Map<String, float[]>> offsets = new HashMap<>();
        scanGroupOffsets(resourceManager, offsets);
        return offsets;
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
