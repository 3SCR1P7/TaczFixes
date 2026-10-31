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
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlags;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
        // 每次 reload 都重新扫描, 否则拖入新枪包后 PackRepository 仍使用旧的扫描结果。
        PackRepository repository = new PackRepository(consumer -> {
            for (GunPackInfoScanner.PackInfoData info : GunPackInfoScanner.scan(taczDir, language)) {
                Pack pack = createPack(info, format);
                if (pack != null) {
                    consumer.accept(pack);
                }
            }
        });
        repository.reload();
        repository.setSelected(GunPackSettings.arrange(new ArrayList<>(repository.getAvailableIds())));
        Minecraft minecraft = Minecraft.getInstance();
        boolean inWorld = minecraft.level != null;
        return new PackSelectionScreen(repository, repo -> {
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
        PackLocationInfo location = new PackLocationInfo(info.id(), info.displayName(),
                PackSource.DEFAULT, java.util.Optional.empty());
        Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo loc) {
                return new GunPackResources(info.id(), info.path(), info.iconPath(), info.logo());
            }

            @Override
            public PackResources openFull(PackLocationInfo loc, Pack.Metadata metadata) {
                return openPrimary(loc);
            }
        };
        Pack.Metadata metadata = new Pack.Metadata(info.description(), PackCompatibility.COMPATIBLE,
                FeatureFlags.DEFAULT_FLAGS, List.of(), false);
        return new Pack(location, supplier, metadata,
                new PackSelectionConfig(false, Pack.Position.TOP, false));
    }
}
