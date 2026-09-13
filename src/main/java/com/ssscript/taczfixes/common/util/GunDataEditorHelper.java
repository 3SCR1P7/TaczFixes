package com.ssscript.taczfixes.common.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.ssscript.taczfixes.common.mixin.MixinCommonGunIndexAccessor;
import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.data.RecoilConfigJsonParser;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.manager.GunDataManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class GunDataEditorHelper {
    private GunDataEditorHelper() {
    }

    /** GunData 序列化会把 InaccuracyType 等枚举 map 键写成大写, 反序列化会抛异常, 这里归一化回小写。 */
    private static String normalizeEnumKeys(String text) {
        return text
                .replace("\"STAND\"", "\"stand\"")
                .replace("\"MOVE\"", "\"move\"")
                .replace("\"SNEAK\"", "\"sneak\"")
                .replace("\"LIE\"", "\"lie\"")
                .replace("\"AIM\"", "\"aim\"");
    }

    /** 校验文本是否为合法的枪械 data JSON(GunData 语义), 并解析为 GunData。失败返回 null。 */
    public static GunData parseGunData(String text) {
        if (text == null || text.isBlank()) return null;
        text = normalizeEnumKeys(text);
        try {
            JsonElement element = JsonParser.parseString(text);
            if (element == null || !element.isJsonObject()) return null;
            GunData gunData = CommonAssetsManager.GSON.fromJson(element, GunData.class);
            if (gunData == null || !validateGunData(gunData)) return null;
            // exclusive_attachments 等后处理: 失败不影响有效性(源文件原文已验证过, 仅为应用补充)
            try {
                GunDataManager.initExclusiveAttachmentModifiers(gunData, element);
            } catch (Exception ignored) {
            }
            return gunData;
        } catch (Exception ex) {
            return null;
        }
    }

    /** 语义校验: 仅检查会导致运行期 NPE 的核心字段(非法枚举被 Gson 置 null)。 */
    private static boolean validateGunData(GunData gunData) {
        if (gunData.getBulletData() == null) return false;
        com.tacz.guns.resource.pojo.data.gun.BulletData bullet = gunData.getBulletData();
        if (bullet.getDamageAmount() < 0.0f) return false;
        com.tacz.guns.resource.pojo.data.gun.GunReloadData reload = gunData.getReloadData();
        if (reload != null && reload.getType() == null) return false;
        return true;
    }

    /** 将编辑后的完整 data JSON 文本暂存到 config/taczfixes, 启动/tacz重载时替换进源枪包。 */
    public static boolean saveGunDataFile(ItemStack gunItem, String fullText) {
        if (gunItem == null || gunItem.isEmpty() || fullText == null || fullText.isBlank()) return false;
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null) return false;
        ResourceLocation gunId = gun.getGunId(gunItem);
        if (gunId == null) return false;
        return GunDataOverrideStorage.save(TaczFixesDataManager.resolveDataId(gunId), fullText);
    }

    /** 解析编辑文本中的 taczfixes(顶层) 与 gunsmithlib_extension.shield.taczfixes, 立即写入运行时数据表。 */
    public static void applyTaczFixes(ResourceLocation dataId, String fullText) {
        if (dataId == null || fullText == null || fullText.isBlank()) return;
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(fullText).getAsJsonObject();
            com.google.gson.JsonObject tfObject = null;
            if (root.has("taczfixes") && root.get("taczfixes").isJsonObject()) {
                tfObject = root.getAsJsonObject("taczfixes");
            }
            GunTaczFixesData data = tfObject != null
                    ? CommonAssetsManager.GSON.fromJson(tfObject, GunTaczFixesData.class)
                    : new GunTaczFixesData();
            if (data == null) data = new GunTaczFixesData();
            if (tfObject != null) {
                com.google.gson.JsonElement rmElement = tfObject.get("recoil_multiplier");
                if (rmElement != null && rmElement.isJsonObject()) {
                    RecoilConfigJsonParser.collectRecoilModifiers(data.recoil_multiplier, rmElement.getAsJsonObject());
                }
            }
            data.shield = null;
            if (root.has("gunsmithlib_extension") && root.get("gunsmithlib_extension").isJsonObject()
                    && root.getAsJsonObject("gunsmithlib_extension").has("shield")
                    && root.getAsJsonObject("gunsmithlib_extension").get("shield").isJsonObject()) {
                com.google.gson.JsonObject shield = root.getAsJsonObject("gunsmithlib_extension").getAsJsonObject("shield");
                if (shield.has("taczfixes") && shield.get("taczfixes").isJsonObject()) {
                    data.shield = CommonAssetsManager.GSON.fromJson(
                            shield.get("taczfixes"), GunTaczFixesData.ShieldConfig.class);
                }
            }
            TaczFixesDataManager.put(dataId, data);
        } catch (Exception ignored) {
        }
    }

    /** 按 gunId 应用(服务器/客户端通用, 不需要物品栈)。 */
    public static boolean applyByGunId(ResourceLocation gunId, String fullText) {
        if (gunId == null) return false;
        GunData gunData = parseGunData(fullText);
        if (gunData == null) return false;
        CommonGunIndex index = TimelessAPI.getCommonGunIndex(gunId).orElse(null);
        if (index == null) return false;
        ((MixinCommonGunIndexAccessor) index).taczfixes$setGunData(gunData);
        return true;
    }

    /** 将解析后的 GunData 应用到运行时的枪械索引。成功返回 true。 */
    public static boolean applyGunData(ItemStack gunItem, GunData gunData) {
        if (gunItem == null || gunData == null) return false;
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null) return false;
        ResourceLocation gunId = gun.getGunId(gunItem);
        if (gunId == null) return false;
        CommonGunIndex index = TimelessAPI.getCommonGunIndex(gunId).orElse(null);
        if (index == null) return false;
        ((MixinCommonGunIndexAccessor) index).taczfixes$setGunData(gunData);
        return true;
    }

    /** 读取枪械源数据文件原文(目录包/zip)。找不到返回 null。 */
    private static String normalizeLineEndings(String text) {
        return text == null ? null : text.replace("\r\n", "\n").replace('\r', '\n');
    }

    public static String readOriginalDataText(ItemStack gunItem) {
        if (gunItem == null || gunItem.isEmpty()) return null;
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null) return null;
        ResourceLocation gunId = gun.getGunId(gunItem);
        if (gunId == null) return null;
        ResourceLocation dataId = TaczFixesDataManager.resolveDataId(gunId);
        if (dataId == null) return null;
        try {
            java.nio.file.Path taczDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("tacz");
            if (!java.nio.file.Files.isDirectory(taczDir)) return null;
            String rel = "data/" + dataId.getNamespace() + "/data/guns/" + dataId.getPath() + ".json";
            try (java.util.stream.Stream<java.nio.file.Path> entries = java.nio.file.Files.list(taczDir)) {
                for (java.nio.file.Path entry : (Iterable<java.nio.file.Path>) entries::iterator) {
                    if (java.nio.file.Files.isDirectory(entry)) {
                        java.nio.file.Path target = entry.resolve(rel);
                        if (java.nio.file.Files.isRegularFile(target)) {
                            return normalizeLineEndings(
                                    java.nio.file.Files.readString(target, java.nio.charset.StandardCharsets.UTF_8));
                        }
                    } else if (entry.getFileName().toString().toLowerCase().endsWith(".zip")) {
                        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(entry.toFile())) {
                            java.util.zip.ZipEntry ze = zip.getEntry(rel);
                            if (ze != null) {
                                try (java.io.InputStream is = zip.getInputStream(ze)) {
                                    return normalizeLineEndings(new String(is.readAllBytes(),
                                            java.nio.charset.StandardCharsets.UTF_8));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    /** 获取当前枪械运行时的 data 文本: 优先源文件原文(保留 exclusive_attachments 等结构), 无文件才序列化兜底。 */
    public static String currentGunDataText(ItemStack gunItem) {
        String original = readOriginalDataText(gunItem);
        if (original != null) {
            return original;
        }
        if (gunItem == null || gunItem.isEmpty()) return null;
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null) return null;
        ResourceLocation gunId = gun.getGunId(gunItem);
        if (gunId == null) return null;
        return TimelessAPI.getCommonGunIndex(gunId).map(index -> {
            com.google.gson.JsonObject object = CommonAssetsManager.GSON.toJsonTree(index.getGunData()).getAsJsonObject();
            ResourceLocation dataId = TaczFixesDataManager.resolveDataId(gunId);
            GunTaczFixesData fixes = TaczFixesDataManager.get(dataId);
            if (fixes != null) {
                // 组装 taczfixes(不含 shield) 与 gunsmithlib_extension.shield.taczfixes 两处
                com.google.gson.JsonObject tfObject = CommonAssetsManager.GSON.toJsonTree(fixes).getAsJsonObject();
                if (tfObject.has("shield")) {
                    GunTaczFixesData.ShieldConfig shield = fixes.shield;
                    tfObject.remove("shield");
                    if (shield != null) {
                        // 以 gsm ShieldData(字段名即 snake_case) 为主体, 保留原生 shield 字段, 再挂 taczfixes
                        com.google.gson.JsonObject shieldObj = null;
                        try {
                            shieldObj = mod.chloeprime.gunsmithlib.common.gunpack_extension.shared.shield.ShieldData
                                    .fromGun(gunItem)
                                    .map(d -> CommonAssetsManager.GSON.toJsonTree(d).getAsJsonObject())
                                    .orElse(null);
                        } catch (Exception ignored) {
                        }
                        if (shieldObj == null) {
                            shieldObj = new com.google.gson.JsonObject();
                        }
                        shieldObj.add("taczfixes", CommonAssetsManager.GSON.toJsonTree(shield));
                        com.google.gson.JsonObject extension = new com.google.gson.JsonObject();
                        extension.add("shield", shieldObj);
                        object.add("gunsmithlib_extension", extension);
                    }
                }
                object.add("taczfixes", tfObject);
            }
            return restoreInfinite(
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(object));
        }).orElse(null);
    }

    /** 把序列化中被 TACZ 转为 Float.MAX 的无限距离还原为 "infinite"。 */
    private static String restoreInfinite(String text) {
        return text.replaceAll("3\\.4028235[Ee]38", "\"infinite\"");
    }
}
