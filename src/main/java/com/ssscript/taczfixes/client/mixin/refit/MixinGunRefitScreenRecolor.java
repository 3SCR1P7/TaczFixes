package com.ssscript.taczfixes.client.mixin.refit;

import com.ssscript.taczfixes.client.util.GunRecolorGuiState;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.components.FlatColorButton;
import com.tacz.guns.client.gui.components.refit.GunPropertyDiagrams;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 改装界面"调色"入口与调色面板(打开时隐藏其它全部控件)。 */
@Mixin(GunRefitScreen.class)
public abstract class MixinGunRefitScreenRecolor extends Screen {

    protected MixinGunRefitScreenRecolor(LocalPlayer player) {
        super(Component.literal(""));
    }

    @Inject(method = "addAttachmentTypeButtons", at = @At("TAIL"), remap = false)
    private void taczfixes$addRecolorButton(CallbackInfo ci) {
        ItemStack gun = GunRecolorGuiState.gun();
        ItemStack target = GunRecolorGuiState.resolveTarget(gun);
        boolean hide;
        if (target.getItem() instanceof com.tacz.guns.api.item.IGun) {
            // 枪械本体: 安装了 arcana 涂装模块(调色喷罐)时隐藏入口, 让位给 arcana。
            hide = com.ssscript.taczfixes.client.util.GunRecolorManager.hasPaintModule(gun);
        } else {
            // 配件: 无 uv 贴图的配件不提供调色入口。
            hide = com.ssscript.taczfixes.client.util.GunRecolorManager.originalTexture(target) == null;
        }
        if (hide) {
            if (GunRecolorGuiState.isOpen()) {
                GunRecolorGuiState.close();
            }
            return;
        }
        if (GunRecolorGuiState.isOpen()) {
            GunRecolorGuiState.buildPanel((Screen) (Object) this,
                    widget -> this.addRenderableWidget(widget),
                    () -> this.init());
            return;
        }
        if (!com.ssscript.taczfixes.common.config.Config.REFITSCREEN_SHOW_RECOLOR_BUTTON.get()) {
            return;
        }
        // 使用与 TaCZ "显示图表" 相同的 FlatColorButton, 保证透明度/样式一致;
        // 该按钮位于 (11, 11, 288, 16), 调色按钮放在其右侧并与之对齐
        FlatColorButton button = new FlatColorButton(303, 11, 48, 16,
                Component.translatable("gui.taczfixes.recolor.button"), pressed -> {
            GunRecolorGuiState.open();
            this.init();
        });
        this.addRenderableWidget(button);
    }

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void taczfixes$sweepWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                        CallbackInfo ci) {
        if (GunRecolorGuiState.isOpen()) {
            GunRecolorGuiState.sweep((Screen) (Object) this);
        }
    }

    /** 调色界面打开时连同"显示图表"画出的图表一起隐藏。 */
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/client/gui/components/refit/GunPropertyDiagrams;draw" +
                    "(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;II)V",
            remap = false), remap = false)
    private void taczfixes$hideDiagrams(GuiGraphics graphics, Font font, int x, int y) {
        if (GunRecolorGuiState.isOpen()) {
            return;
        }
        GunPropertyDiagrams.draw(graphics, font, x, y);
    }

    @Inject(method = "onClose", at = @At("TAIL"), remap = false)
    private void taczfixes$closeRecolor(CallbackInfo ci) {
        GunRecolorGuiState.closeAndSend();
    }
}
