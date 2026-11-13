package com.ssscript.taczfixes.client.screen;

import com.ssscript.taczfixes.client.curseforge.CurseForgeInstalledPacks;
import com.ssscript.taczfixes.client.util.PendingResourceReload;
import com.ssscript.taczfixes.common.data.GunPackDependencies;
import com.ssscript.taczfixes.common.data.GunPackInfoScanner;
import com.ssscript.taczfixes.common.data.GunPackResources;
import com.ssscript.taczfixes.common.data.GunPackSettings;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 枪包管理: 直接复用原版资源包界面(PackSelectionScreen), 只把枪包作为包列表传入。 */
public final class GunPackScreen {
    private GunPackScreen() {
    }

    /**
     * 用于“从 CurseForge 获取”页面返回时复用枪包管理界面:
     * 先把选择结果 commit 保存并关闭目录监听, 但不触发原来的界面跳转(由调用方决定跳到哪)。
     */
    public static final class NavigationGate {
        private boolean suppressed = false;

        public void suppress() {
            this.suppressed = true;
        }

        /** 恢复跳转(提交选择时临时抑制, 之后恢复, 保证界面的“完成”按钮仍然有效)。 */
        public void resume() {
            this.suppressed = false;
        }

        public boolean isSuppressed() {
            return this.suppressed;
        }
    }

    public static Screen create() {
        return create(null);
    }

    /** parent 用于从主菜单/暂停界面进入时返回; 已有世界时沿用原有行为(关闭界面回到游戏)。 */
    public static Screen create(Screen parent) {
        Path taczDir = FMLPaths.GAMEDIR.get().resolve("tacz");
        int format = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
        // 每次 reload 都重新扫描, 这样拖入界面的新枪包(原版会复制进 tacz 目录)也能出现。
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        // 每次 reload 都重新扫描, 否则拖入新枪包后 PackRepository 仍使用旧的扫描结果。
        List<GunPackInfoScanner.PackInfoData> packs = GunPackInfoScanner.scan(taczDir, language);
        // 把 CurseForge 安装记录对应到实际枪包 ID, 供红点/更新按钮使用。
        Map<String, CurseForgeInstalledPacks.Installed> installedByPackId =
                CurseForgeInstalledPacks.matchPacks(packs);
        PackRepository repository = new PackRepository(consumer -> {
            for (GunPackInfoScanner.PackInfoData info : packs) {
                consumer.accept(createPack(info, format));
            }
        });
        repository.reload();
        repository.setSelected(GunPackSettings.arrange(new ArrayList<>(repository.getAvailableIds())));
        Minecraft minecraft = Minecraft.getInstance();
        boolean inWorld = minecraft.level != null;
        NavigationGate gate = new NavigationGate();
        return new GunPackSelectionScreen(repository, repo -> {
            List<String> selected = new ArrayList<>(repo.getSelectedIds());
            List<String> disabled = new ArrayList<>();
            for (String id : repo.getAvailableIds()) {
                if (!selected.contains(id)) {
                    disabled.add(id);
                }
            }
            disabled.sort(String.CASE_INSENSITIVE_ORDER);
            boolean changed = !GunPackSettings.order().equals(selected)
                    || !GunPackSettings.disabledNames().equals(disabled);
            if (changed) {
                GunPackSettings.update(selected, disabled);
                // 无论是否由“从 CurseForge 获取”页面接管跳转, 都需要在界面全部关闭后重载资源。
                PendingResourceReload.request();
            }
            if (gate.isSuppressed()) {
                return;
            }
            if (inWorld) {
                minecraft.setScreen(null);
            } else {
                // 主菜单没有世界: 立即重载客户端资源(数据包会在下次进入世界时由枪包设置生效)
                minecraft.setScreen(parent != null ? parent : new TitleScreen());
                if (changed) {
                    minecraft.reloadResourcePacks();
                }
            }
        }, taczDir, Component.translatable("gui.taczfixes.gun_packs.title"), parent, gate, installedByPackId);
    }

    private static Pack createPack(GunPackInfoScanner.PackInfoData info, int format) {
        // 依赖有问题的枪包按原版“不兼容资源包”显示(标红 + 兼容性提示), 但仍然可以被选中/加载。
        // 用 format-1 让原版 PackCompatibility 判定为 TOO_OLD(红色“不兼容”), 与 1.21.1 版本的 TOO_OLD 一致。
        int packFormat = info.incompatible() ? format - 1 : format;
        Pack.Info packInfo = new Pack.Info(info.description(), packFormat, FeatureFlags.DEFAULT_FLAGS);
        return Pack.create(info.id(), title(info), false,
                id -> new GunPackResources(info.id(), info.path(), info.iconPath(), info.logo()),
                packInfo, PackType.CLIENT_RESOURCES, Pack.Position.TOP, false, PackSource.DEFAULT);
    }

    /** 不兼容枪包的标题: 红色标记 + 第一个依赖问题原因。 */
    private static Component title(GunPackInfoScanner.PackInfoData info) {
        if (!info.incompatible()) {
            return info.displayName();
        }
        return Component.empty()
                .append(Component.translatable("gui.taczfixes.gun_packs.incompatible"))
                .append(" ")
                .append(info.displayName())
                .append(" ")
                .append(issueText(info.issues()));
    }

    private static Component issueText(List<GunPackDependencies.Issue> issues) {
        GunPackDependencies.Issue first = issues.get(0);
        Component reason = first.type() == GunPackDependencies.IssueType.MISSING
                ? Component.translatable("gui.taczfixes.gun_packs.issue.missing", first.modId())
                : Component.translatable("gui.taczfixes.gun_packs.issue.version",
                        first.modId(), first.range() == null ? "" : first.range());
        if (issues.size() > 1) {
            reason = Component.translatable("gui.taczfixes.gun_packs.issue.more", reason, issues.size() - 1);
        }
        return Component.literal("\u00a77(").append(reason).append(")");
    }
}
