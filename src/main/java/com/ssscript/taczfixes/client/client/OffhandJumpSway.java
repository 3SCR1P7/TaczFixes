package com.ssscript.taczfixes.client.client;

import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.util.math.SecondOrderDynamics;
import com.tacz.guns.util.time.GunTime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** 复刻 TaCZ 主手第一人称的起跳/落地小幅晃动, 用于副手枪械。 */
@OnlyIn(Dist.CLIENT)
public final class OffhandJumpSway {
    private static final SecondOrderDynamics JUMPING_DYNAMICS = new SecondOrderDynamics(0.28F, 1.0F, 0.65F, 0.0F);
    private static final float JUMPING_Y_SWAY = -2.0F;
    private static float jumpingSwayProgress;
    private static boolean lastOnGround;
    private static long jumpingTimeStamp = -1L;

    private OffhandJumpSway() {
    }

    public static void apply(BedrockAnimatedModel model, float partialTicks) {
        if (jumpingTimeStamp == -1L) {
            jumpingTimeStamp = GunTime.nowMillis();
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && partialTicks > 0.0f) {
            double posY = Mth.lerp(partialTicks, player.yOld, player.getY());
            float velocityY = (float) (posY - player.yOld) / partialTicks;
            if (player.onGround()) {
                if (!lastOnGround) {
                    jumpingSwayProgress = velocityY / -0.1F;
                    if (jumpingSwayProgress > 1.0F) {
                        jumpingSwayProgress = 1.0F;
                    }
                    lastOnGround = true;
                } else {
                    jumpingSwayProgress -= (float) (GunTime.nowMillis() - jumpingTimeStamp) / 150.0F;
                    if (jumpingSwayProgress < 0.0F) {
                        jumpingSwayProgress = 0.0F;
                    }
                }
            } else if (lastOnGround) {
                jumpingSwayProgress = velocityY / 0.42F;
                if (jumpingSwayProgress > 1.0F) {
                    jumpingSwayProgress = 1.0F;
                }
                lastOnGround = false;
            } else {
                jumpingSwayProgress -= (float) (GunTime.nowMillis() - jumpingTimeStamp) / 300.0F;
                if (jumpingSwayProgress < 0.0F) {
                    jumpingSwayProgress = 0.0F;
                }
            }
        }
        jumpingTimeStamp = GunTime.nowMillis();
        float ySway = JUMPING_DYNAMICS.update(JUMPING_Y_SWAY * jumpingSwayProgress);
        BedrockPart rootNode = model == null ? null : model.getRootNode();
        if (rootNode != null) {
            rootNode.offsetY += -ySway / 16.0F;
        }
    }

    public static void reset() {
        jumpingTimeStamp = -1L;
        jumpingSwayProgress = 0.0F;
        lastOnGround = false;
    }
}