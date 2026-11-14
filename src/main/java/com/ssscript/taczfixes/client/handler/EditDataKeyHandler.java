package com.ssscript.taczfixes.client.handler;

import com.mojang.blaze3d.platform.InputConstants;
import com.ssscript.taczfixes.client.screen.GunDataEditScreen;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;
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
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
