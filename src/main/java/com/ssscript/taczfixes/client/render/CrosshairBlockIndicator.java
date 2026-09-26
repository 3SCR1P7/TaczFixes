package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.util.GunBlocking;
import com.ssscript.taczfixes.common.util.UnderwaterShooting;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.compat.shouldersurfing.ShoulderSurfingCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** 水下或因方块阻挡无法开枪时, 用红色禁止符号替代准星。 */
@OnlyIn(Dist.CLIENT)
public final class CrosshairBlockIndicator {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TaczFixesMod.MOD_ID, "textures/gui/blocked_crosshair.png");

    private CrosshairBlockIndicator() {
    }

    /** 返回 true 表示已绘制禁止符号并接管准星渲染。 */
    public static boolean render(GuiGraphics graphics, Window window) {
        if (!com.ssscript.taczfixes.common.config.Config.BLOCKED_CROSSHAIR_ENABLED.get()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || minecraft.gameMode == null
                || minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return false;
        }
        if (!minecraft.options.getCameraType().isFirstPerson() && !ShoulderSurfingCompat.showCrosshair()) {
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        if (IGun.getIGunOrNull(stack) == null) {
            return false;
        }
        if (!UnderwaterShooting.isBlocked(player, stack) && !GunBlocking.isFireDisabled(player, stack)) {
            return false;
        }
        int size = com.ssscript.taczfixes.common.config.Config.BLOCKED_CROSSHAIR_SIZE.get();
        int x = window.getGuiScaledWidth() / 2 - size / 2;
        int y = window.getGuiScaledHeight() / 2 - size / 2;
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        // 显式指定绘制尺寸, 与纹理文件分辨率无关(源区域 16x16 缩放到 size x size)
        graphics.blit(TEXTURE, x, y, size, size, 0.0f, 0.0f, 16, 16, 16, 16);
        return true;
    }
}
