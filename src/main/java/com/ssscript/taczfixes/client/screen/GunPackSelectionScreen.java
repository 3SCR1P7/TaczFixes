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
 * 枪包管理界面(复用原版 PackSelectionScreen), 在底部空白行额外加一个
 * “从 CurseForge 获取”按钮, 用于打开 {@link CurseForgeScreen}。
 *
 * 原版界面: 两个包列表在 y=32..height-51, 打开文件夹/完成按钮在 y=height-48,
 * 因此 y=height-28 整行是空的, 可以安全放置额外按钮而不遮挡列表。
 */
public class GunPackSelectionScreen extends PackSelectionScreen {
    private final Screen returnParent;
    private final GunPackScreen.NavigationGate navigationGate;

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
        Button button = Button
                .builder(Component.translatable("gui.taczfixes.curseforge.open"), pressed ->
                        Minecraft.getInstance().setScreen(new CurseForgeScreen(this, this.returnParent, this.navigationGate)))
                .bounds(this.width / 2 - 154, this.height - 28, 150, 20)
                .build();
        this.addRenderableWidget(button);
    }
}
