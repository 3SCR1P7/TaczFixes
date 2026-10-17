package com.ssscript.taczfixes.client.screen;

import com.ssscript.taczfixes.client.util.PendingResourceReload;
import com.ssscript.taczfixes.common.data.GunPackInfoScanner;
import com.ssscript.taczfixes.common.data.GunPackResources;
import com.ssscript.taczfixes.common.data.GunPackSettings;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
import java.util.List;

/** 枪包管理: 直接复用原版资源包界面(PackSelectionScreen), 只把枪包作为包列表传入。 */
public final class GunPackScreen {
    private GunPackScreen() {
    }

    public static Screen create() {
        Path taczDir = FMLPaths.GAMEDIR.get().resolve("tacz");
        int format = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
        // 每次 reload 都重新扫描, 这样拖入界面的新枪包(原版会复制进 tacz 目录)也能出现。
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        PackRepository repository = new PackRepository(consumer -> {
            for (GunPackInfoScanner.PackInfoData info : GunPackInfoScanner.scan(taczDir, language)) {
                consumer.accept(createPack(info, format));
            }
        });
        repository.reload();
        repository.setSelected(GunPackSettings.arrange(new ArrayList<>(repository.getAvailableIds())));
        return new PackSelectionScreen(repository, repo -> {
            List<String> selected = new ArrayList<>(repo.getSelectedIds());
            List<String> disabled = new ArrayList<>();
            for (String id : repo.getAvailableIds()) {
                if (!selected.contains(id)) disabled.add(id);
            }
            disabled.sort(String.CASE_INSENSITIVE_ORDER);
            // 无改动则不写设置、不重载(避免仅点击"完成"也触发资源重载)。
            boolean changed = !GunPackSettings.order().equals(selected)
                    || !GunPackSettings.disabledNames().equals(disabled);
            Minecraft.getInstance().setScreen(null);
            if (changed) {
                GunPackSettings.update(selected, disabled);
                PendingResourceReload.request();
            }
        }, taczDir, Component.translatable("gui.taczfixes.gun_packs.title"));
    }

    private static Pack createPack(GunPackInfoScanner.PackInfoData info, int format) {
        Pack.Info packInfo = new Pack.Info(info.description(), format, FeatureFlags.DEFAULT_FLAGS);
        return Pack.create(info.name(), info.displayName(), false,
                id -> new GunPackResources(info.name(), info.path(), info.iconPath(), info.logo()),
                packInfo, PackType.CLIENT_RESOURCES, Pack.Position.TOP, false, PackSource.DEFAULT);
    }
}
