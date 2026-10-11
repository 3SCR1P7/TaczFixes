package com.ssscript.taczfixes.common.data;

import com.google.gson.JsonElement;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.manager.AttachmentDataManager;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
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

/** 把 data/slot_adapters 的数据按配件原生 AttachmentData(含 modifier)解析并缓存。 */
public class AdapterNativeDataReloadListener implements PreparableReloadListener {
    private static final Logger LOGGER = LogManager.getLogger("taczfixes");

    @Override
    public CompletableFuture<Void> reload(PreparableReloadListener.PreparationBarrier barrier,
                                          ResourceManager resourceManager, ProfilerFiller prepareProfiler,
                                          ProfilerFiller applyProfiler, Executor backgroundExecutor,
                                          Executor gameExecutor) {
        return CompletableFuture.supplyAsync(() -> scan(resourceManager), backgroundExecutor)
                .thenCompose(barrier::wait)
                // 不做 clear: 客户端资源管理器可能不含枪包 data, 空扫描不能覆盖服务端已加载的数据;
                // 按 id 覆盖即可, 被移除的适配器不会安装故残留无害。
                .thenAcceptAsync(AdapterAttachmentDataManager::putAll, gameExecutor);
    }

    private static Map<ResourceLocation, AttachmentData> scan(ResourceManager resourceManager) {
        Map<ResourceLocation, AttachmentData> result = new HashMap<>();
        Map<ResourceLocation, JsonElement> all =
                ResourceScanner.scanDirectory(resourceManager, "data/slot_adapters", CommonAssetsManager.GSON);
        for (Map.Entry<ResourceLocation, JsonElement> entry : all.entrySet()) {
            JsonElement element = entry.getValue();
            if (element == null || !element.isJsonObject()) continue;
            try {
                AttachmentData data = CommonAssetsManager.GSON.fromJson(element, AttachmentData.class);
                if (data != null) {
                    AttachmentDataManager.initModifiers(data, element, CommonAssetsManager.GSON);
                    result.put(entry.getKey(), data);
                }
            } catch (Exception ex) {
                LOGGER.error("taczfixes: failed to parse slot adapter data {}", entry.getKey(), ex);
            }
        }
        return result;
    }
}
