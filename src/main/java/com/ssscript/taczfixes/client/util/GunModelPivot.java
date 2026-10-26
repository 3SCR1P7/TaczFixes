package com.ssscript.taczfixes.client.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.bedrock.BedrockCube;
import com.tacz.guns.client.model.bedrock.BedrockCubeBox;
import com.tacz.guns.client.model.bedrock.BedrockCubePerFace;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

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

    public static float[] centerStable(BedrockGunModel model, ItemStack stack) {
        if (model == null || stack == null) return null;
        BedrockPart root = model.getRootNode();
        if (root == null) return null;
        float[] ground = groundXZ(stack);
        return new float[]{ground[0], root.y / 16f, ground[1]};
    }
    public static float[] centerAnimated(BedrockGunModel model, ItemStack stack) {
        if (model == null || stack == null) return null;
        BedrockPart root = model.getRootNode();
        if (root == null) return null;
        Bounds bounds = new Bounds();
        PoseStack pose = new PoseStack();
        collectBounds(root, pose, bounds);
        if (!bounds.valid) return centerStable(model, stack);
        Vector3f center = new Vector3f(
                (bounds.minX + bounds.maxX) * 0.5f,
                (bounds.minY + bounds.maxY) * 0.5f,
                (bounds.minZ + bounds.maxZ) * 0.5f);
        return new float[]{center.x, center.y, center.z};
    }

    private static void collectBounds(BedrockPart part, PoseStack pose, Bounds bounds) {
        if (!part.visible) return;
        pose.pushPose();
        part.translateAndRotateAndScale(pose);
        String name = part.name == null ? "" : part.name.toLowerCase();
        boolean handPart = name.contains("righthand") || name.contains("lefthand")
                || name.contains("right_hand") || name.contains("left_hand")
                || name.equals("arm") || name.endsWith("_arm");
        if (!handPart) {
            Matrix4f matrix = pose.last().pose();
            for (BedrockCube cube : part.cubes) {
                float minX;
                float minY;
                float minZ;
                float maxX;
                float maxY;
                float maxZ;
                if (cube instanceof BedrockCubeBox box) {
                    minX = box.minX;
                    minY = box.minY;
                    minZ = box.minZ;
                    maxX = box.maxX;
                    maxY = box.maxY;
                    maxZ = box.maxZ;
                } else if (cube instanceof BedrockCubePerFace face) {
                    minX = face.minX;
                    minY = face.minY;
                    minZ = face.minZ;
                    maxX = face.maxX;
                    maxY = face.maxY;
                    maxZ = face.maxZ;
                } else {
                    continue;
                }
                includeCorner(matrix, bounds, minX, minY, minZ);
                includeCorner(matrix, bounds, minX, minY, maxZ);
                includeCorner(matrix, bounds, minX, maxY, minZ);
                includeCorner(matrix, bounds, minX, maxY, maxZ);
                includeCorner(matrix, bounds, maxX, minY, minZ);
                includeCorner(matrix, bounds, maxX, minY, maxZ);
                includeCorner(matrix, bounds, maxX, maxY, minZ);
                includeCorner(matrix, bounds, maxX, maxY, maxZ);
            }
        }
        for (BedrockPart child : part.children) {
            collectBounds(child, pose, bounds);
        }
        pose.popPose();
    }

    private static void includeCorner(Matrix4f matrix, Bounds bounds, float x, float y, float z) {
        Vector3f point = new Vector3f(x / 16.0f, y / 16.0f, z / 16.0f);
        matrix.transformPosition(point);
        bounds.include(point);
    }

    private static final class Bounds {
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;
        private boolean valid;

        private void include(Vector3f point) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
            maxZ = Math.max(maxZ, point.z);
            valid = true;
        }
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
        ResourceLocation geoPath = ResourceLocation.fromNamespaceAndPath(gunId.getNamespace(),
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
