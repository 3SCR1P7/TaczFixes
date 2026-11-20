package com.ssscript.taczfixes.client.hud;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 枪械 Lua 逻辑机脚本发布的自定义 HUD 参数客户端缓存。
 * 只缓存主手枪械, 按快捷栏槽位区分; 缓存的枪械 id 与当前手持不一致时视为无参数。
 */
public final class HudParamStore {

    private static final int MAX_PARAMS = 64;
    private static final int HOTBAR_SLOTS = 9;

    private static final Map<Integer, Entry> BY_SLOT = new ConcurrentHashMap<>();

    private HudParamStore() {
    }

    /** 客户端线程: 应用服务端下发的一组参数更新。 */
    public static void apply(int slot, String gunId, boolean clear,
                             Map<String, String> values, Set<String> removed) {
        if (slot < 0 || slot >= HOTBAR_SLOTS) {
            return;
        }
        String id = gunId == null ? "" : gunId;
        Entry current = BY_SLOT.get(slot);
        Map<String, String> next = current != null && current.gunId().equals(id)
                ? new HashMap<>(current.values()) : new HashMap<>();
        if (clear) {
            next.clear();
        }
        if (removed != null) {
            for (String key : removed) {
                next.remove(key);
            }
        }
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isEmpty() || entry.getValue() == null) {
                    continue;
                }
                if (next.size() >= MAX_PARAMS && !next.containsKey(entry.getKey())) {
                    continue;
                }
                next.put(entry.getKey(), entry.getValue());
            }
        }
        BY_SLOT.put(slot, new Entry(id, Map.copyOf(next)));
    }

    /** 当前主手枪械的参数快照; 没有发布过时返回空表。 */
    public static Map<String, String> valuesFor(LocalPlayer player, ResourceLocation gunId) {
        if (player == null || gunId == null) {
            return Map.of();
        }
        Entry entry = BY_SLOT.get(player.getInventory().selected);
        if (entry == null || !gunId.toString().equals(entry.gunId())) {
            return Map.of();
        }
        return entry.values();
    }

    public static void clear() {
        BY_SLOT.clear();
    }

    private record Entry(String gunId, Map<String, String> values) {
    }
}
