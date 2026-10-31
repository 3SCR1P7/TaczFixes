package com.ssscript.taczfixes.common.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import com.mojang.serialization.JsonOps;

import java.util.Map;

/** 1.20 枪包的 loot injector 仍使用 minecraft:set_nbt, 1.21.1 中需转换为 set_components/custom_data。 */
public final class LegacyLootFunctionFixer {
    private LegacyLootFunctionFixer() {
    }

    public static JsonElement fix(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return element;
        }
        if (element.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                result.add(fix(child));
            }
            return result;
        }
        if (!element.isJsonObject()) {
            return element;
        }
        JsonObject object = element.getAsJsonObject();
        JsonObject fixed = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            fixed.add(entry.getKey(), fix(entry.getValue()));
        }
        JsonElement function = fixed.get("function");
        if (function == null || !function.isJsonPrimitive()
                || !"minecraft:set_nbt".equals(function.getAsString())) {
            return fixed;
        }
        JsonObject converted = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : fixed.entrySet()) {
            if (!"function".equals(entry.getKey()) && !"tag".equals(entry.getKey())) {
                converted.add(entry.getKey(), entry.getValue());
            }
        }
        converted.addProperty("function", "minecraft:set_components");
        JsonElement tagElement = fixed.get("tag");
        Tag tag = parseTag(tagElement);
        if (tag != null) {
            JsonElement tagJson = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag);
            JsonObject components = new JsonObject();
            components.add("minecraft:custom_data", tagJson);
            converted.add("components", components);
        }
        return converted;
    }

    private static Tag parseTag(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            if (element.isJsonPrimitive()) {
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                if (primitive.isString()) {
                    return TagParser.parseTag(primitive.getAsString());
                }
            }
            return JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, element);
        } catch (Exception ignored) {
            return null;
        }
    }
}
