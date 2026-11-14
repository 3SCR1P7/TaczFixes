package com.ssscript.taczfixes.client.handler;

import com.mojang.blaze3d.platform.InputConstants;
import com.ssscript.taczfixes.client.screen.GunDataEditScreen;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

public class EditDataKeyHandler {
    public static final KeyMapping EDIT_DATA_KEY = new KeyMapping(
            "key.taczfixes.edit_data",
            KeyConflictContext.UNIVERSAL,
            KeyModifier.ALT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_D,
            "key.categories.taczfixes");

    private static boolean wasDown = false;
    private static boolean legacyBindingChecked = false;

    @SubscribeEvent
    public void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        migrateLegacyDefaultBinding();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            wasDown = false;
            return;
        }
        boolean down = EDIT_DATA_KEY.isDown();
        if (down && !wasDown) {
            net.minecraft.world.item.ItemStack mainHand = mc.player != null ? mc.player.getMainHandItem() : net.minecraft.world.item.ItemStack.EMPTY;
            if (mainHand.getItem() instanceof com.tacz.guns.api.item.IGun
                    || mainHand.getItem() instanceof com.tacz.guns.api.item.IAttachment) {
                mc.setScreen(new GunDataEditScreen());
            }
        }
        wasDown = down;
    }

    /**
     * 旧版本默认键为 U(无修饰键), 升级后首次启动时一次性迁移到新的默认 Alt+D 并保存;
     * 迁移记录写入 config/taczfixes 下, 之后玩家手动改回 U 不会被再次覆盖。
     */
    private static void migrateLegacyDefaultBinding() {
        if (legacyBindingChecked) return;
        legacyBindingChecked = true;
        try {
            Path marker = FMLPaths.CONFIGDIR.get().resolve("taczfixes").resolve("keybind_migration.txt");
            if (Files.exists(marker)) return;
            boolean legacyDefault = EDIT_DATA_KEY.getKey().equals(InputConstants.getKey("key.keyboard.u"))
                    && EDIT_DATA_KEY.getKeyModifier() == KeyModifier.NONE;
            if (legacyDefault) {
                EDIT_DATA_KEY.setKeyModifierAndCode(KeyModifier.ALT, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_D));
                Minecraft.getInstance().options.save();
            }
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, "edit_data default: U -> ALT+D\n");
        } catch (Exception ignored) {
        }
    }
}
