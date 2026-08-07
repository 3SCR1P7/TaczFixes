package com.ssscript.taczfixes.data;

import com.google.gson.Gson;
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
import java.util.Map;
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
                .thenAcceptAsync(TaczFixesDataManager::putAll, gameExecutor);
    }

    private static Map<ResourceLocation, GunTaczFixesData> scan(ResourceManager resourceManager) {
        Map<ResourceLocation, GunTaczFixesData> result = new HashMap<>();
        Map<ResourceLocation, JsonElement> all = ResourceScanner.scanDirectory(resourceManager, "data/guns", GSON);
        for (Map.Entry<ResourceLocation, JsonElement> entry : all.entrySet()) {
            JsonElement element = entry.getValue();
            if (element == null || !element.isJsonObject()) continue;
            JsonObject root = element.getAsJsonObject();
            if (!root.has("taczfixes")) continue;
            try {
                GunTaczFixesData data = GSON.fromJson(root.get("taczfixes"), GunTaczFixesData.class);
                if (data != null) {
                    result.put(entry.getKey(), data);
                }
            } catch (Exception ex) {
                LOGGER.error("taczfixes: failed to parse taczfixes of gun data {}", entry.getKey(), ex);
            }
        }
        return result;
    }
}
