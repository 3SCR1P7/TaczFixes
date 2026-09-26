package com.ssscript.taczfixes.client.handler;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ssscript.taczfixes.common.util.CrawlHitboxHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** F3+B 时以蓝色绘制爬行玩家的独立受击碰撞箱。 */
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
        if (!com.ssscript.taczfixes.common.config.Config.CRAWL_HITBOX_ENABLED.get()) {
            return;
        }
        float partialTick = event.getPartialTick();
        Vec3 cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        boolean rendered = false;
        for (AbstractClientPlayer player : minecraft.level.players()) {
            if (!CrawlHitboxHelper.isCrawling(player)) {
                continue;
            }
            Vec3 pos = player.getPosition(partialTick);
            float yaw = Mth.rotLerp(partialTick, player.yRotO, player.getYRot());
            poseStack.pushPose();
            poseStack.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
            LevelRenderer.renderLineBox(poseStack, lines,
                    -CrawlHitboxHelper.WIDTH / 2.0d, 0.0d, -CrawlHitboxHelper.LENGTH / 2.0d,
                    CrawlHitboxHelper.WIDTH / 2.0d, CrawlHitboxHelper.HEIGHT, CrawlHitboxHelper.LENGTH / 2.0d,
                    0.0f, 0.0f, 1.0f, 1.0f);
            poseStack.popPose();
            rendered = true;
        }
        if (rendered) {
            buffers.endBatch(RenderType.lines());
        }
    }
}
