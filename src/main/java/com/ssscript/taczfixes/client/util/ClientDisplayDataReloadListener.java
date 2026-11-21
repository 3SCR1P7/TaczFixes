package com.ssscript.taczfixes.client.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ssscript.taczfixes.client.hud.ApricityHudResources;
import com.ssscript.taczfixes.client.hud.ApricityHudSupport;
import com.ssscript.taczfixes.client.hud.CustomHudController;
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
        return CompletableFuture.supplyAsync(() -> {
                    // 枪包 HUD 资源复制是纯文件 IO, 放在后台线程; AUI 模板注册在 apply 阶段完成。
                    if (ApricityHudSupport.isAuiModLoaded()) {
                        ApricityHudResources.syncAllFiles();
                    }
                    // 动态迷彩: 扫描 tacz 枪包(data 声明 + assets 着色器片段)。必须在 barrier 之前
                    // 完成 stage, 因为原版 shader 重载(RegisterShadersEvent)发生在各监听器 apply 阶段。
                    try {
                        com.ssscript.taczfixes.client.camo.DynamicCamo.stage(
                                com.ssscript.taczfixes.client.camo.CamoPackScanner.scan());
                    } catch (Throwable t) {
                        LOGGER.warn("taczfixes: dynamic camo scan failed", t);
                        com.ssscript.taczfixes.client.camo.DynamicCamo.stage(java.util.List.of());
                    }
                    return scan(resourceManager);
                }, backgroundExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(result -> {
                    AttachmentGroupOffsetManager.putAll(result);
                    TaczFixesDataManager.putAll(TaczFixesDataReloadListener.scanFileSystemGunData());
                    com.ssscript.taczfixes.common.data.CustomSlotManager.bumpDataStamp();
                    TaczFixesDataManager.syncPosAlterRanges();
                    com.ssscript.taczfixes.client.hud.CustomHudManager.clear();
                    CustomHudController.onResourceReload();
                    com.ssscript.taczfixes.client.util.GunPackIconLoader.clear();
                    com.ssscript.taczfixes.client.util.GunRecolorManager.clearTextures();
                    // 静态贴图透明度缓存不随资源重载失效: 重载后新贴图 alpha 变化 / 旧异常结果必须重新扫描。
                    com.ssscript.taczfixes.client.util.GunTransparency.clear();
                    // 枪械模型 ground 中心(PIVOT_CACHE)来自枪包 geo json, 重载后必须重新读取。
                    com.ssscript.taczfixes.client.util.GunModelPivot.clear();
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
        return ResourceLocation.fromNamespaceAndPath(displayLocation.getNamespace(), name);
    }
}
