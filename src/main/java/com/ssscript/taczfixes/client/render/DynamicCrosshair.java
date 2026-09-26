package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.renderer.crosshair.CrosshairType;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.compat.shouldersurfing.ShoulderSurfingCompat;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;

/**
 * 动态准星: 根据当前枪械散布平滑地向外扩散/收缩。
 * 十字/线型贴图按中心区域分块平移四臂; 其它样式(圆/方/点/三叉)整体平滑缩放。
 */
@OnlyIn(Dist.CLIENT)
public final class DynamicCrosshair {
    private static final float BASE_OFFSET_PIXELS = 8.0f;
    private static final float MAX_OFFSET_PIXELS = 18.0f;
    private static final float MAX_RATIO = 3.0f;
    private static final float SMOOTH_TAU_SECONDS = 0.07f;
    private static final float MIN_RECOVER_SECONDS = 0.02f;
    private static final float JUMP_TAU_SECONDS = 0.15f;

    private static float displayedRatio;
    private static float fireImpulse;
    private static float jumpFactor = 1.0f;
    private static long lastFrameNanos = System.nanoTime();

    private DynamicCrosshair() {
    }

    /** 开火瞬间把准星扩散到配置倍率, 之后缓动回位(主手/副手开火都会调用)。 */
    public static void onShot(LocalPlayer player, ItemStack stack) {
        float expansion = Config.DYNAMIC_CROSSHAIR_FIRE_EXPANSION.get().floatValue();
        if (expansion > 1.0f) {
            fireImpulse = Math.max(fireImpulse, expansion - 1.0f);
        }
    }

    /** 绘制动态准星, 返回 true 表示已接管渲染。 */
    public static boolean render(GuiGraphics graphics, Window window) {
        if (!Config.DYNAMIC_CROSSHAIR_ENABLED.get()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.gameMode == null
                || minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return false;
        }
        if (!minecraft.options.getCameraType().isFirstPerson() && !ShoulderSurfingCompat.showCrosshair()) {
            return false;
        }
        CrosshairType type = RenderConfig.CROSSHAIR_TYPE.get();
        // 点状与空准星不参与动态扩散; 其它样式都按区域拉开各部分距离
        if (type == CrosshairType.DOT_1 || type == CrosshairType.EMPTY) {
            return false;
        }
        float offset = updateOffset();
        ResourceLocation texture = CrosshairType.getTextureLocation(type);
        float x = window.getGuiScaledWidth() / 2.0f - 8.0f;
        float y = window.getGuiScaledHeight() / 2.0f - 8.0f;
        PoseStack pose = graphics.pose();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.9f);
        drawExpandingParts(graphics, pose, texture, x, y, offset);
        return true;
    }

    private static float updateOffset() {
        long now = System.nanoTime();
        float dt = Mth.clamp((now - lastFrameNanos) / 1.0e9f, 0.0f, 0.1f);
        lastFrameNanos = now;
        float recover = Math.max(Config.DYNAMIC_CROSSHAIR_RECOVER_SECONDS.get().floatValue(), MIN_RECOVER_SECONDS);
        fireImpulse *= (float) Math.exp(-dt * 3.0f / recover);
        LocalPlayer player = Minecraft.getInstance().player;
        updateJumpFactor(player, dt);
        float targetRatio = 0.0f;
        if (player != null) {
            targetRatio = spreadRatio(player, player.getMainHandItem());
        }
        float alpha = 1.0f - (float) Math.exp(-dt / SMOOTH_TAU_SECONDS);
        displayedRatio += (targetRatio - displayedRatio) * alpha;
        float factor = Config.DYNAMIC_CROSSHAIR_FACTOR.get().floatValue();
        float base = Math.min(displayedRatio * factor * BASE_OFFSET_PIXELS, MAX_OFFSET_PIXELS);
        // 开火扩散 = 当前散布宽度 + (倍率-1) * 基准值, 而不是把当前大小乘以倍率
        float impulse = fireImpulse * factor * BASE_OFFSET_PIXELS;
        return base + impulse;
    }

    /** 滞空散布倍率平滑过渡, 避免落地/起跳和 onGround 抖动造成准星抽搐。 */
    private static void updateJumpFactor(LocalPlayer player, float dt) {
        float target = 1.0f;
        if (player != null && !player.onGround()) {
            ItemStack stack = player.getMainHandItem();
            IGun gun = IGun.getIGunOrNull(stack);
            if (gun != null) {
                ResourceLocation dataId = TaczFixesDataManager.resolveDataId(gun.getGunId(stack));
                GunTaczFixesData.JumpInaccuracyConfig jump = TaczFixesDataManager.getJumpInaccuracyConfig(dataId);
                jump = AttachmentTaczFixesManager.adjustJumpInaccuracy(stack, jump);
                if (jump != null && jump.multiplier != null && jump.speed != null
                        && jump.multiplier > 0.0 && jump.multiplier != 1.0) {
                    target = jump.multiplier.floatValue();
                }
            }
        }
        float alpha = 1.0f - (float) Math.exp(-dt / JUMP_TAU_SECONDS);
        jumpFactor += (target - jumpFactor) * alpha;
    }

    /**
     * 当前散布相对该枪站立散布的倍率(站立=1, 移动>1, 潜行/趴下<1, 滞空再乘滞空倍率)。
     * 相比固定区间归一化, 这样滞空倍率、配件等造成的整体散布变化也会反映为准星扩散。
     */
    private static float spreadRatio(LocalPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return 0.0f;
        }
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return 0.0f;
        }
        InaccuracyType type = InaccuracyType.getInaccuracyType(player);
        Map<InaccuracyType, Float> table = null;
        IGunOperator operator = IGunOperator.fromLivingEntity(player);
        AttachmentCacheProperty cache = operator == null ? null : operator.getCacheProperty();
        if (cache != null) {
            try {
                table = cache.getCache(GunProperties.INACCURACY);
            } catch (RuntimeException ignored) {
                table = null;
            }
        }
        if (table == null) {
            ClientGunIndex index = (ClientGunIndex) TimelessAPI.getClientGunIndex(gun.getGunId(stack)).orElse(null);
            GunData data = index == null ? null : index.getGunData();
            if (data != null) {
                table = data.getInaccuracy();
            }
        }
        if (table == null || table.isEmpty()) {
            return 1.0f;
        }
        // 滞空时统一按移动散布处理: 跳跃顶点速度会短暂低于判定阈值,
        // 否则会在 MOVE/STAND 间反复切换导致准星抽搐。
        if (type == InaccuracyType.STAND && !player.onGround() && table.get(InaccuracyType.MOVE) != null) {
            type = InaccuracyType.MOVE;
        }
        Float current = table.get(type);
        Float aim = table.get(InaccuracyType.AIM);
        float base = current == null ? 0.0f : current.floatValue();
        if (aim != null) {
            float aimingProgress = IClientPlayerGunOperator.fromLocalPlayer(player)
                    .getClientAimingProgress(Minecraft.getInstance().getFrameTime());
            if (aimingProgress > 0.0f) {
                base = Mth.lerp(aimingProgress, base, aim.floatValue());
            }
        }
        base *= jumpFactor;
        Float standValue = table.get(InaccuracyType.STAND);
        float reference = standValue == null ? base : standValue.floatValue();
        if (reference <= 1.0e-3f) {
            reference = Math.max(base, 0.05f);
        }
        return Mth.clamp(base / reference, 0.0f, MAX_RATIO);
    }

    /**
     * 按连通区域绘制: 每个连通的白色部分作为整体处理——普通部分沿远离中心的方向
     * 整体平移(十字四臂、方形四角、线条等), 包裹中心的环形部分整体外扩, 中心点不动。
     */
    private static void drawExpandingParts(GuiGraphics graphics, PoseStack pose, ResourceLocation texture,
                                           float x, float y, float offset) {
        CrosshairPartLayout layout = CrosshairPartLayout.get(texture);
        if (layout.width() <= 0 || layout.parts().isEmpty()) {
            graphics.blit(texture, (int) x, (int) y, 0, 0, 16, 16, 16, 16);
            return;
        }
        float scaleX = 16.0f / layout.width();
        float scaleY = 16.0f / layout.height();
        for (CrosshairPartLayout.Part part : layout.parts()) {
            float dx = 0.0f;
            float dy = 0.0f;
            float zoom = 1.0f;
            if (part.ringLike()) {
                zoom = 1.0f + Math.min(offset, 8.0f) / 8.0f;
            } else if (!part.centered()) {
                dx = part.dirX() * offset;
                dy = part.dirY() * offset;
            }
            pose.pushPose();
            pose.translate(x + 8.0f, y + 8.0f, 0.0f);
            pose.scale(scaleX * zoom, scaleY * zoom, 1.0f);
            pose.translate(-layout.width() / 2.0f + dx / scaleX, -layout.height() / 2.0f + dy / scaleY, 0.0f);
            for (CrosshairPartLayout.Strip strip : part.strips()) {
                graphics.blit(texture, strip.x(), strip.y(), strip.width(), strip.height(),
                        strip.x(), strip.y(), strip.width(), strip.height(),
                        layout.width(), layout.height());
            }
            pose.popPose();
        }
    }
}
