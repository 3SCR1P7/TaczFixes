package com.ssscript.taczfixes.common.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

public class RecoilConfigJsonParser {
    private static final Logger LOGGER = LogManager.getLogger("taczfixes");

    private RecoilConfigJsonParser() {
    }

    public static void collectRecoilModifiers(Map<String, GunTaczFixesData.RecoilConfig> recoilMap, JsonObject recoilRoot) {
        if (recoilMap == null) return;
        for (Map.Entry<String, JsonElement> modeEntry : recoilRoot.entrySet()) {
            GunTaczFixesData.RecoilConfig config = recoilMap.get(modeEntry.getKey());
            if (config == null || !modeEntry.getValue().isJsonObject()) continue;
            JsonObject modeObj = modeEntry.getValue().getAsJsonObject();
            Map<String, GunTaczFixesData.RecoilModifierConfig> mods = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> modEntry : modeObj.entrySet()) {
                String key = modEntry.getKey();
                if ("window".equals(key) || "pitch_multiplier".equals(key)
                        || "yaw_multiplier".equals(key) || "modifiers".equals(key)) continue;
                if (!modEntry.getValue().isJsonObject()) continue;
                GunTaczFixesData.RecoilModifierConfig mod =
                        parseRecoilModifier(modEntry.getValue().getAsJsonObject());
                if (mod != null) {
                    mods.put(key, mod);
                } else {
                    LOGGER.warn("taczfixes: recoil modifier {} of mode {} ignored, invalid count", key, modeEntry.getKey());
                }
            }
            if (mods.isEmpty()) continue;
            if (config.modifiers == null) {
                config.modifiers = mods;
            } else {
                config.modifiers.putAll(mods);
            }
        }
    }

    public static GunTaczFixesData.RecoilModifierConfig parseRecoilModifier(JsonObject modObj) {
        GunTaczFixesData.RecoilModifierConfig mod = new GunTaczFixesData.RecoilModifierConfig();
        JsonElement countEl = modObj.get("count");
        if (countEl != null) {
            if (countEl.isJsonArray()) {
                JsonArray arr = countEl.getAsJsonArray();
                if (arr.size() < 1) {
                    return null;
                }
                if (!isNumber(arr.get(0))) {
                    return null;
                }
                mod.count_start = arr.get(0).getAsInt();
                if (arr.size() >= 2) {
                    JsonElement second = arr.get(1);
                    if (isInfinite(second)) {
                        mod.count_end = null;
                    } else if (isNumber(second)) {
                        mod.count_end = second.getAsInt();
                    } else {
                        return null;
                    }
                }
                if (arr.size() >= 3) {
                    if (!isNumber(arr.get(2))) {
                        return null;
                    }
                    mod.count_step = arr.get(2).getAsInt();
                }
            } else if (countEl.isJsonPrimitive()) {
                mod.count = countEl.getAsInt();
            } else {
                return null;
            }
        }
        mod.pitch_multiplier = readDouble(modObj, "pitch_multiplier");
        mod.yaw_multiplier = readDouble(modObj, "yaw_multiplier");
        return mod;
    }

    private static boolean isNumber(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber();
    }

    private static boolean isInfinite(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                && "infinite".equals(element.getAsString());
    }

    public static Double readDouble(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (element != null && element.isJsonPrimitive()) {
            return element.getAsDouble();
        }
        return null;
    }
}
