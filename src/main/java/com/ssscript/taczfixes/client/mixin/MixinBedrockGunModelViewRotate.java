package com.ssscript.taczfixes.client.mixin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.util.RefitViewMode;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Mixin(BedrockGunModel.class)
public abstract class MixinBedrockGunModelViewRotate {

    @Unique
    private boolean taczfixes$viewTransformPushed;

    @Unique
    private static final Map<ResourceLocation, float[]> taczfixes$pivotCache = new HashMap<>();

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V",
            at = @At("HEAD"), remap = false)
    private void taczfixes$viewRotateHead(PoseStack pose, ItemStack stack, ItemDisplayContext displayContext,
                                          RenderType renderType, int light, int overlay, float red, float green, float blue, float alpha,
                                          net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource, CallbackInfo ci) {
        taczfixes$viewTransformPushed = false;
        boolean active = RefitViewMode.isActive();
        if (!active && !RefitViewMode.isTransitioning()) return;
        Screen screen = Minecraft.getInstance().screen;
        if (!(screen instanceof GunRefitScreen)) {
            if (active) return;
        }
        RefitViewMode.updateTransition();
        if (RefitViewMode.getYawDeg() == 0f && RefitViewMode.getRollDeg() == 0f
                && RefitViewMode.getDistance() == 1f
                && RefitViewMode.getPanX() == 0f && RefitViewMode.getPanY() == 0f) {
            return;
        }
        float[] center = taczfixes$modelCenter((BedrockGunModel) (Object) this, stack);
        if (center == null) return;
        pose.pushPose();
        pose.translate(center[0], center[1], center[2]);
        pose.mulPose(new Quaternionf().rotationXYZ(
                0.0f,
                (float) Math.toRadians(RefitViewMode.getYawDeg()),
                (float) Math.toRadians(RefitViewMode.getRollDeg())));
        float distance = RefitViewMode.getDistance();
        if (distance != 1f) {
            pose.scale(distance, distance, distance);
        }
        pose.translate(-center[0], -center[1], -center[2]);
        float panX = RefitViewMode.getPanX();
        float panY = RefitViewMode.getPanY();
        if (panX != 0f || panY != 0f) {
            Matrix4f mat = pose.last().pose();
            pose.translate(mat.m00() * panX - mat.m01() * panY,
                    mat.m10() * panX - mat.m11() * panY,
                    mat.m20() * panX - mat.m21() * panY);
        }
        taczfixes$viewTransformPushed = true;
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V",
            at = @At("TAIL"), remap = false)
    private void taczfixes$viewRotateTail(PoseStack pose, ItemStack stack, ItemDisplayContext displayContext,
                                          RenderType renderType, int light, int overlay, float red, float green, float blue, float alpha,
                                          net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource, CallbackInfo ci) {
        if (taczfixes$viewTransformPushed) {
            pose.popPose();
        }
    }

    @Unique
    private static float[] taczfixes$modelCenter(BedrockGunModel model, ItemStack stack) {
        float[] ground = taczfixes$groundXZ(stack);
        BedrockPart root = model.getRootNode();
        if (root == null) return null;
        PoseStack pose = new PoseStack();
        root.translateAndRotateAndScale(pose);
        Matrix4f mat = pose.last().pose();
        return new float[]{ground[0], mat.m31(), ground[1]};
    }

    @Unique
    private static float[] taczfixes$groundXZ(ItemStack stack) {
        if (stack.getItem() instanceof IGun gun) {
            ResourceLocation gunId = gun.getGunId(stack);
            if (gunId != null) {
                float[] cached = taczfixes$pivotCache.get(gunId);
                if (cached != null) return cached;
                float[] pivot = taczfixes$readJsonPivot(gunId);
                if (pivot != null) {
                    taczfixes$pivotCache.put(gunId, pivot);
                    return pivot;
                }
            }
        }
        return new float[]{0f, 0f};
    }

    @Unique
    private static float[] taczfixes$readJsonPivot(ResourceLocation gunId) {
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
                float[] pivot = taczfixes$readPivot(bone);
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

    @Unique
    private static float[] taczfixes$readPivot(JsonObject bone) {
        JsonArray pivot = bone.has("pivot") ? bone.getAsJsonArray("pivot")
                : (bone.has("position") ? bone.getAsJsonArray("position") : null);
        if (pivot == null || pivot.size() < 3) return null;
        return new float[]{pivot.get(0).getAsFloat(), pivot.get(1).getAsFloat(), pivot.get(2).getAsFloat()};
    }
}