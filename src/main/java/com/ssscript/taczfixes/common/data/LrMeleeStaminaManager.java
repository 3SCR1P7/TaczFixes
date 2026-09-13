package com.ssscript.taczfixes.common.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** lrtactical 近战武器 index 中的 taczfixes.stamina_consume(轻击/重击)覆盖值。 */
public final class LrMeleeStaminaManager {

    private static final Map<ResourceLocation, float[]> DATA = new ConcurrentHashMap<>();

    private LrMeleeStaminaManager() {
    }

    /** 扫描 index JSON, 读取 attack_left / attack_right 下的 taczfixes.stamina_consume。 */
    public static void parse(ResourceLocation id, JsonElement root) {
        if (id == null || root == null) return;
        float[] costs = new float[]{Float.NaN, Float.NaN};
        scan(null, root, costs);
        if (Float.isNaN(costs[0]) && Float.isNaN(costs[1])) {
            DATA.remove(id);
        } else {
            DATA.put(id, costs);
        }
    }

    /** 返回 {轻击, 重击}, NaN 表示未配置。 */
    public static float[] get(ResourceLocation id) {
        return id == null ? null : DATA.get(id);
    }

    public static void clear() {
        DATA.clear();
    }

    private static void scan(String key, JsonElement element, float[] costs) {
        if (element == null) return;
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if ("attack_left".equals(key) || "attack_right".equals(key)) {
                JsonObject taczfixes = object.has("taczfixes") && object.get("taczfixes").isJsonObject()
                        ? object.getAsJsonObject("taczfixes") : null;
                if (taczfixes != null && taczfixes.has("stamina_consume")) {
                    try {
                        float value = taczfixes.get("stamina_consume").getAsFloat();
                        if ("attack_left".equals(key)) {
                            costs[0] = value;
                        } else {
                            costs[1] = value;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                scan(entry.getKey(), entry.getValue(), costs);
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                scan(key, child, costs);
            }
        }
    }
}
