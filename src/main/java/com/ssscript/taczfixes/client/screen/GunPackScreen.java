package com.ssscript.taczfixes.client.screen;

import com.ssscript.taczfixes.client.util.PendingResourceReload;
import com.ssscript.taczfixes.common.data.GunPackInfoScanner;
import com.ssscript.taczfixes.common.data.GunPackResources;
import com.ssscript.taczfixes.common.data.GunPackSettings;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** 枪包管理: 直接复用原版资源包界面(PackSelectionScreen), 只把枪包作为包列表传入。 */
public final class GunPackScreen {
    private GunPackScreen() {
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
        Map<String, String> idToName = new LinkedHashMap<>();
        // 每次 reload 都重新扫描, 否则拖入新枪包后 PackRepository 仍使用旧的扫描结果。
        PackRepository repository = new PackRepository(consumer -> {
            List<GunPackInfoScanner.PackInfoData> infos = GunPackInfoScanner.scan(taczDir, language);
            idToName.clear();
            for (GunPackInfoScanner.PackInfoData info : infos) {
                idToName.put(info.id(), info.name());
                consumer.accept(createPack(info, format));
            }
        });
        repository.reload();
        List<String> arrangedNames = GunPackSettings.arrange(
                new ArrayList<>(new LinkedHashSet<>(idToName.values())));
        List<String> selected = new ArrayList<>();
        for (String id : repository.getAvailableIds()) {
            String name = idToName.get(id);
            if (name != null && arrangedNames.contains(name)) {
                selected.add(id);
            }
        }
        repository.setSelected(selected);
        Minecraft minecraft = Minecraft.getInstance();
        boolean inWorld = minecraft.level != null;
        return new PackSelectionScreen(repository, repo -> {
            List<String> selectedIds = new ArrayList<>(repo.getSelectedIds());
            List<String> selectedNames = new ArrayList<>();
            for (String id : selectedIds) {
                String name = idToName.get(id);
                if (name != null && !selectedNames.contains(name)) {
                    selectedNames.add(name);
                }
            }
            List<String> disabledNames = new ArrayList<>();
            for (String id : repo.getAvailableIds()) {
                if (selectedIds.contains(id)) {
                    continue;
                }
                String name = idToName.get(id);
                if (name != null && !selectedNames.contains(name) && !disabledNames.contains(name)) {
                    disabledNames.add(name);
                }
            }
            disabledNames.sort(String.CASE_INSENSITIVE_ORDER);
            // 设置文件仍按命名空间记录, 同命名空间的多个枪包会一起启用/禁用。
            boolean changed = !GunPackSettings.order().equals(selectedNames)
                    || !GunPackSettings.disabledNames().equals(disabledNames);
            if (changed) {
                GunPackSettings.update(selectedNames, disabledNames);
            }
            if (inWorld) {
                minecraft.setScreen(null);
                if (changed) {
                    PendingResourceReload.request();
                }
            } else {
                // 主菜单没有世界: 立即重载客户端资源(数据包会在下次进入世界时由枪包设置生效)
                minecraft.setScreen(parent != null ? parent : new TitleScreen());
                if (changed) {
                    minecraft.reloadResourcePacks();
                }
            }
        }, taczDir, Component.translatable("gui.taczfixes.gun_packs.title"));
    }

    private static Pack createPack(GunPackInfoScanner.PackInfoData info, int format) {
        Pack.Info packInfo = new Pack.Info(info.description(), format, FeatureFlags.DEFAULT_FLAGS);
        return Pack.create(info.id(), info.displayName(), false,
                id -> new GunPackResources(info.id(), info.path(), info.iconPath(), info.logo()),
                packInfo, PackType.CLIENT_RESOURCES, Pack.Position.TOP, false, PackSource.DEFAULT);
    }
}
