package com.ssscript.taczfixes.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * 枪包管理界面(复用原版 PackSelectionScreen), 在底部按钮行左侧加一个“从 CurseForge 获取”按钮。
 *
 * 1.21.1 的原版界面改用 HeaderAndFooterLayout(底部按钮行高 33, “打开文件夹/完成”居中),
 * 这里把按钮放在同一行的左侧, 宽度按左侧剩余空间自适应; 空间不足时用短标签避免遮挡。
 */
public class GunPackSelectionScreen extends PackSelectionScreen {
    private final Screen returnParent;
    private final GunPackScreen.NavigationGate navigationGate;
    private Button curseForgeButton;

    public GunPackSelectionScreen(PackRepository repository, Consumer<PackRepository> output,
                                  Path packDir, Component title, Screen returnParent,
                                  GunPackScreen.NavigationGate navigationGate) {
        super(repository, output, packDir, title);
        this.returnParent = returnParent;
        this.navigationGate = navigationGate;
    }

    public GunPackScreen.NavigationGate getNavigationGate() {
        return this.navigationGate;
    }

    @Override
    protected void init() {
        super.init();
        this.curseForgeButton = Button
                .builder(Component.translatable("gui.taczfixes.curseforge.open"), pressed ->
                        Minecraft.getInstance().setScreen(new CurseForgeScreen(this, this.returnParent, this.navigationGate)))
                .bounds(8, this.height - 27, 150, 20)
                .build();
        this.addRenderableWidget(this.curseForgeButton);
        this.placeCurseForgeButton();
    }

    /** 1.21.1 窗口尺寸变化时只走 repositionElements, 这里同步更新按钮位置/宽度。 */
    @Override
    protected void repositionElements() {
        super.repositionElements();
        this.placeCurseForgeButton();
    }

    private void placeCurseForgeButton() {
        if (this.curseForgeButton == null) return;
        // 底部 footer 行的“打开文件夹/完成”居中(总宽约 308), 在它左侧放本模组按钮
        int footerStart = this.width / 2 - 154;
        int buttonWidth = Math.max(60, Math.min(150, footerStart - 12));
        this.curseForgeButton.setWidth(buttonWidth);
        this.curseForgeButton.setMessage(buttonWidth >= 110
                ? Component.translatable("gui.taczfixes.curseforge.open")
                : Component.translatable("gui.taczfixes.curseforge.open.short"));
        this.curseForgeButton.setX(8);
        this.curseForgeButton.setY(this.height - 27);
    }
}
