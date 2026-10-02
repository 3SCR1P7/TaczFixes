package com.ssscript.taczfixes.common.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ssscript.taczfixes.common.data.AttachmentTaczFixesData;
import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.data.AttachmentTaczFixesReloadListener;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.resource.CommonAssetsManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** 配件 data 的读取/校验/运行时应用(与枪械编辑同机制, 文件位于 data/&lt;ns&gt;/data/attachments/)。 */
public final class AttachmentDataEditorHelper {
    private AttachmentDataEditorHelper() {
    }

    /** 配件索引指向的 data id(默认自身)。 */
    public static ResourceLocation resolveDataId(ResourceLocation attachmentId) {
        if (attachmentId == null) return null;
        return TimelessAPI.getCommonAttachmentIndex(attachmentId)
                .map(index -> index.getPojo().getData())
                .orElse(attachmentId);
    }

    public static ResourceLocation attachmentIdOf(ItemStack item) {
        IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
        return attachment == null ? null : attachment.getAttachmentId(item);
    }

    /** 编辑文本是否为合法 JSON 对象。 */
    public static boolean validateAttachmentData(String text) {
        if (text == null || text.isBlank()) return false;
        try {
            JsonElement element = JsonParser.parseString(text);
            return element != null && element.isJsonObject();
        } catch (Exception ex) {
            return false;
        }
    }

    /** 解析编辑文本中的 taczfixes 并立即写入运行时配件数据表。 */
    public static void applyTaczFixes(ResourceLocation dataId, String fullText) {
        if (dataId == null || fullText == null || fullText.isBlank()) return;
        try {
            JsonObject root = JsonParser.parseString(fullText).getAsJsonObject();
            JsonElement tfElement = root.get("taczfixes");
            AttachmentTaczFixesData data = tfElement != null && tfElement.isJsonObject()
                    ? AttachmentTaczFixesReloadListener.parse(tfElement.getAsJsonObject())
                    : new AttachmentTaczFixesData();
            if (data != null) {
                AttachmentTaczFixesManager.put(dataId, data);
            }
        } catch (Exception ignored) {
        }
    }

    private static String normalizeLineEndings(String text) {
        return text == null ? null : text.replace("\r\n", "\n").replace('\r', '\n');
    }

    /** 读取配件源数据文件原文(目录或 zip); 找不到返回 null。 */
    public static String readOriginalDataText(ItemStack attachmentItem) {
        if (attachmentItem == null || attachmentItem.isEmpty()) return null;
        ResourceLocation attachmentId = attachmentIdOf(attachmentItem);
        if (attachmentId == null) return null;
        ResourceLocation dataId = resolveDataId(attachmentId);
        if (dataId == null) return null;
        try {
            java.nio.file.Path taczDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("tacz");
            if (!java.nio.file.Files.isDirectory(taczDir)) return null;
            String rel = "data/" + dataId.getNamespace() + "/data/attachments/" + dataId.getPath() + ".json";
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
        }
        return null;
    }

    /** 当前配件编辑文本: 优先源文件原文, 否则用运行时 taczfixes 序列化兜底。 */
    public static String currentAttachmentDataText(ItemStack attachmentItem) {
        String original = readOriginalDataText(attachmentItem);
        if (original != null) {
            return original;
        }
        ResourceLocation attachmentId = attachmentIdOf(attachmentItem);
        if (attachmentId == null) return null;
        ResourceLocation dataId = resolveDataId(attachmentId);
        AttachmentTaczFixesData data = AttachmentTaczFixesManager.resolveData(attachmentId);
        JsonObject root = new JsonObject();
        if (data != null) {
            root.add("taczfixes", CommonAssetsManager.GSON.toJsonTree(data));
        }
        return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

}
