package com.ssscript.taczfixes.common.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 枪包启用/优先级设置, 持久化于 config/taczfixes_gunpacks.json。
 * order: 枪包 ID(namespace@文件名)列表, 越靠前优先级越高; 兼容旧的命名空间条目。
 * disabled: 被禁用的枪包 ID; 兼容旧的命名空间条目。
 */
public final class GunPackSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Paths.get("config", "taczfixes_gunpacks.json");

    private static List<String> order = new ArrayList<>();
    private static final Set<String> disabled = new LinkedHashSet<>();
    private static boolean loaded = false;

    private GunPackSettings() {
    }

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(FILE)) return;
            JsonElement element = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8));
            if (!element.isJsonObject()) return;
            JsonObject root = element.getAsJsonObject();
            JsonArray orderArray = root.getAsJsonArray("order");
            if (orderArray != null) {
                for (JsonElement entry : orderArray) {
                    if (entry.isJsonPrimitive()) order.add(entry.getAsString());
                }
            }
            JsonArray disabledArray = root.getAsJsonArray("disabled");
            if (disabledArray != null) {
                for (JsonElement entry : disabledArray) {
                    if (entry.isJsonPrimitive()) disabled.add(entry.getAsString());
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static synchronized void save() {
        JsonObject root = new JsonObject();
        JsonArray orderArray = new JsonArray();
        order.forEach(orderArray::add);
        JsonArray disabledArray = new JsonArray();
        disabled.forEach(disabledArray::add);
        root.add("order", orderArray);
        root.add("disabled", disabledArray);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }
    }

    public static synchronized List<String> order() {
        ensureLoaded();
        return new ArrayList<>(order);
    }

    public static synchronized List<String> disabledNames() {
        ensureLoaded();
        return new ArrayList<>(disabled);
    }

    public static synchronized boolean isDisabled(String packId) {
        ensureLoaded();
        if (packId == null) return false;
        if (disabled.contains(packId)) return true;
        // 兼容旧配置: 只写命名空间时禁用该命名空间下的所有枪包。
        return disabled.contains(GunPackIds.namespaceOf(packId));
    }

    /** 把某个枪包标记为未启用: 它会显示在“可用”列表里, 由玩家自己决定是否启用。 */
    public static synchronized void addDisabled(String packId) {
        if (packId == null || packId.isBlank()) return;
        ensureLoaded();
        if (disabled.add(packId)) {
            save();
        }
    }

    /** 保存当前启用顺序与禁用列表。 */
    public static synchronized void update(List<String> newOrder, List<String> newDisabled) {
        ensureLoaded();
        order = new ArrayList<>(newOrder);
        disabled.clear();
        disabled.addAll(newDisabled);
        save();
    }

    /**
     * 枪包更新后文件名变化时, 把旧 ID 的启用状态与排序位置迁移到新 ID。
     * 旧 ID 不存在时不做任何改动。
     */
    public static synchronized void migrate(String oldId, String newId) {
        if (oldId == null || newId == null || oldId.equals(newId)) return;
        ensureLoaded();
        boolean changed = false;
        int index = order.indexOf(oldId);
        if (index >= 0) {
            if (order.contains(newId)) {
                order.remove(oldId);
            } else {
                order.set(index, newId);
            }
            changed = true;
        }
        if (disabled.remove(oldId)) {
            disabled.add(newId);
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    /**
     * 按设置排列发现到的枪包 ID: 已配置的高优先级在前, 新枪包按名称追加在后; 过滤掉禁用的。
     * 旧配置里的命名空间条目会匹配该命名空间下的所有枪包。
     */
    public static synchronized List<String> arrange(List<String> discovered) {
        ensureLoaded();
        LinkedHashSet<String> known = new LinkedHashSet<>();
        for (String entry : order) {
            if (entry == null) continue;
            for (String id : discovered) {
                if (id.equals(entry) || (GunPackIds.isNamespaceEntry(entry)
                        && GunPackIds.namespaceOf(id).equals(entry))) {
                    known.add(id);
                }
            }
        }
        List<String> rest = new ArrayList<>();
        for (String id : discovered) {
            if (!known.contains(id)) rest.add(id);
        }
        rest.sort(String.CASE_INSENSITIVE_ORDER);
        List<String> result = new ArrayList<>(known);
        result.addAll(rest);
        result.removeIf(GunPackSettings::isDisabled);
        return result;
    }
}
