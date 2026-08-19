package com.ssscript.taczfixes.client.handler;

import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.ssscript.taczfixes.client.util.SwitchedDisplayManager;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

public class DisplaySwitchKeyHandler {
    public static final KeyMapping SWITCH_FORM_KEY = new KeyMapping(
            "key.taczfixes.switch_form",
            GLFW.GLFW_KEY_N,
            "key.categories.taczfixes");

    private static boolean wasDown = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        boolean down = SWITCH_FORM_KEY.isDown();
        if (down && !wasDown) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null || !player.isAlive()) return;
            ItemStack gun = player.getMainHandItem();
            IGun iGun = IGun.getIGunOrNull(gun);
            if (iGun == null) return;
            int count = switchAllForms(gun, iGun);
        }
        wasDown = down;
    }

    private static int switchAllForms(ItemStack gun, IGun iGun) {
        int count = 0;
        for (AttachmentType type : AttachmentType.values()) {
            ResourceLocation id = iGun.getAttachmentId(gun, type);
            if (DefaultAssets.isEmptyAttachmentId(id)) {
                id = iGun.getBuiltInAttachmentId(gun, type);
            }
            if (!DefaultAssets.isEmptyAttachmentId(id)) {
                int next = SwitchedDisplayManager.advanceForm(gun, id);
                if (next >= 0) {
                    count++;
                }
            }
        }
        ResourceLocation gunId = iGun.getGunId(gun);
        Map<String, CustomSlotDefinition> slots = CustomSlotManager.getSlots(gunId);
        for (Map.Entry<String, CustomSlotDefinition> entry : slots.entrySet()) {
            ItemStack item = CustomSlotStorage.get(gun, entry.getKey());
            if (item.isEmpty()) continue;
            IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
            if (attachment == null) continue;
            int next = SwitchedDisplayManager.advanceForm(gun, attachment.getAttachmentId(item));
            if (next >= 0) {
                count++;
            }
        }
        return count;
    }
}