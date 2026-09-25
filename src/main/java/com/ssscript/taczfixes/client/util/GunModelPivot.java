package com.ssscript.taczfixes.client.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** 枪械模型的 positioning/ground 组中心(与改装界面拖动旋转使用的中心一致)。 */
public final class GunModelPivot {
    private static final Map<ResourceLocation, float[]> PIVOT_CACHE = new HashMap<>();

    private GunModelPivot() {
    }

    /** 返回 {x, y, z} 模型中心; 无法解析时返回 null。 */
    public static float[] center(BedrockGunModel model, ItemStack stack) {
        if (model == null || stack == null) return null;
        float[] ground = groundXZ(stack);
        BedrockPart root = model.getRootNode();
        if (root == null) return null;
        PoseStack pose = new PoseStack();
        root.translateAndRotateAndScale(pose);
        Matrix4f mat = pose.last().pose();
        return new float[]{ground[0], mat.m31(), ground[1]};
    }

    private static float[] groundXZ(ItemStack stack) {
        if (stack.getItem() instanceof IGun gun) {
            ResourceLocation gunId = gun.getGunId(stack);
            if (gunId != null) {
                float[] cached = PIVOT_CACHE.get(gunId);
                if (cached != null) return cached;
                float[] pivot = readJsonPivot(gunId);
                if (pivot != null) {
                    PIVOT_CACHE.put(gunId, pivot);
                    return pivot;
                }
            }
        }
        return new float[]{0f, 0f};
    }

    private static float[] readJsonPivot(ResourceLocation gunId) {
        ResourceLocation geoPath = new ResourceLocation(gunId.getNamespace(),
                "geo_models/gun/" + gunId.getPath() + "_geo.json");
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(geoPath);
        if (res.isEmpty()) return null;
        try (InputStream in = res.get().open();
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = new Gson().fromJson(reader, JsonObject.class);
            JsonArray geometries = null;
            if (root.has("minecraft:geometry")) {
                geometries = root.getAsJsonArray("minecraft:geometry");
            } else if (root.has("geometry_model")) {
                geometries = new JsonArray();
                geometries.add(root.getAsJsonObject("geometry_model"));
            }
            if (geometries == null || geometries.size() == 0) return null;
            JsonArray bones = geometries.get(0).getAsJsonObject().getAsJsonArray("bones");
            float[] out = new float[]{0f, 0f};
            for (JsonElement e : bones) {
                JsonObject bone = e.getAsJsonObject();
                if (!bone.has("name")) continue;
                String name = bone.get("name").getAsString();
                if (!"ground".equals(name)) continue;
                float[] pivot = readPivot(bone);
                if (pivot == null) break;
                out[0] = pivot[0] / 16f;
                out[1] = pivot[2] / 16f;
                break;
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static float[] readPivot(JsonObject bone) {
        JsonArray pivot = bone.has("pivot") ? bone.getAsJsonArray("pivot")
                : (bone.has("position") ? bone.getAsJsonArray("position") : null);
        if (pivot == null || pivot.size() < 3) return null;
        return new float[]{pivot.get(0).getAsFloat(), pivot.get(1).getAsFloat(), pivot.get(2).getAsFloat()};
    }
}
