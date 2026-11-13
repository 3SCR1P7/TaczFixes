package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.screen.GunPackUpdateBadgeSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 仅在我们的枪包管理界面: 给有更新的枪包图标右上角画红点。 */
@Mixin(TransferableSelectionList.PackEntry.class)
public abstract class MixinPackEntryUpdateBadge {
    private static final int ICON_SIZE = 32;
    private static final int DOT_OUTLINE_COLOR = 0xFF4A0000;
    private static final int DOT_COLOR = 0xFFFF3B30;

    @Shadow
    public abstract String getPackId();

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIIIIIIZF)V", at = @At("TAIL"))
    private void taczfixes$renderUpdateBadge(GuiGraphics guiGraphics, int index, int top, int left, int width,
                                             int height, int mouseX, int mouseY, boolean hovered,
                                             float partialTick, CallbackInfo callback) {
        if (!(Minecraft.getInstance().screen instanceof GunPackUpdateBadgeSource source)) return;
        String packId = this.getPackId();
        if (packId == null) return;
        if (source.taczfixes$hasUpdate(packId)) {
            int dotX = left + ICON_SIZE - 7;
            int dotY = top + 1;
            guiGraphics.fill(dotX, dotY, dotX + 7, dotY + 7, DOT_OUTLINE_COLOR);
            guiGraphics.fill(dotX + 1, dotY + 1, dotX + 6, dotY + 6, DOT_COLOR);
        }
    }
}
