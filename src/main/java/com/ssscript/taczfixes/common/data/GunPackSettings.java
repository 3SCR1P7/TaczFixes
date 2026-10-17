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
 * order: 命名空间列表, 越靠前优先级越高(在同名资源冲突时覆盖后面的枪包)。
 * disabled: 被禁用的枪包命名空间。
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

    public static synchronized boolean isDisabled(String name) {
        ensureLoaded();
        return name != null && disabled.contains(name);
    }

    /** 保存当前启用顺序与禁用列表。 */
    public static synchronized void update(List<String> newOrder, List<String> newDisabled) {
        ensureLoaded();
        order = new ArrayList<>(newOrder);
        disabled.clear();
        disabled.addAll(newDisabled);
        save();
    }

    /** 按设置排列发现到的枪包: 已配置的高优先级在前, 新枪包按名称追加在后; 过滤掉禁用的。 */
    public static synchronized List<String> arrange(List<String> discovered) {
        ensureLoaded();
        LinkedHashSet<String> known = new LinkedHashSet<>();
        for (String name : order) {
            if (name != null && discovered.contains(name)) known.add(name);
        }
        List<String> rest = new ArrayList<>();
        for (String name : discovered) {
            if (!known.contains(name)) rest.add(name);
        }
        rest.sort(String.CASE_INSENSITIVE_ORDER);
        List<String> result = new ArrayList<>(known);
        result.addAll(rest);
        result.removeIf(disabled::contains);
        return result;
    }
}
