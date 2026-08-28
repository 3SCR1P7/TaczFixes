package com.ssscript.taczfixes.common.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.tacz.guns.util.ResourceScanner;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

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
                    TaczFixesDataManager.putAll(scanFileSystemGunData());
                    CustomSlotManager.putAllTags(result.allowTags);
                    SwitchedDisplayDataManager.putAll(result.switchedDisplays);
                }, gameExecutor);
    }

    private static ScanResult scan(ResourceManager resourceManager) {
        ScanResult result = new ScanResult();
        Map<ResourceLocation, JsonElement> all = ResourceScanner.scanDirectory(resourceManager, "data/guns", GSON);
        for (Map.Entry<ResourceLocation, JsonElement> entry : all.entrySet()) {
            JsonElement element = entry.getValue();
            if (element == null || !element.isJsonObject()) continue;
            JsonObject root = element.getAsJsonObject();
            if (!root.has("taczfixes") && !root.has("gunsmithlib_extension")) continue;
            try {
                GunTaczFixesData data = root.has("taczfixes")
                        ? GSON.fromJson(root.get("taczfixes"), GunTaczFixesData.class)
                        : new GunTaczFixesData();
                if (data != null) {
                    JsonElement tfElement = root.get("taczfixes");
                    if (tfElement != null && tfElement.isJsonObject()) {
                        JsonElement rmElement = tfElement.getAsJsonObject().get("recoil_multiplier");
                        if (rmElement != null && rmElement.isJsonObject()) {
                            RecoilConfigJsonParser.collectRecoilModifiers(data.recoil_multiplier, rmElement.getAsJsonObject());
                        }
                    }
                    if (data.shield == null) {
                        data.shield = parseGunShield(root);
                    }
                    if (data.shield != null || tfElement != null && tfElement.isJsonObject()) {
                        result.gunData.put(entry.getKey(), data);
                    }
                }
            } catch (Exception ex) {
                LOGGER.error("taczfixes: failed to parse taczfixes of gun data {}", entry.getKey(), ex);
            }
        }
        scanFileSystem(result);
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
        result.switchedDisplays.putAll(scanSwitchedDisplays(resourceManager));
        return result;
    }

    public static Map<ResourceLocation, GunTaczFixesData> scanFileSystemGunData() {
        ScanResult result = new ScanResult();
        scanFileSystem(result);
        return result.gunData;
    }

    private static void scanFileSystem(ScanResult result) {
        try {
            Path taczDir = FMLPaths.GAMEDIR.get().resolve("tacz");
            if (!Files.isDirectory(taczDir)) return;
            try (Stream<Path> packStream = Files.list(taczDir)) {
                packStream.filter(Files::isDirectory).forEach(packDir -> {
                    Path dataDir = packDir.resolve("data");
                    if (!Files.isDirectory(dataDir)) return;
                    try (Stream<Path> nsStream = Files.list(dataDir)) {
                        nsStream.filter(Files::isDirectory).forEach(nsDir -> {
                            Path gunsDir = nsDir.resolve("data").resolve("guns");
                            if (!Files.isDirectory(gunsDir)) return;
                            try (Stream<Path> gunStream = Files.list(gunsDir)) {
                                gunStream.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(file -> {
                                    String fileName = file.getFileName().toString();
                                    String id = nsDir.getFileName().toString() + ":" + fileName.substring(0, fileName.length() - 5);
                                    try {
                                        JsonObject root = parseLenientJson(file);
                                        if (root == null) return;
                                        JsonElement tfElement = root.get("taczfixes");
                                        if (tfElement == null && !root.has("gunsmithlib_extension")) return;
                                        GunTaczFixesData data = tfElement != null && tfElement.isJsonObject()
                                                ? GSON.fromJson(tfElement, GunTaczFixesData.class)
                                                : new GunTaczFixesData();
                                        if (data != null) {
                                            if (tfElement != null && tfElement.isJsonObject()) {
                                                JsonElement rmElement = tfElement.getAsJsonObject().get("recoil_multiplier");
                                                if (rmElement != null && rmElement.isJsonObject()) {
                                                    RecoilConfigJsonParser.collectRecoilModifiers(data.recoil_multiplier, rmElement.getAsJsonObject());
                                                }
                                            }
                                            if (data.shield == null) {
                                                data.shield = parseGunShield(root);
                                            }
                                            if (data.shield != null || tfElement != null && tfElement.isJsonObject()) {
                                                result.gunData.put(ResourceLocation.tryParse(id), data);
                                            }
                                        }
                                    } catch (Exception ex) {
                                        LOGGER.error("taczfixes: failed to parse gun data file {}", file, ex);
                                    }
                                });
                            } catch (IOException e) {
                                LOGGER.warn("taczfixes: failed to list gun dir {}", gunsDir, e);
                            }
                        });
                    } catch (IOException e) {
                        LOGGER.warn("taczfixes: failed to list data dir {}", dataDir, e);
                    }
                });
            }
        } catch (Exception ex) {
            LOGGER.error("taczfixes: failed to scan tacz dir", ex);
        }
    }

    /** 解析 gunsmithlib_extension.shield.taczfixes 内的枪盾配置。 */
    private static GunTaczFixesData.ShieldConfig parseGunShield(JsonObject root) {
        try {
            if (root == null || !root.has("gunsmithlib_extension")) return null;
            JsonElement gsElement = root.get("gunsmithlib_extension");
            if (gsElement == null || !gsElement.isJsonObject()) return null;
            JsonElement shieldElement = gsElement.getAsJsonObject().get("shield");
            if (shieldElement == null || !shieldElement.isJsonObject()) return null;
            JsonElement tfElement = shieldElement.getAsJsonObject().get("taczfixes");
            if (tfElement == null || !tfElement.isJsonObject()) return null;
            return GSON.fromJson(tfElement, GunTaczFixesData.ShieldConfig.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private static JsonObject parseLenientJson(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonReader jr = new JsonReader(reader);
            jr.setLenient(true);
            return JsonParser.parseReader(jr).getAsJsonObject();
        }
    }

    private static Map<ResourceLocation, List<ResourceLocation>> scanSwitchedDisplays(ResourceManager resourceManager) {
        Map<ResourceLocation, List<ResourceLocation>> result = new HashMap<>();
        for (String prefix : new String[]{"index/attachments", "data/index/attachments"}) {
            Map<ResourceLocation, JsonElement> indexes =
                    ResourceScanner.scanDirectory(resourceManager, prefix, GSON);
            if (!indexes.isEmpty()) {
                for (Map.Entry<ResourceLocation, JsonElement> entry : indexes.entrySet()) {
                    JsonElement element = entry.getValue();
                    if (element == null || !element.isJsonObject()) continue;
                    JsonObject root = element.getAsJsonObject();
                    if (!root.has("display_switched")) continue;
                    JsonArray array = root.getAsJsonArray("display_switched");
                    if (array == null || array.isEmpty()) continue;
                    List<ResourceLocation> list = new ArrayList<>();
                    boolean valid = false;
                    for (JsonElement item : array) {
                        if (item == null || !item.isJsonPrimitive()) continue;
                        ResourceLocation displayId = ResourceLocation.tryParse(item.getAsString());
                        if (displayId == null) continue;
                        list.add(displayId);
                        valid = true;
                    }
                    if (valid) {
                        result.put(entry.getKey(), list);
                    }
                }
            }
        }
        return result;
    }

    private static class ScanResult {
        private final Map<ResourceLocation, GunTaczFixesData> gunData = new HashMap<>();
        private final Map<ResourceLocation, Set<ResourceLocation>> allowTags = new HashMap<>();
        private final Map<ResourceLocation, List<ResourceLocation>> switchedDisplays = new HashMap<>();
    }
}
