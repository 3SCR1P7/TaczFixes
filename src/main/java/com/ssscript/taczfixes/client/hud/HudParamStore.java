package com.ssscript.taczfixes.client.hud;

import com.ssscript.taczfixes.TaczFixesMod;
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
    /** 超限丢弃告警的全局最短间隔(每个上传周期最多一条), 避免服务器持续超发时刷屏。 */
    private static final long DROP_WARN_INTERVAL_MS = 5000L;
    private static volatile long lastDropWarnMs;

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
            int dropped = 0;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isEmpty() || entry.getValue() == null) {
                    continue;
                }
                if (next.size() >= MAX_PARAMS && !next.containsKey(entry.getKey())) {
                    // 超限不再完全静默: 每个上传周期汇总一次被丢弃的 key 数(带限流)。
                    dropped++;
                    continue;
                }
                next.put(entry.getKey(), entry.getValue());
            }
            warnDropped(dropped);
        }
        BY_SLOT.put(slot, new Entry(id, Map.copyOf(next)));
    }

    /** 限流记录超限丢弃告警, 说明丢弃的 key 数与上限。 */
    private static void warnDropped(int dropped) {
        if (dropped <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastDropWarnMs < DROP_WARN_INTERVAL_MS) {
            return;
        }
        lastDropWarnMs = now;
        TaczFixesMod.LOGGER.warn("taczfixes: HUD params exceed limit {}, dropped {} key(s)", MAX_PARAMS, dropped);
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
