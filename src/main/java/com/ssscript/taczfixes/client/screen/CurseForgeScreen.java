package com.ssscript.taczfixes.client.screen;

import com.ssscript.taczfixes.client.curseforge.CurseForgeApi;
import com.ssscript.taczfixes.client.curseforge.CurseForgeConfig;
import com.ssscript.taczfixes.client.curseforge.CurseForgeImages;
import com.ssscript.taczfixes.client.curseforge.GunPackInstaller;
import com.ssscript.taczfixes.client.util.PendingResourceReload;
import com.ssscript.taczfixes.common.data.GunPackIds;
import com.ssscript.taczfixes.common.data.GunPackSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.CompletionException;

/**
 * “从 CurseForge 获取枪包”页面: 搜索 / 浏览 / 下载 / 自动安装 TaCZ 枪包。
 *
 * 搜索结果只显示真正的 TaCZ 枪包(依据 TaCZ 依赖、gunpack.meta.json 模块或关键词),
 * 每条结果带 CurseForge 封面缩略图; 支持切换排序方式(下载量/最近更新/名称)。
 * 分页按“过滤后的枪包数量”计算: 后台逐页扫描 CurseForge 原始结果并累积枪包,
 * 第一页结果立刻显示, 总数量在后台统计完成后更新。
 * 下载的 zip 会写入游戏目录的 tacz 文件夹; 旧版格式的枪包会先自动转换(等价于 /tacz convert)。
 */
public class CurseForgeScreen extends Screen {
    /** 行高 40, 让 32px 封面上下各留 4px, 不会压到选中框。 */
    private static final int ROW_HEIGHT = 40;
    /** 每页显示的枪包数量(过滤后), 与后端分页大小一致。 */
    private static final int PACKS_PER_PAGE = CurseForgeApi.PAGE_SIZE;
    /** 最多扫描多少页原始结果, 避免对超大结果集发出过多请求。 */
    private static final int MAX_RAW_PAGES = 25;
    /** 同时在途的原始页面请求数。 */
    private static final int MAX_IN_FLIGHT = 2;
    private static final int ICON_SIZE = 32;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private final Screen packManager;
    private final Screen returnParent;
    private final GunPackScreen.NavigationGate navigationGate;
    private final Path taczDir;

    private EditBox searchBox;
    private ResultList resultList;
    private Button searchButton;
    private Button prevButton;
    private Button nextButton;
    private Button sortButton;
    private Button downloadButton;
    private Button settingsButton;
    private Button backButton;

    /** 已扫描到的、过滤后的 TaCZ 枪包(保持 CurseForge 排序)。 */
    private final List<CurseForgeApi.ModInfo> filteredPacks = new ArrayList<>();
    /** 已返回但还没轮到追加的原始页(处理并发乱序)。 */
    private final NavigableMap<Integer, CurseForgeApi.SearchPage> rawPageBuffer = new TreeMap<>();
    /** 本次下载安装的枪包 id, 返回枪包管理界面时重新标记为“未启用/可用”。 */
    private final java.util.Set<String> pendingAvailableIds = new java.util.LinkedHashSet<>();

    private String query = "";
    private CurseForgeApi.SortMode sortMode;
    private int pageIndex;
    private int loadedRawPages;
    private int nextRawPage;
    private int rawTotalCount = -1;
    private int pagesToScan = -1;
    private int scanInFlight;
    /** 已成功取到的原始页数(用于“仅扫描前 N 个结果”展示, 含中间失败时缓冲区里的页)。 */
    private int successfulRawPages;
    private boolean allRawScanned;
    private boolean scanTruncated;
    private boolean firstPageLoading;
    private boolean searched;
    private boolean downloading;
    private boolean leaveDone;
    private boolean openingChild;
    private boolean managerClosed;
    private volatile double downloadProgress = -1.0D;
    private int requestSeq;
    private int fileSeq;
    private CurseForgeApi.ModInfo selectedMod;
    private CurseForgeApi.FileInfo selectedFile;
    private Component status = Component.translatable("gui.taczfixes.curseforge.status.idle");
    private int statusColor = 0xFFA0A0A0;

    public CurseForgeScreen(Screen packManager, Screen returnParent,
                            GunPackScreen.NavigationGate navigationGate) {
        super(Component.translatable("gui.taczfixes.curseforge.title"));
        this.packManager = packManager;
        this.returnParent = returnParent;
        this.navigationGate = navigationGate;
        this.taczDir = FMLPaths.GAMEDIR.get().resolve("tacz");
        this.sortMode = CurseForgeApi.SortMode.byName(CurseForgeConfig.sortMode());
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        // 重开界面时允许之前失败的封面重新加载
        CurseForgeImages.clearFailed();
        // 搜索框居中, 搜索按钮固定在它右侧(按钮尺寸与其它底部按钮一致: 56x20)
        int boxWidth = Math.min(224, this.width - 56 - 4 - 16);
        boxWidth = Math.max(80, boxWidth);
        int boxX = Math.min(centerX - boxWidth / 2, this.width - 16 - 56 - 4 - boxWidth);
        boxX = Math.max(8, boxX);
        this.searchBox = new EditBox(this.font, boxX, 28, boxWidth, 20, Component.empty());
        this.searchBox.setMaxLength(80);
        this.searchBox.setValue(this.query);
        this.searchBox.setResponder(value -> this.query = value);
        this.addRenderableWidget(this.searchBox);

        this.searchButton = this.addRenderableWidget(Button
                .builder(Component.translatable("gui.taczfixes.curseforge.search"), button -> this.restartSearch())
                .bounds(boxX + boxWidth + 4, 28, 56, 20)
                .build());

        this.resultList = new ResultList(this.minecraft, this.width - 40, this.height, 56, this.height - 96, ROW_HEIGHT);
        this.resultList.setLeftPos(20);
        this.addRenderableWidget(this.resultList);

        this.initBottomButtons();

        try {
            this.setInitialFocus(this.searchBox);
        } catch (Throwable ignored) {
        }

        this.refreshUi();
        if (!this.searched) {
            this.restartSearch();
        }
    }

    /**
     * 底部布局:
     * 靠上那一行: 只有“下载”, 居中并加宽(与下行等宽);
     * 靠下那一行: 上一页 / 下一页 / 排序 / 返回, 其中“排序”占“设置”原来的位置且为普通按钮的 1.5 倍宽;
     * “设置”单独放在右下角。
     */
    private void initBottomButtons() {
        int gap = 4;
        int buttonHeight = 20;
        int buttonWidth = 56;
        int sortWidth = buttonWidth + buttonWidth / 2;
        int lowerTotal = buttonWidth * 3 + sortWidth + gap * 3;
        // 窄屏时缩小按钮, 给右下角的“设置”留出位置
        for (int candidate : new int[]{56, 48, 40, 36}) {
            buttonWidth = candidate;
            sortWidth = buttonWidth + buttonWidth / 2;
            lowerTotal = buttonWidth * 3 + sortWidth + gap * 3;
            if (lowerTotal <= this.width - 136) {
                break;
            }
        }
        int upperY = this.height - 58;
        int lowerY = this.height - 30;
        int lowerX = Math.max(2, this.width / 2 - lowerTotal / 2);

        // 上排: 只有“下载”一个按钮, 居中, 宽度 198
        int downloadWidth = Math.min(198, this.width - 16);
        int downloadX = Math.max(2, this.width / 2 - downloadWidth / 2);
        this.downloadButton = this.addRenderableWidget(Button
                .builder(Component.translatable("gui.taczfixes.curseforge.download"), button -> this.download())
                .bounds(downloadX, upperY, downloadWidth, buttonHeight)
                .build());

        // 下排: 上一页 / 下一页 / 排序(1.5 倍宽) / 返回
        int x = lowerX;
        this.prevButton = this.addRenderableWidget(Button
                .builder(Component.translatable("gui.taczfixes.curseforge.prev"), button -> this.changePage(-1))
                .bounds(x, lowerY, buttonWidth, buttonHeight)
                .build());
        x += buttonWidth + gap;
        this.nextButton = this.addRenderableWidget(Button
                .builder(Component.translatable("gui.taczfixes.curseforge.next"), button -> this.changePage(1))
                .bounds(x, lowerY, buttonWidth, buttonHeight)
                .build());
        x += buttonWidth + gap;
        this.sortButton = this.addRenderableWidget(Button
                .builder(this.sortLabel(), button -> this.cycleSort())
                .bounds(x, lowerY, sortWidth, buttonHeight)
                .build());
        x += sortWidth + gap;
        this.backButton = this.addRenderableWidget(Button
                .builder(Component.translatable("gui.taczfixes.curseforge.back"), button -> this.leave())
                .bounds(x, lowerY, buttonWidth, buttonHeight)
                .build());

        // 设置: 单独放在右下角
        int settingsX = Math.max(2, this.width - 8 - buttonWidth);
        this.settingsButton = this.addRenderableWidget(Button
                .builder(Component.translatable("gui.taczfixes.curseforge.settings"), button -> this.openSettings())
                .bounds(settingsX, lowerY, buttonWidth, buttonHeight)
                .build());
    }

    private Component sortLabel() {
        return Component.translatable("gui.taczfixes.curseforge.sort",
                Component.translatable(this.sortMode.labelKey()));
    }

    private void cycleSort() {
        if (this.downloading) return;
        this.sortMode = this.sortMode.next();
        CurseForgeConfig.setSortMode(this.sortMode.name());
        if (this.sortButton != null) {
            this.sortButton.setMessage(this.sortLabel());
        }
        this.restartSearch();
    }

    // ------------------------------------------------------------------ 搜索分页

    /** 重新搜索: 清空已扫描结果, 立即请求第 0 页, 之后在后台继续扫描以统计过滤后的总数。 */
    private void restartSearch() {
        if (this.downloading) return;
        this.searched = true;
        ++this.requestSeq;
        this.filteredPacks.clear();
        this.rawPageBuffer.clear();
        this.loadedRawPages = 0;
        this.nextRawPage = 0;
        this.rawTotalCount = -1;
        this.pagesToScan = -1;
        this.scanInFlight = 0;
        this.successfulRawPages = 0;
        this.allRawScanned = false;
        this.scanTruncated = false;
        this.pageIndex = 0;
        this.firstPageLoading = true;
        this.selectedMod = null;
        this.selectedFile = null;
        ++this.fileSeq;
        this.status = Component.translatable("gui.taczfixes.curseforge.status.loading");
        this.statusColor = 0xFFA0A0A0;
        this.updateButtons();
        this.scanMore();
    }

    private void scanMore() {
        if (this.allRawScanned || this.leaveDone) return;
        int limit = this.pagesToScan > 0 ? this.pagesToScan : 1;
        while (this.scanInFlight < MAX_IN_FLIGHT && this.nextRawPage < limit) {
            final int page = this.nextRawPage++;
            final int token = this.requestSeq;
            ++this.scanInFlight;
            CurseForgeApi.search(this.query, page, this.sortMode)
                    .whenComplete((result, error) -> runOnClient(() -> this.onRawPage(token, page, result, error)));
        }
    }

    private void onRawPage(int token, int page, CurseForgeApi.SearchPage result, Throwable error) {
        if (token != this.requestSeq || this.leaveDone) return;
        this.scanInFlight = Math.max(0, this.scanInFlight - 1);
        // 扫描已经结束(例如某页失败后停止)时, 忽略迟到的响应
        if (this.allRawScanned) return;

        if (error != null) {
            if (page == 0) {
                this.firstPageLoading = false;
                this.allRawScanned = true;
                this.status = describeError(error);
                this.statusColor = 0xFFFF5555;
                this.updateButtons();
                return;
            }
            // 后续页失败: 把缓冲区里已经拿到的页面按页号顺序追加(中间缺页也保留其它已取到的结果), 再停止扫描
            while (!this.rawPageBuffer.isEmpty()) {
                java.util.Map.Entry<Integer, CurseForgeApi.SearchPage> entry = this.rawPageBuffer.pollFirstEntry();
                for (CurseForgeApi.ModInfo mod : entry.getValue().mods()) {
                    if (mod.taczGunPack()) {
                        this.filteredPacks.add(mod);
                    }
                }
            }
            this.loadedRawPages = this.successfulRawPages;
            this.allRawScanned = true;
            this.scanTruncated = true;
            this.refreshUi();
            return;
        }

        if (this.rawTotalCount < 0) {
            this.rawTotalCount = Math.max(0, result.totalCount());
            int totalPages = Math.max(1, (int) Math.ceil(this.rawTotalCount / (double) CurseForgeApi.PAGE_SIZE));
            this.pagesToScan = Math.min(totalPages, MAX_RAW_PAGES);
            this.scanTruncated = totalPages > MAX_RAW_PAGES;
        }

        ++this.successfulRawPages;
        this.rawPageBuffer.put(page, result);
        this.appendBufferedPages();
        if (page == 0) {
            this.firstPageLoading = false;
        }
        if (this.pagesToScan > 0 && this.loadedRawPages >= this.pagesToScan && this.rawPageBuffer.isEmpty()) {
            this.allRawScanned = true;
        }
        this.refreshUi();
        if (!this.allRawScanned) {
            this.scanMore();
        }
    }

    /** 按页号顺序把缓冲区里连续的页面追加到过滤结果(处理并发乱序与中途失败)。 */
    private void appendBufferedPages() {
        while (this.rawPageBuffer.containsKey(this.loadedRawPages)) {
            CurseForgeApi.SearchPage scanned = this.rawPageBuffer.remove(this.loadedRawPages);
            for (CurseForgeApi.ModInfo mod : scanned.mods()) {
                if (mod.taczGunPack()) {
                    this.filteredPacks.add(mod);
                }
            }
            ++this.loadedRawPages;
        }
    }

    private void changePage(int delta) {
        if (this.downloading) return;
        int maxPage = this.filteredPacks.isEmpty() ? 0 : (this.filteredPacks.size() - 1) / PACKS_PER_PAGE;
        int target = Math.max(0, Math.min(maxPage, this.pageIndex + delta));
        if (target == this.pageIndex) return;
        this.pageIndex = target;
        this.selectedMod = null;
        this.selectedFile = null;
        ++this.fileSeq;
        this.refreshUi();
        if (this.resultList != null) {
            this.resultList.setScrollAmount(0.0D);
        }
    }

    private void refreshUi() {
        if (this.firstPageLoading) {
            this.status = Component.translatable("gui.taczfixes.curseforge.status.loading");
            this.statusColor = 0xFFA0A0A0;
        } else if (this.filteredPacks.isEmpty()) {
            this.status = this.allRawScanned
                    ? Component.translatable("gui.taczfixes.curseforge.status.empty")
                    : Component.translatable("gui.taczfixes.curseforge.status.page.scanning",
                    this.pageIndex + 1, 0);
            this.statusColor = 0xFFA0A0A0;
        } else if (this.allRawScanned) {
            int rawScanned = this.successfulRawPages * CurseForgeApi.PAGE_SIZE;
            if (this.rawTotalCount >= 0) {
                rawScanned = Math.min(rawScanned, this.rawTotalCount);
            }
            this.status = this.scanTruncated
                    ? Component.translatable("gui.taczfixes.curseforge.status.page.truncated",
                    this.pageIndex + 1, this.filteredPacks.size(), rawScanned)
                    : Component.translatable("gui.taczfixes.curseforge.status.page.filtered",
                    this.pageIndex + 1, this.filteredPacks.size());
            this.statusColor = 0xFFA0A0A0;
        } else {
            this.status = Component.translatable("gui.taczfixes.curseforge.status.page.scanning",
                    this.pageIndex + 1, this.filteredPacks.size());
            this.statusColor = 0xFFA0A0A0;
        }
        this.rebuildRows();
        this.updateButtons();
    }

    private List<CurseForgeApi.ModInfo> currentPagePacks() {
        int start = this.pageIndex * PACKS_PER_PAGE;
        if (start >= this.filteredPacks.size()) return List.of();
        int end = Math.min(start + PACKS_PER_PAGE, this.filteredPacks.size());
        return this.filteredPacks.subList(start, end);
    }

    private void rebuildRows() {
        if (this.resultList == null) return;
        this.resultList.clearRows();
        CurseForgeApi.ModInfo selected = this.selectedMod;
        for (CurseForgeApi.ModInfo mod : this.currentPagePacks()) {
            ModRow row = new ModRow(mod);
            this.resultList.addRow(row);
            if (selected != null && selected.id() == mod.id()) {
                this.resultList.setSelected(row);
            }
        }
    }

    private void updateButtons() {
        if (this.prevButton != null) {
            this.prevButton.active = !this.downloading && this.pageIndex > 0;
        }
        if (this.nextButton != null) {
            this.nextButton.active = !this.downloading
                    && (this.pageIndex + 1) * PACKS_PER_PAGE < this.filteredPacks.size();
        }
        if (this.downloadButton != null) {
            this.downloadButton.active = !this.downloading && this.selectedFile != null;
        }
        if (this.searchButton != null) {
            this.searchButton.active = !this.downloading;
        }
        if (this.sortButton != null) {
            this.sortButton.active = !this.downloading;
        }
        if (this.settingsButton != null) {
            this.settingsButton.active = !this.downloading;
        }
        if (this.backButton != null) {
            this.backButton.active = !this.downloading;
        }
    }

    // ------------------------------------------------------------------ 文件与下载

    private void onRowSelected(CurseForgeApi.ModInfo mod) {
        if (this.downloading || mod == null) return;
        this.selectedMod = mod;
        this.selectedFile = null;
        final int seq = ++this.fileSeq;
        this.status = Component.translatable("gui.taczfixes.curseforge.status.resolving",
                Component.literal(mod.name()));
        this.statusColor = 0xFFA0A0A0;
        this.updateButtons();
        CurseForgeApi.listFiles(mod.id()).whenComplete((files, error) -> runOnClient(() -> {
            if (seq != this.fileSeq) return;
            if (error != null) {
                this.status = describeError(error);
                this.statusColor = 0xFFFF5555;
                this.updateButtons();
                return;
            }
            CurseForgeApi.FileInfo file = CurseForgeApi.pickLatestGunPackFile(files);
            this.selectedFile = file;
            if (file == null) {
                this.status = Component.translatable("gui.taczfixes.curseforge.status.no_file");
                this.statusColor = 0xFFFFAA00;
            } else {
                Component fileName = Component.literal(file.fileName());
                Component size = Component.literal(GunPackInstaller.formatSize(file.length()));
                if (mod.allowModDistribution()) {
                    this.status = Component.translatable("gui.taczfixes.curseforge.status.ready", fileName, size);
                } else {
                    this.status = Component.translatable("gui.taczfixes.curseforge.status.ready.no_distribution",
                            fileName, size);
                }
                this.statusColor = 0xFFA0A0A0;
            }
            this.updateButtons();
        }));
    }

    private void download() {
        if (this.downloading || this.selectedFile == null) return;
        final CurseForgeApi.FileInfo file = this.selectedFile;
        this.downloading = true;
        this.downloadProgress = 0.0D;
        this.statusColor = 0xFFA0A0A0;
        this.updateButtons();
        CurseForgeApi.downloadAndInstall(file, this.taczDir, progress -> this.downloadProgress = progress)
                .whenComplete((result, error) -> runOnClient(() -> {
                    this.downloading = false;
                    this.downloadProgress = -1.0D;
                    if (error != null) {
                        this.status = describeError(error);
                        this.statusColor = 0xFFFF5555;
                    } else {
                        Component name = Component.literal(result.file().getFileName().toString());
                        if (result.converted()) {
                            this.status = Component.translatable(
                                    "gui.taczfixes.curseforge.status.success.converted", name);
                        } else if (result.metaAdded()) {
                            this.status = Component.translatable(
                                    "gui.taczfixes.curseforge.status.success.meta_added", name);
                        } else {
                            this.status = Component.translatable("gui.taczfixes.curseforge.status.success", name);
                        }
                        this.statusColor = 0xFF55FF55;
                        PendingResourceReload.request();
                        // 新安装的枪包默认放在“可用”列表, 由玩家自己决定何时启用
                        String namespace = GunPackInstaller.readGunPackNamespace(result.file());
                        if (namespace != null && !namespace.isBlank()) {
                            String packId = GunPackIds.of(namespace, result.file());
                            this.pendingAvailableIds.add(packId);
                            GunPackSettings.addDisabled(packId);
                        }
                    }
                    this.updateButtons();
                }));
    }

    // ------------------------------------------------------------------ 界面导航

    private void openSettings() {
        if (this.downloading) return;
        this.openingChild = true;
        Minecraft.getInstance().setScreen(new CurseForgeSettingsScreen(this));
    }

    /** 让枪包管理界面 commit 保存并关闭目录监听, 但不触发它自己的界面跳转。可重复调用。 */
    private void closePackManager() {
        if (this.managerClosed) return;
        this.managerClosed = true;
        if (this.navigationGate != null) {
            this.navigationGate.suppress();
        }
        if (this.packManager == null) return;
        if (this.packManager instanceof GunPackSelectionScreen manager && manager.getNavigationGate() != null) {
            manager.getNavigationGate().suppress();
        }
        try {
            this.packManager.onClose();
        } catch (Exception ignored) {
        }
    }

    /** 返回枪包管理界面: 先保存原界面上的选择, 再重建以显示新安装的枪包。 */
    private void leave() {
        if (this.leaveDone) return;
        this.leaveDone = true;
        this.closePackManager();
        this.applyPendingAvailable();
        Minecraft.getInstance().setScreen(GunPackScreen.create(this.returnParent));
    }

    /**
     * 原枪包管理界面的 commit 会用“它自己知道的可用包”重算禁用列表,
     * 从而把刚下载的枪包 id 从禁用列表里冲掉; 因此在重建界面前再补写一次。
     */
    private void applyPendingAvailable() {
        for (String packId : this.pendingAvailableIds) {
            GunPackSettings.addDisabled(packId);
        }
    }

    @Override
    public void added() {
        super.added();
        // 从子界面(设置)返回时会重新 added: 清掉“正在打开子界面”标记。
        this.openingChild = false;
    }

    @Override
    public void onClose() {
        this.leave();
    }

    /** 界面被其它原因替换(断线/传送/其它模组跳转)时, 也要保存选择并关闭目录监听。 */
    @Override
    public void removed() {
        if (this.openingChild) {
            // 只是打开了子界面(设置), 本页面稍后会回来, 不能当成关闭。
            this.openingChild = false;
            return;
        }
        if (this.leaveDone) return;
        this.leaveDone = true;
        this.closePackManager();
        this.applyPendingAvailable();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && this.searchBox != null && this.searchBox.isFocused()) {
            this.restartSearch();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderDirtBackground(guiGraphics);
        int centerX = this.width / 2;
        int maxWidth = this.width - 24;
        guiGraphics.drawCenteredString(this.font, this.title, centerX, 10, 0xFFFFFF);

        if (this.downloading) {
            int barWidth = Math.min(300, this.width - 60);
            int barX = centerX - barWidth / 2;
            int barY = this.height - 90;
            guiGraphics.fill(barX, barY, barX + barWidth, barY + 6, 0xFF000000);
            double progress = Math.max(0.0D, Math.min(1.0D, this.downloadProgress));
            guiGraphics.fill(barX, barY, barX + (int) (barWidth * progress), barY + 6, 0xFF55FF55);
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("gui.taczfixes.curseforge.status.downloading",
                            (int) (progress * 100.0D)),
                    centerX, this.height - 78, 0xFFFFFFFF);
        } else if (this.status != null) {
            String text = trim(this.status.getString(), maxWidth);
            if (!text.isEmpty()) {
                guiGraphics.drawCenteredString(this.font, Component.literal(text),
                        centerX, this.height - 78, this.statusColor);
            }
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private Component describeError(Throwable error) {
        String message = describe(error);
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("401") || lower.contains("403") || lower.contains("api key")) {
            return Component.translatable("gui.taczfixes.curseforge.status.error.key", message);
        }
        if (lower.contains("429") || lower.contains("rate limited")) {
            return Component.translatable("gui.taczfixes.curseforge.status.error.rate", message);
        }
        if (lower.contains("timeout") || lower.contains("timed out") || lower.contains("connect")
                || lower.contains("network") || lower.contains("unknownhost")) {
            return Component.translatable("gui.taczfixes.curseforge.status.error.network", message);
        }
        if (lower.contains("not a valid") || lower.contains("not a gun pack") || lower.contains("gunpack.meta")) {
            return Component.translatable("gui.taczfixes.curseforge.status.error.invalid_pack", message);
        }
        return Component.translatable("gui.taczfixes.curseforge.status.error", message);
    }

    private static void runOnClient(Runnable task) {
        Minecraft.getInstance().execute(task);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String describe(Throwable error) {
        Throwable cause = unwrap(error);
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            message = cause.getClass().getSimpleName();
        }
        return message;
    }

    private String trim(String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) return "";
        if (this.font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int keep = maxWidth - this.font.width(ellipsis);
        if (keep <= 0) return ellipsis;
        return this.font.plainSubstrByWidth(text, keep) + ellipsis;
    }

    private static String formatDownloads(long downloads) {
        if (downloads >= 1_000_000_000L) return String.format(Locale.ROOT, "%.1fB", downloads / 1_000_000_000.0D);
        if (downloads >= 1_000_000L) return String.format(Locale.ROOT, "%.1fM", downloads / 1_000_000.0D);
        if (downloads >= 1_000L) return String.format(Locale.ROOT, "%.1fk", downloads / 1_000.0D);
        return Long.toString(downloads);
    }

    private static final class ResultList extends ObjectSelectionList<ModRow> {
        ResultList(Minecraft minecraft, int width, int height, int y0, int y1, int itemHeight) {
            super(minecraft, width, height, y0, y1, itemHeight);
            this.centerListVertically = false;
            this.setRenderBackground(true);
            this.setRenderTopAndBottom(false);
        }

        @Override
        public int getRowWidth() {
            return this.width - 24;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.x1 - 8;
        }

        void clearRows() {
            super.clearEntries();
        }

        void addRow(ModRow row) {
            super.addEntry(row);
        }
    }

    private final class ModRow extends ObjectSelectionList.Entry<ModRow> {
        private final CurseForgeApi.ModInfo mod;

        ModRow(CurseForgeApi.ModInfo mod) {
            this.mod = mod;
        }

        @Override
        public Component getNarration() {
            return Component.literal(this.mod.name());
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            int iconX = left + 2;
            int iconY = top + 4;
            CurseForgeImages.Icon icon = CurseForgeImages.request(this.mod.logoUrl());
            if (icon != null) {
                // 缩略图实际尺寸(通常 64x64)缩放到 32x32 画出完整封面
                guiGraphics.blit(icon.location(), iconX, iconY, ICON_SIZE, ICON_SIZE,
                        0.0F, 0.0F, icon.width(), icon.height(), icon.width(), icon.height());
            } else {
                // 占位: 使用 TaCZ 默认枪包图标
                guiGraphics.blit(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                "taczfixes", "textures/gui/gun_pack_placeholder.png"),
                        iconX, iconY, ICON_SIZE, ICON_SIZE, 0.0F, 0.0F, 64, 64, 64, 64);
            }

            int textLeft = left + ICON_SIZE + 8;
            int maxTextWidth = width - ICON_SIZE - 14;
            guiGraphics.drawString(CurseForgeScreen.this.font,
                    Component.literal(trim(this.mod.name(), maxTextWidth)), textLeft, top + 4, 0xFFFFFF, false);
            String summary = this.mod.summary() == null ? "" : this.mod.summary();
            guiGraphics.drawString(CurseForgeScreen.this.font,
                    Component.literal(trim(summary, maxTextWidth)), textLeft, top + 15, 0xFFA0A0A0, false);
            StringBuilder meta = new StringBuilder();
            if (this.mod.author() != null && !this.mod.author().isBlank()) {
                meta.append(this.mod.author()).append("  ·  ");
            }
            meta.append(formatDownloads(this.mod.downloads()));
            if (this.mod.modified() > 0L) {
                meta.append("  ·  ").append(DATE_FORMAT.format(Instant.ofEpochMilli(this.mod.modified())));
            }
            guiGraphics.drawString(CurseForgeScreen.this.font,
                    Component.literal(trim(meta.toString(), maxTextWidth)), textLeft, top + 26, 0xFF808080, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) return false;
            if (CurseForgeScreen.this.resultList != null) {
                CurseForgeScreen.this.resultList.setSelected(this);
            }
            CurseForgeScreen.this.onRowSelected(this.mod);
            return true;
        }
    }
}
