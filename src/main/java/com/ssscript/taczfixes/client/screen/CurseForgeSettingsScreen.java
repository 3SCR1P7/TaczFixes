package com.ssscript.taczfixes.client.screen;

import com.ssscript.taczfixes.client.curseforge.CurseForgeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** CurseForge 下载设置: API Key / API 地址 / 文件下载地址前缀。 */
public class CurseForgeSettingsScreen extends Screen {
    private final Screen parent;
    private EditBox apiKeyBox;
    private EditBox apiBaseBox;
    private EditBox fileBaseBox;
    private Component error;

    public CurseForgeSettingsScreen(Screen parent) {
        super(Component.translatable("gui.taczfixes.curseforge.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        CurseForgeConfig.ensureLoaded();
        int fieldWidth = Math.min(308, this.width - 40);
        int left = this.width / 2 - fieldWidth / 2;
        this.apiKeyBox = new EditBox(this.font, left, 64, fieldWidth, 20,
                Component.translatable("gui.taczfixes.curseforge.settings.api_key.hint"));
        this.apiKeyBox.setMaxLength(200);
        this.apiKeyBox.setValue(CurseForgeConfig.apiKey());
        this.apiKeyBox.setFormatter((text, cursor) -> FormattedCharSequence.forward(
                text == null || text.isEmpty() ? "" : "*".repeat(text.length()), Style.EMPTY));
        this.addRenderableWidget(this.apiKeyBox);

        this.apiBaseBox = new EditBox(this.font, left, 108, fieldWidth, 20,
                Component.literal(CurseForgeConfig.DEFAULT_API_BASE));
        this.apiBaseBox.setMaxLength(300);
        this.apiBaseBox.setValue(CurseForgeConfig.apiBase());
        this.addRenderableWidget(this.apiBaseBox);

        this.fileBaseBox = new EditBox(this.font, left, 152, fieldWidth, 20,
                Component.literal(CurseForgeConfig.DEFAULT_FILE_BASE));
        this.fileBaseBox.setMaxLength(300);
        this.fileBaseBox.setValue(CurseForgeConfig.fileBase());
        this.addRenderableWidget(this.fileBaseBox);

        int buttonWidth = Math.min(150, (this.width - 48) / 2);
        int buttonY = this.height - 40;
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.save())
                .bounds(this.width / 2 - buttonWidth - 4, buttonY, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.cancel())
                .bounds(this.width / 2 + 4, buttonY, buttonWidth, 20).build());
    }

    private void save() {
        String api = this.apiBaseBox.getValue() == null ? "" : this.apiBaseBox.getValue().trim();
        String files = this.fileBaseBox.getValue() == null ? "" : this.fileBaseBox.getValue().trim();
        if (!api.isEmpty() && !CurseForgeConfig.isValidBase(api)) {
            this.error = Component.translatable("gui.taczfixes.curseforge.settings.invalid_api");
            return;
        }
        if (!files.isEmpty() && !CurseForgeConfig.isValidBase(files)) {
            this.error = Component.translatable("gui.taczfixes.curseforge.settings.invalid_file");
            return;
        }
        if (!CurseForgeConfig.update(this.apiKeyBox.getValue(), api, files)) {
            this.error = Component.translatable("gui.taczfixes.curseforge.settings.save_failed");
            return;
        }
        this.back();
    }

    private void cancel() {
        this.back();
    }

    private void back() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void onClose() {
        this.back();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1.21.1: 背景由 super.render 绘制, 文本在控件之上补画
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int maxWidth = this.width - 20;
        guiGraphics.drawCenteredString(this.font, this.title, centerX, 12, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, this.trim(
                        Component.translatable("gui.taczfixes.curseforge.settings.note").getString(), maxWidth),
                centerX, 26, 0xFFA0A0A0);
        guiGraphics.drawCenteredString(this.font, this.trim(
                        Component.translatable("gui.taczfixes.curseforge.settings.mirror_note").getString(), maxWidth),
                centerX, 38, 0xFFA0A0A0);

        int fieldWidth = Math.min(308, this.width - 40);
        int left = centerX - fieldWidth / 2;
        guiGraphics.drawString(this.font, Component.translatable("gui.taczfixes.curseforge.settings.api_key"),
                left, 54, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.taczfixes.curseforge.settings.api_base"),
                left, 98, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.taczfixes.curseforge.settings.file_base"),
                left, 142, 0xFFFFFF, false);
        if (this.error != null) {
            guiGraphics.drawCenteredString(this.font, this.error, centerX, this.height - 64, 0xFFFF5555);
        }
    }

    private String trim(String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) return "";
        if (this.font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int keep = maxWidth - this.font.width(ellipsis);
        if (keep <= 0) return ellipsis;
        return this.font.plainSubstrByWidth(text, keep) + ellipsis;
    }
}
