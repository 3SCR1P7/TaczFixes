package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.hud.CustomHudDefinition;
import com.ssscript.taczfixes.client.hud.CustomHudManager;
import com.ssscript.taczfixes.client.hud.CustomHudRenderer;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.overlay.GunHudOverlay;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 枪械 data 配置了 custom_hud 时隐藏 tacz 原生 HUD, 改为渲染自定义 HUD。 */
@Mixin(GunHudOverlay.class)
public class MixinGunHudOverlayCustomHud {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$renderCustomHud(ForgeGui gui, GuiGraphics graphics, float partialTick,
                                           int width, int height, CallbackInfo ci) {
        if (!RenderConfig.GUN_HUD_ENABLE.get()) return;
        CustomHudDefinition def = CustomHudManager.activeHud();
        if (def == null) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack stack = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return;
        CustomHudRenderer.render(graphics, def, player, stack, gun, width, height);
        ci.cancel();
    }
}
