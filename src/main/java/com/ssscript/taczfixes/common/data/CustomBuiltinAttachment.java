package com.ssscript.taczfixes.common.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 自定义配件槽的原厂配件配置(builtin_attachments):
 * attachments 为可选原厂候选列表, default_attached 为默认预装的虚拟原厂件, show_icon 控制是否在改装界面显示。
 * 兼容 tacz 的写法: 字符串 / 字符串数组 / 对象。
 */
@JsonAdapter(CustomBuiltinAttachment.Adapter.class)
public class CustomBuiltinAttachment {
    public List<String> attachments;
    public String default_attached;
    public Boolean show_icon;

    public List<String> getAttachments() {
        return attachments == null ? Collections.emptyList() : attachments;
    }

    public boolean isShowIcon() {
        return Boolean.TRUE.equals(show_icon);
    }

    public static class Adapter implements JsonDeserializer<CustomBuiltinAttachment>,
            JsonSerializer<CustomBuiltinAttachment> {

        @Override
        public CustomBuiltinAttachment deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            CustomBuiltinAttachment out = new CustomBuiltinAttachment();
            out.attachments = new ArrayList<>();
            if (json == null || json.isJsonNull()) {
                return out;
            }
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                out.attachments.add(json.getAsString());
                return out;
            }
            if (json.isJsonArray()) {
                parseList(json.getAsJsonArray(), out.attachments);
                return out;
            }
            if (!json.isJsonObject()) {
                throw new JsonParseException("builtin_attachments must be a string, array, or object");
            }
            JsonObject obj = json.getAsJsonObject();
            JsonElement attachments = obj.get("attachments");
            if (attachments != null) {
                if (attachments.isJsonArray()) {
                    parseList(attachments.getAsJsonArray(), out.attachments);
                } else if (attachments.isJsonPrimitive() && attachments.getAsJsonPrimitive().isString()) {
                    out.attachments.add(attachments.getAsString());
                } else {
                    throw new JsonParseException("builtin_attachments.attachments must be a string or string array");
                }
            }
            JsonElement def = obj.has("default_attached") ? obj.get("default_attached")
                    : (obj.has("default") ? obj.get("default") : null);
            if (def != null && def.isJsonPrimitive() && def.getAsJsonPrimitive().isString()) {
                out.default_attached = def.getAsString();
            }
            JsonElement showIcon = obj.get("show_icon");
            if (showIcon != null && showIcon.isJsonPrimitive() && showIcon.getAsJsonPrimitive().isBoolean()) {
                out.show_icon = showIcon.getAsBoolean();
            }
            return out;
        }

        private static void parseList(JsonArray array, List<String> out) {
            for (JsonElement e : array) {
                if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                    out.add(e.getAsString());
                }
            }
        }

        @Override
        public JsonElement serialize(CustomBuiltinAttachment src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            if (src.attachments != null && !src.attachments.isEmpty()) {
                JsonArray array = new JsonArray();
                for (String id : src.attachments) {
                    if (id != null && !id.isEmpty()) {
                        array.add(id);
                    }
                }
                obj.add("attachments", array);
            }
            if (src.default_attached != null && !src.default_attached.isEmpty()) {
                obj.addProperty("default_attached", src.default_attached);
            }
            if (src.show_icon != null) {
                obj.addProperty("show_icon", src.show_icon);
            }
            return obj;
        }
    }
}
