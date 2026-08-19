package com.ssscript.taczfixes.common.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.resource.pojo.data.attachment.Modifier;
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

public class AttachmentTaczFixesReloadListener implements PreparableReloadListener {
    private static final Logger LOGGER = LogManager.getLogger("taczfixes");
    private static final Gson GSON = new Gson();

    @Override
    public CompletableFuture<Void> reload(PreparableReloadListener.PreparationBarrier barrier,
                                          ResourceManager resourceManager, ProfilerFiller prepareProfiler,
                                          ProfilerFiller applyProfiler, Executor backgroundExecutor,
                                          Executor gameExecutor) {
        return CompletableFuture.supplyAsync(() -> scan(resourceManager), backgroundExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(AttachmentTaczFixesManager::putAll, gameExecutor);
    }

    private static Map<ResourceLocation, AttachmentTaczFixesData> scan(ResourceManager resourceManager) {
        Map<ResourceLocation, AttachmentTaczFixesData> result = new HashMap<>();
        Map<ResourceLocation, JsonElement> all = ResourceScanner.scanDirectory(resourceManager, "data/attachments", GSON);
        for (Map.Entry<ResourceLocation, JsonElement> entry : all.entrySet()) {
            JsonElement element = entry.getValue();
            if (element == null || !element.isJsonObject()) continue;
            JsonObject root = element.getAsJsonObject();
            if (!root.has("taczfixes")) continue;
            JsonElement tfElement = root.get("taczfixes");
            if (!tfElement.isJsonObject()) continue;
            try {
                AttachmentTaczFixesData data = parse(tfElement.getAsJsonObject());
                if (data != null) {
                    result.put(entry.getKey(), data);
                }
            } catch (Exception ex) {
                LOGGER.error("taczfixes: failed to parse taczfixes of attachment data {}", entry.getKey(), ex);
            }
        }
        return result;
    }

    private static AttachmentTaczFixesData parse(JsonObject tf) {
        AttachmentTaczFixesData data = GSON.fromJson(tf, AttachmentTaczFixesData.class);
        if (data == null) return null;
        JsonElement rmElement = tf.get("recoil_multiplier");
        if (rmElement != null && rmElement.isJsonObject()) {
            RecoilConfigJsonParser.collectRecoilModifiers(data.recoil_multiplier, rmElement.getAsJsonObject());
        }
        JsonElement iaElement = tf.get("inaccuracy_multiplier");
        if (iaElement != null && iaElement.isJsonObject()) {
            data.inaccuracy_multiplier = parseInaccuracyAdjust(iaElement.getAsJsonObject());
        }
        return data;
    }

    private static AttachmentTaczFixesData.InaccuracyAdjust parseInaccuracyAdjust(JsonObject ia) {
        AttachmentTaczFixesData.InaccuracyAdjust adj = new AttachmentTaczFixesData.InaccuracyAdjust();
        adj.max_stack = parseModifier(ia.getAsJsonObject("max_stack"));
        adj.per_shot = parseModifier(ia.getAsJsonObject("per_shot"));
        adj.cooldown_speed = parseModifier(ia.getAsJsonObject("cooldown_speed"));
        adj.cooldown_delay = parseModifier(ia.getAsJsonObject("cooldown_delay"));
        return adj;
    }

    private static Modifier parseModifier(JsonObject obj) {
        if (obj == null) return null;
        Modifier mod = GSON.fromJson(obj, Modifier.class);
        if (mod == null) return null;
        if (obj.has("addned") && obj.get("addned").isJsonPrimitive() && !obj.has("addend")) {
            mod.setAddend(obj.get("addned").getAsDouble());
        }
        return mod;
    }
}
