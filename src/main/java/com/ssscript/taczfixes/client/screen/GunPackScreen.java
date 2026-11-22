package com.ssscript.taczfixes.client.screen;

import com.ssscript.taczfixes.client.curseforge.CurseForgeInstalledPacks;
import com.ssscript.taczfixes.client.util.PendingResourceReload;
import com.ssscript.taczfixes.common.data.GunPackSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        // 包来源每次 reload 都重新扫描 tacz 目录, 拖入管理界面的新枪包能立刻出现在列表里。
        GunPackSource source = new GunPackSource(taczDir, language);
        PackRepository repository = new PackRepository(source);
        repository.reload();
        // 把 CurseForge 安装记录对应到实际枪包 ID, 供红点/更新按钮使用。
        Map<String, CurseForgeInstalledPacks.Installed> installedByPackId =
                CurseForgeInstalledPacks.matchPacks(source.currentPacks());
        repository.setSelected(GunPackSettings.arrange(new ArrayList<>(repository.getAvailableIds())));
        Minecraft minecraft = Minecraft.getInstance();
        boolean inWorld = minecraft.level != null;
        NavigationGate gate = new NavigationGate();
        Consumer<PackRepository> output = repo -> {
            List<String> selected = new ArrayList<>(repo.getSelectedIds());
            List<String> disabled = new ArrayList<>();
            for (String id : repo.getAvailableIds()) {
                if (!selected.contains(id)) {
                    disabled.add(id);
                }
            }
            disabled.sort(String.CASE_INSENSITIVE_ORDER);
            // 只有“已选”栏(启用集合与优先级)变化才需要重载;“可用”栏变化只是新增/移除了不加载的枪包。
            boolean selectedChanged = !GunPackSettings.order().equals(selected);
            boolean disabledChanged = !GunPackSettings.disabledNames().equals(disabled);
            if (selectedChanged || disabledChanged) {
                GunPackSettings.update(selected, disabled);
            }
            if (gate.isSuppressed()) {
                // 由“从 CurseForge 获取”页面接管跳转, 资源重载由它自己的流程触发。
                if (selectedChanged) {
                    PendingResourceReload.request();
                }
                return;
            }
            if (inWorld) {
                // 世界内不能直接重载, 等界面真正关闭(下一 tick)再重载。
                if (selectedChanged) {
                    PendingResourceReload.request();
                }
                minecraft.setScreen(null);
                return;
            }
            // 主菜单没有“界面关闭”这个时机: 立即重载并清掉待处理标记,
            // 否则进入世界时(界面变 null)会再重载一次。
            minecraft.setScreen(parent != null ? parent : new TitleScreen());
            if (selectedChanged || PendingResourceReload.isPending()) {
                PendingResourceReload.reloadNow();
            }
        };
        return new GunPackSelectionScreen(repository, output, taczDir,
                Component.translatable("gui.taczfixes.gun_packs.title"), parent, gate, installedByPackId);
    }
}
