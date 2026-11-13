package com.ssscript.taczfixes.client.screen;

import com.ssscript.taczfixes.client.curseforge.CurseForgeApi;
import com.ssscript.taczfixes.client.curseforge.CurseForgeInstalledPacks;
import com.ssscript.taczfixes.client.curseforge.GunPackInstaller;
import com.ssscript.taczfixes.client.curseforge.GunPackUpdateChecker;
import com.ssscript.taczfixes.client.mixin.MixinAbstractSelectionListAccessor;
import com.ssscript.taczfixes.client.mixin.MixinPackSelectionScreenAccessor;
import com.ssscript.taczfixes.client.util.PendingResourceReload;
import com.ssscript.taczfixes.common.data.GunPackIds;
import com.ssscript.taczfixes.common.data.GunPackSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 枪包管理界面(复用原版 PackSelectionScreen)。底部保留“从 CurseForge 获取”按钮, 旁边是“更新”按钮:
 * 点击列表中某个枪包所在行即可选中(图标左侧的箭头/按钮区域除外), 选中且有更新时“更新”可用。
 * 有可用更新的枪包由 {@link com.ssscript.taczfixes.client.mixin.MixinPackEntryUpdateBadge} 在图标右上角画红点。
 */
public class GunPackSelectionScreen extends PackSelectionScreen implements GunPackUpdateBadgeSource {
    private static final long FAILED_MESSAGE_MS = 2500L;
    private static final int BUTTON_GAP = 4;

    private final Screen returnParent;
    private final GunPackScreen.NavigationGate navigationGate;
    private final Path taczDir;
    private final Map<String, CurseForgeInstalledPacks.Installed> installedByPackId;
    private Button curseForgeButton;
    private Button updateButton;
    private String selectedPackId;
    private boolean updating;
    private volatile double progress;
    private long failedAt;
    /** 上一次刷新按钮文案时的状态, 避免每帧 setMessage。 */
    private String lastUpdateLabel;

    public GunPackSelectionScreen(PackRepository repository, Consumer<PackRepository> output,
                                  Path packDir, Component title, Screen returnParent,
                                  GunPackScreen.NavigationGate navigationGate,
                                  Map<String, CurseForgeInstalledPacks.Installed> installedByPackId) {
        super(repository, output, packDir, title);
        this.returnParent = returnParent;
        this.navigationGate = navigationGate;
        this.taczDir = packDir;
        this.installedByPackId = installedByPackId == null ? Map.of() : Map.copyOf(installedByPackId);
    }

    public GunPackScreen.NavigationGate getNavigationGate() {
        return this.navigationGate;
    }

    public Screen getReturnParent() {
        return this.returnParent;
    }

    @Override
    protected void init() {
        super.init();
        this.curseForgeButton = this.addRenderableWidget(Button
                .builder(Component.translatable("gui.taczfixes.curseforge.open"), pressed ->
                        Minecraft.getInstance().setScreen(new CurseForgeScreen(this, this.returnParent, this.navigationGate)))
                .bounds(0, 0, 150, 20)
                .build());
        this.updateButton = this.addRenderableWidget(Button
                .builder(Component.translatable("gui.taczfixes.gun_packs.update"),
                        pressed -> this.onUpdateButton())
                .bounds(0, 0, 150, 20)
                .build());
        this.alignBottomButtons();
        // 打开界面时补查一次(启动检查尚未完成或距上次检查超过 30 分钟时才会真正发请求)
        CurseForgeApi.warmUpCache();
        GunPackUpdateChecker.checkAsync();
        this.lastUpdateLabel = null;
        this.refreshUpdateButton();
    }

    // -------------------------------------------------------------------- layout

    /**
     * 底部两个按钮与上方原版“打开包文件夹”“完成”组成 2×2 网格:
     * 下排分别与上排按钮的 x/宽度完全一致, y 为上排按钮底部再加 4px。
     * 原版按钮的坐标不写死, 全部在运行时从当前界面里按按钮文案查找。
     */
    private void alignBottomButtons() {
        if (this.curseForgeButton == null || this.updateButton == null) return;
        Button openFolder = this.findVanillaButton(Component.translatable("pack.openFolder").getString());
        Button done = this.findVanillaButton(Component.translatable("gui.done").getString());
        if (openFolder != null && done != null) {
            this.placeUnder(this.curseForgeButton, openFolder);
            this.placeUnder(this.updateButton, done);
            return;
        }
        // 找不到原版按钮时退化为居中布局, 避免两个按钮叠在一起。
        int buttonWidth = Math.min(150, Math.max(80, (this.width - 16 - BUTTON_GAP) / 2));
        int total = buttonWidth * 2 + BUTTON_GAP;
        int x = Math.max(2, this.width / 2 - total / 2);
        int y = this.height - 28;
        this.curseForgeButton.setWidth(buttonWidth);
        this.curseForgeButton.setHeight(20);
        this.curseForgeButton.setX(x);
        this.curseForgeButton.setY(y);
        this.updateButton.setWidth(buttonWidth);
        this.updateButton.setHeight(20);
        this.updateButton.setX(x + buttonWidth + BUTTON_GAP);
        this.updateButton.setY(y);
    }

    private void placeUnder(Button button, Button anchor) {
        button.setX(anchor.getX());
        button.setWidth(anchor.getWidth());
        button.setHeight(anchor.getHeight());
        button.setY(anchor.getY() + anchor.getHeight() + BUTTON_GAP);
    }

    private Button findVanillaButton(String message) {
        for (GuiEventListener listener : this.children()) {
            if (listener instanceof Button button
                    && button != this.curseForgeButton
                    && button != this.updateButton
                    && button.visible
                    && message.equals(button.getMessage().getString())) {
                return button;
            }
        }
        return null;
    }

    // --------------------------------------------------------------- update button

    private void onUpdateButton() {
        if (this.updating) return;
        GunPackUpdateChecker.Update update = this.selectedUpdate();
        if (update == null) return;
        // 先把当前界面的选择保存下来(更新后文件名可能变化, 迁移设置要基于已保存状态)
        this.commitSelection();
        this.updating = true;
        this.progress = 0.0D;
        this.failedAt = 0L;
        this.refreshUpdateButton();
        try {
            CurseForgeApi.downloadAndInstall(update.latest(), this.taczDir, value -> this.progress = value)
                    .whenComplete((result, error) -> Minecraft.getInstance()
                            .execute(() -> this.finishUpdate(update, result, error)));
        } catch (Throwable ignored) {
            this.updating = false;
            this.failedAt = System.currentTimeMillis();
            this.refreshUpdateButton();
        }
    }

    private void finishUpdate(GunPackUpdateChecker.Update update, GunPackInstaller.InstallResult result,
                              Throwable error) {
        this.updating = false;
        this.progress = 0.0D;
        if (error != null || result == null || result.file() == null) {
            this.failedAt = System.currentTimeMillis();
            this.refreshUpdateButton();
            return;
        }
        try {
            this.applyUpdate(update, result.file());
        } catch (Throwable ignored) {
            this.failedAt = System.currentTimeMillis();
            this.refreshUpdateButton();
            return;
        }
        this.lastUpdateLabel = null;
        this.refreshUpdateButton();
        PendingResourceReload.request();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == this) {
            try {
                minecraft.setScreen(GunPackScreen.create(this.returnParent));
            } catch (Throwable ignored) {
                // 更新已成功; 重建界面失败时下次打开管理页会重新扫描
            }
        }
    }

    private void applyUpdate(GunPackUpdateChecker.Update update, Path installedFile) {
        CurseForgeInstalledPacks.Installed installed = update.installed();
        Path oldFile = installed.fileName() == null || installed.fileName().isBlank()
                ? null : this.taczDir.resolve(installed.fileName());
        Path finalFile = GunPackInstaller.replaceInstalled(this.taczDir, oldFile, installedFile,
                update.latest().fileName());
        String namespace = GunPackInstaller.readGunPackNamespace(finalFile);
        if (namespace == null || namespace.isBlank()) {
            namespace = installed.namespace();
        }
        if (namespace != null && !namespace.isBlank()) {
            String oldNamespace = installed.namespace() == null || installed.namespace().isBlank()
                    ? namespace : installed.namespace();
            GunPackSettings.migrate(GunPackIds.of(oldNamespace, oldFile), GunPackIds.of(namespace, finalFile));
        }
        CurseForgeInstalledPacks.updateFile(installed.modId(), update.latest(), finalFile, namespace);
        GunPackUpdateChecker.forget(installed.modId());
    }

    private GunPackUpdateChecker.Update selectedUpdate() {
        if (this.selectedPackId == null) return null;
        return GunPackUpdateChecker.updateFor(this.installedByPackId.get(this.selectedPackId));
    }

    private void refreshUpdateButton() {
        if (this.updateButton == null) return;
        GunPackUpdateChecker.Update available = this.selectedUpdate();
        Component label;
        boolean active;
        if (this.updating) {
            double clamped = Math.max(0.0D, Math.min(1.0D, this.progress));
            label = Component.translatable("gui.taczfixes.gun_packs.update.progress",
                    (int) Math.round(clamped * 100.0D));
            active = false;
        } else if (this.failedAt > 0L && System.currentTimeMillis() - this.failedAt < FAILED_MESSAGE_MS) {
            label = Component.translatable("gui.taczfixes.gun_packs.update.failed");
            active = available != null;
        } else {
            label = Component.translatable("gui.taczfixes.gun_packs.update");
            active = available != null;
        }
        String text = label.getString();
        if (!text.equals(this.lastUpdateLabel)) {
            this.lastUpdateLabel = text;
            this.updateButton.setMessage(label);
        }
        this.updateButton.active = active;
    }

    /** 保存当前选择并停止目录监听, 但不触发原版界面的关闭跳转。 */
    private void commitSelection() {
        if (this.navigationGate != null) {
            this.navigationGate.suppress();
        }
        try {
            this.onClose();
        } catch (Exception ignored) {
        } finally {
            if (this.navigationGate != null) {
                this.navigationGate.resume();
            }
        }
    }

    // ------------------------------------------------------------------ selection

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean selected = false;
        if (button == 0 && !this.updating) {
            TransferableSelectionList list = this.listAt(mouseX, mouseY);
            if (list != null && mouseX - list.getRowLeft() > 32.0D && mouseX < list.getRight() - 6.0D) {
                TransferableSelectionList.PackEntry packEntry = this.entryAt(list, mouseY);
                if (packEntry != null) {
                    this.selectedPackId = packEntry.getPackId();
                    selected = true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button) || selected;
    }

    private TransferableSelectionList.PackEntry entryAt(TransferableSelectionList list, double mouseY) {
        MixinAbstractSelectionListAccessor accessor = (MixinAbstractSelectionListAccessor) list;
        int rowHeight = accessor.taczfixes$getItemHeight();
        java.util.List<TransferableSelectionList.PackEntry> entries = list.children();
        for (int i = 0; i < entries.size(); i++) {
            int top = accessor.taczfixes$getRowTop(i);
            if (mouseY >= top && mouseY < top + rowHeight) {
                return entries.get(i);
            }
        }
        return null;
    }

    private TransferableSelectionList listAt(double mouseX, double mouseY) {
        MixinPackSelectionScreenAccessor accessor = (MixinPackSelectionScreenAccessor) this;
        TransferableSelectionList available = accessor.taczfixes$getAvailablePackList();
        if (available != null && available.isMouseOver(mouseX, mouseY)) return available;
        TransferableSelectionList selected = accessor.taczfixes$getSelectedPackList();
        return selected != null && selected.isMouseOver(mouseX, mouseY) ? selected : null;
    }

    @Override
    public boolean taczfixes$hasUpdate(String packId) {
        return GunPackUpdateChecker.updateFor(this.installedByPackId.get(packId)) != null;
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.refreshUpdateButton();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
