package com.ssscript.taczfixes.common.data;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * attachment_slots 配置块: 两个开关字段与槽位定义混在同一个 JSON 对象里。
 * <ul>
 *   <li>hidden_unavailable: 未满足 dependence/conflict 的自定义槽是否隐藏(默认 true)。false 时仍显示但用不可用图标。</li>
 *   <li>hidden_unavailable_default: tacz 默认槽不可用(类型未开放)时是否隐藏(默认 false)。</li>
 * </ul>
 */
public class AttachmentSlotsConfig {

    public Boolean hidden_unavailable;
    public Boolean hidden_unavailable_default;
    public Map<String, CustomSlotDefinition> slots = new LinkedHashMap<>();

    /** JSON 中开关与槽位同层, 序列化时保持原结构。 */
    public static class Adapter implements JsonDeserializer<AttachmentSlotsConfig>, JsonSerializer<AttachmentSlotsConfig> {

        @Override
        public AttachmentSlotsConfig deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            AttachmentSlotsConfig config = new AttachmentSlotsConfig();
            if (json == null || !json.isJsonObject()) return config;
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                if ("hidden_unavailable".equals(key)) {
                    config.hidden_unavailable = parseBool(value);
                } else if ("hidden_unavailable_default".equals(key)) {
                    config.hidden_unavailable_default = parseBool(value);
                } else if (value != null && value.isJsonObject()) {
                    CustomSlotDefinition def = context.deserialize(value, CustomSlotDefinition.class);
                    if (def != null) {
                        config.slots.put(key, def);
                    }
                }
            }
            return config;
        }

        @Override
        public JsonElement serialize(AttachmentSlotsConfig src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            if (src.hidden_unavailable != null) {
                obj.addProperty("hidden_unavailable", src.hidden_unavailable);
            }
            if (src.hidden_unavailable_default != null) {
                obj.addProperty("hidden_unavailable_default", src.hidden_unavailable_default);
            }
            if (src.slots != null) {
                for (Map.Entry<String, CustomSlotDefinition> entry : src.slots.entrySet()) {
                    obj.add(entry.getKey(), context.serialize(entry.getValue()));
                }
            }
            return obj;
        }

        private static Boolean parseBool(JsonElement e) {
            if (e == null || e.isJsonNull()) return null;
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
                return e.getAsBoolean();
            }
            return null;
        }
    }
}
