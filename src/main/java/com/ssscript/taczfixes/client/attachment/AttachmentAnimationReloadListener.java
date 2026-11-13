package com.ssscript.taczfixes.client.attachment;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.util.ResourceScanner;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 客户端资源重载监听器：扫描 {@code assets/&lt;namespace&gt;/display/attachments/*.json}，
 * 解析其中的 {@code taczfixes} 块（TaCZ 的 AttachmentDisplay POJO 会忽略未知字段，因此需要自行解析原始 JSON）。
 *
 * <p>解析结果以 display id 为键；运行时再通过 TaCZ 的 CommonAttachmentIndex（display 字段）
 * 映射到配件注册名。重载完成后立即销毁全部旧状态机实例。
 */
public class AttachmentAnimationReloadListener implements PreparableReloadListener {
    private static final Gson GSON = new Gson();

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
                                          ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                          Executor backgroundExecutor, Executor gameExecutor) {
        return CompletableFuture.supplyAsync(() -> scan(resourceManager), backgroundExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(AttachmentAnimationManager::onResourceReload, gameExecutor);
    }

    private static Map<ResourceLocation, AttachmentAnimationDef> scan(ResourceManager resourceManager) {
        Map<ResourceLocation, AttachmentAnimationDef> result = new HashMap<>();
        Map<ResourceLocation, JsonElement> displays =
                ResourceScanner.scanDirectory(resourceManager, "display/attachments", GSON);
        for (Map.Entry<ResourceLocation, JsonElement> entry : displays.entrySet()) {
            JsonElement element = entry.getValue();
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            try {
                JsonObject root = element.getAsJsonObject();
                AttachmentAnimationDef definition = AttachmentAnimationDef.parse(entry.getKey(), root);
                if (definition != null) {
                    result.put(entry.getKey(), definition);
                }
            } catch (Exception ignored) {
                // 单个 display 文件解析失败不影响其它配件，静默回退到静态渲染。
            }
        }
        return result;
    }
}
