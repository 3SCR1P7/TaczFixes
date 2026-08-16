package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.data.CustomSlotDefinition;
import com.ssscript.taczfixes.data.CustomSlotManager;
import com.ssscript.taczfixes.network.ClientMessageLoadRefitPreset;
import com.ssscript.taczfixes.network.NetworkHandler;
import com.ssscript.taczfixes.util.CustomSlotStorage;
import com.ssscript.taczfixes.util.RefitPresetStorage;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.config.common.GunConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashMap;
import java.util.Map;

@Mixin(GunRefitScreen.class)
public abstract class MixinGunRefitScreenPresetButtons extends Screen {

    protected MixinGunRefitScreenPresetButtons(LocalPlayer player) {
        super(Component.literal(""));
    }

    @Inject(method = "addAttachmentTypeButtons", at = @At("TAIL"), remap = false)
    private void taczfixes$addPresetButtons(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack gunStack = player.getMainHandItem();
        IGun igun = IGun.getIGunOrNull(gunStack);
        if (igun == null) return;
        ResourceLocation gunId = igun.getGunId(gunStack);
        Map<String, ResourceLocation> preset = RefitPresetStorage.get(gunId);
        boolean hasPreset = preset != null && !preset.isEmpty();
        int y = this.height - 28;
        int right = this.width - 20;
        Component saveLabel = Component.literal("保存当前改装");
        int saveW = this.font.width(saveLabel) + 10;
        if (hasPreset) {
            Component loadLabel = Component.literal("加载上次改装");
            int loadW = this.font.width(loadLabel) + 10;
            int loadX = right - saveW - loadW - 4;
            this.addRenderableWidget(Button.builder(loadLabel, b -> loadPreset(gunId))
                    .bounds(loadX, y, loadW, 18).build());
        }
        int saveX = right - saveW;
        this.addRenderableWidget(Button.builder(saveLabel, b -> savePreset(gunStack, gunId))
                .bounds(saveX, y, saveW, 18).build());
    }

    private void savePreset(ItemStack gunStack, ResourceLocation gunId) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return;
        Map<String, ResourceLocation> preset = new LinkedHashMap<>();
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            ItemStack item = gun.getAttachment(gunStack, type);
            IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
            if (attachment != null) {
                preset.put(type.name(), attachment.getAttachmentId(item));
            }
        }
        for (Map.Entry<String, CustomSlotDefinition> entry : CustomSlotManager.getSlots(gunId).entrySet()) {
            ResourceLocation attachmentId = CustomSlotStorage.getAttachmentId(gunStack, entry.getKey());
            if (attachmentId != null) {
                preset.put(entry.getKey(), attachmentId);
            }
        }
        RefitPresetStorage.save(gunId, preset);
        playRefitSound(ResourceLocation.tryParse("tacz:attachments/attachment_general_uninstall"));
        this.init();
    }

    private void loadPreset(ResourceLocation gunId) {
        Map<String, ResourceLocation> preset = RefitPresetStorage.get(gunId);
        if (preset == null || preset.isEmpty()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            ResourceLocation soundId = player.getRandom().nextBoolean()
                    ? ResourceLocation.tryParse("tacz:attachments/attachment_general_a")
                    : ResourceLocation.tryParse("tacz:attachments/attachment_general_b");
            playRefitSound(soundId);
        }
        NetworkHandler.CHANNEL.sendToServer(new ClientMessageLoadRefitPreset(preset));
    }

    private void playRefitSound(ResourceLocation soundId) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        SoundPlayManager.playClientSound(player, soundId, 1.0f, 1.0f,
                GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
    }
}