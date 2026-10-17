package com.ssscript.taczfixes.client.handler;

import com.ssscript.taczfixes.client.screen.GunPackMenuButton;
import com.ssscript.taczfixes.client.util.GunPackIconLoader;
import com.ssscript.taczfixes.common.config.Config;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 在主菜单/暂停界面的图标按钮列(Mods/选项按钮左侧)添加枪包管理入口。
 * 图标使用 TaCZ 默认枪包的 logo; 按钮无文字, 悬浮显示标题。位置/尺寸可在 config/compat/gun_pack 配置。
 */
public class GunPackMenuButtonHandler {
    /** TaCZ 默认枪包 logo: GunPackIconLoader 会自动在 textures/ 下查找, 路径不含 textures 前缀。 */
    private static final ResourceLocation DEFAULT_LOGO = new ResourceLocation("tacz", "pack");

    @SubscribeEvent
    public void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        boolean title = screen instanceof TitleScreen;
        boolean pause = screen instanceof PauseScreen;
        if (!title && !pause) {
            return;
        }
        if (taczfixes$hasButton(screen)) {
            return;
        }
        Button anchor;
        int anchorY;
        if (title) {
            anchor = taczfixes$findButton(screen, "fml.menu.mods");
            anchorY = anchor == null ? -1 : anchor.getY();
            if (anchor == null) {
                // 没有 Forge Mods 按钮时放在选项行的上一行同一列
                anchor = taczfixes$findButton(screen, "menu.options");
                if (anchor == null) {
                    return;
                }
                anchorY = anchor.getY() - 24;
            }
        } else {
            anchor = taczfixes$findButton(screen, "menu.options");
            if (anchor == null) {
                return;
            }
            anchorY = anchor.getY();
        }
        int size = Math.max(8, Config.GUN_PACK_BUTTON_SIZE.get());
        int x = anchor.getX() - size - 4 + Config.GUN_PACK_BUTTON_OFFSET_X.get();
        int y = anchorY + (anchor.getHeight() - size) / 2 + Config.GUN_PACK_BUTTON_OFFSET_Y.get();
        GunPackIconLoader.LoadedIcon icon = GunPackIconLoader.load(DEFAULT_LOGO);
        event.addListener(new GunPackMenuButton(x, y, size, icon, screen));
    }

    private static boolean taczfixes$hasButton(Screen screen) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof GunPackMenuButton) {
                return true;
            }
        }
        return false;
    }

    private static Button taczfixes$findButton(Screen screen, String translationKey) {
        String label = Component.translatable(translationKey).getString();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && button.getMessage().getString().equals(label)) {
                return button;
            }
        }
        return null;
    }
}
