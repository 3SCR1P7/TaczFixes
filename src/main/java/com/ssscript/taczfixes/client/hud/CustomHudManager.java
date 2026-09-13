package com.ssscript.taczfixes.client.hud;

import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.CommonAssetsManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** 自定义 HUD 定义加载: 从 tacz 枪包目录/zip 的 assets/<ns>/hud/<path>.json 按需读取并缓存。 */
public final class CustomHudManager {

    private static final Map<ResourceLocation, CustomHudDefinition> CACHE = new HashMap<>();
    private static final Set<ResourceLocation> MISSING = new HashSet<>();

    private CustomHudManager() {
    }

    public static CustomHudDefinition get(ResourceLocation id) {
        if (id == null) return null;
        CustomHudDefinition cached = CACHE.get(id);
        if (cached != null) return cached;
        if (MISSING.contains(id)) return null;
        CustomHudDefinition def = loadFromPacks(id);
        if (def == null) {
            MISSING.add(id);
            return null;
        }
        CACHE.put(id, def);
        return def;
    }

    public static void clear() {
        CACHE.clear();
        MISSING.clear();
    }

    /** 当前主手枪械生效的自定义 HUD(未配置/未加载返回 null)。 */
    public static CustomHudDefinition activeHud() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return null;
        ItemStack stack = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return null;
        ResourceLocation gunId = gun.getGunId(stack);
        ResourceLocation dataId = gunId == null ? null : TaczFixesDataManager.resolveDataId(gunId);
        GunTaczFixesData data = dataId == null ? null : TaczFixesDataManager.get(dataId);
        if (data == null || data.custom_hud == null || data.custom_hud.isBlank()) return null;
        ResourceLocation hudId = ResourceLocation.tryParse(data.custom_hud.trim());
        return get(hudId);
    }

    private static CustomHudDefinition loadFromPacks(ResourceLocation id) {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        Path taczDir = gameDir.resolve("tacz");
        if (!Files.isDirectory(taczDir)) return null;
        String relative = "assets/" + id.getNamespace() + "/hud/" + id.getPath() + ".json";
        try (Stream<Path> children = Files.list(taczDir)) {
            List<Path> list = children.sorted().collect(Collectors.toList());
            for (Path child : list) {
                try {
                    if (Files.isDirectory(child)) {
                        Path file = child.resolve(relative);
                        if (Files.isRegularFile(file)) {
                            String text = Files.readString(file, StandardCharsets.UTF_8);
                            return CommonAssetsManager.GSON.fromJson(text, CustomHudDefinition.class);
                        }
                    } else if (Files.isRegularFile(child)
                            && child.getFileName().toString().toLowerCase().endsWith(".zip")) {
                        try (ZipFile zip = new ZipFile(child.toFile())) {
                            ZipEntry entry = zip.getEntry(relative);
                            if (entry != null) {
                                try (InputStream in = zip.getInputStream(entry)) {
                                    String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                                    return CommonAssetsManager.GSON.fromJson(text, CustomHudDefinition.class);
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            com.ssscript.taczfixes.common.register.TaczFixesMod.LOGGER.warn("taczfixes: custom hud scan failed for {}", id, e);
        }
        return null;
    }
}
