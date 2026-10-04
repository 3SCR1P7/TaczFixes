package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.GunRecolorGuiState;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.components.FlatColorButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 改装界面"调色"入口与调色面板(打开时隐藏其它全部控件)。 */
@Mixin(GunRefitScreen.class)
public abstract class MixinGunRefitScreenRecolor extends Screen {

    protected MixinGunRefitScreenRecolor(LocalPlayer player) {
        super(Component.literal(""));
    }

    @Inject(method = "addAttachmentTypeButtons", at = @At("TAIL"), remap = false)
    private void taczfixes$addRecolorButton(CallbackInfo ci) {
        if (GunRecolorGuiState.isOpen()) {
            GunRecolorGuiState.buildPanel((Screen) (Object) this, this::addRenderableWidget, this::init);
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

    @Inject(method = "m_88315_", at = @At("HEAD"), remap = false)
    private void taczfixes$sweepWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                        CallbackInfo ci) {
        if (GunRecolorGuiState.isOpen()) {
            GunRecolorGuiState.sweep((Screen) (Object) this);
        }
    }

    @Inject(method = "m_7379_", at = @At("TAIL"), remap = false)
    private void taczfixes$closeRecolor(CallbackInfo ci) {
        GunRecolorGuiState.closeAndSend();
    }
}
