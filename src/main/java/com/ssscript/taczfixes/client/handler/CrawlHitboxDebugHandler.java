package com.ssscript.taczfixes.client.handler;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.util.CrawlHitboxHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** F3+B 时以蓝色绘制本模组接管的受击箱(游泳/爬行长条箱、旋转站立箱)。 */
@OnlyIn(Dist.CLIENT)
public class CrawlHitboxDebugHandler {
    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes()) {
            return;
        }
        if (!Config.CRAWL_HITBOX_ENABLED.get() && !Config.STANDING_HITBOX_ENABLED.get()) {
            return;
        }
        float partialTick = event.getPartialTick();
        Vec3 cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        boolean rendered = false;
        // 第一人称下不绘制自己的判定箱(否则会贴脸挡住视线)。
        boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
        for (AbstractClientPlayer player : minecraft.level.players()) {
            if (firstPerson && player == minecraft.player) {
                continue;
            }
            float yaw = Mth.rotLerp(partialTick, player.yRotO, player.getYRot());
            if (CrawlHitboxHelper.isHorizontalPose(player)) {
                if (!Config.CRAWL_HITBOX_ENABLED.get()) {
                    continue;
                }
                Vec3 pos = player.getPosition(partialTick);
                poseStack.pushPose();
                poseStack.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z);
                poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
                LevelRenderer.renderLineBox(poseStack, lines,
                        -CrawlHitboxHelper.WIDTH / 2.0d, 0.0d, -CrawlHitboxHelper.LENGTH / 2.0d,
                        CrawlHitboxHelper.WIDTH / 2.0d, CrawlHitboxHelper.HEIGHT, CrawlHitboxHelper.LENGTH / 2.0d,
                        0.0f, 0.0f, 1.0f, 1.0f);
                poseStack.popPose();
                rendered = true;
                continue;
            }
            if (!Config.STANDING_HITBOX_ENABLED.get()) {
                continue;
            }
            // 用插值后的位置 + 当前判定箱尺寸绘制, 否则玩家移动时蓝框会落后一 tick。
            AABB box = player.getBoundingBox();
            Vec3 center = player.getPosition(partialTick).add(box.getCenter().subtract(player.position()));
            double halfX = box.getXsize() / 2.0d;
            double halfY = box.getYsize() / 2.0d;
            double halfZ = box.getZsize() / 2.0d;
            poseStack.pushPose();
            poseStack.translate(center.x - cameraPos.x, center.y - cameraPos.y, center.z - cameraPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
            LevelRenderer.renderLineBox(poseStack, lines, -halfX, -halfY, -halfZ, halfX, halfY, halfZ,
                    0.0f, 0.0f, 1.0f, 1.0f);
            poseStack.popPose();
            rendered = true;
        }
        if (rendered) {
            buffers.endBatch(RenderType.lines());
        }
    }
}
