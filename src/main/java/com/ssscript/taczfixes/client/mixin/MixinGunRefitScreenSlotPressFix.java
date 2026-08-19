package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.client.util.CustomSlotGuiState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.components.refit.GunAttachmentSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Locale;

@Mixin(GunRefitScreen.class)
public abstract class MixinGunRefitScreenSlotPressFix extends Screen {

    protected MixinGunRefitScreenSlotPressFix(LocalPlayer player) {
        super(Component.literal(""));
    }

    @Redirect(method = "addAttachmentTypeButtons", at = @At(value = "NEW",
            target = "com/tacz/guns/client/gui/components/refit/GunAttachmentSlot"), remap = false)
    private GunAttachmentSlot taczfixes$createGunAttachmentSlot(int pX, int pY, AttachmentType type,
                                                                int currentSlotIndex, Inventory inventory,
                                                                Button.OnPress onPress) {
        return new GunAttachmentSlot(pX, pY, type, currentSlotIndex, inventory, btn -> {
            String selected = CustomSlotGuiState.get();
            if (selected != null && type != AttachmentType.NONE
                    && btn instanceof GunAttachmentSlot slot && slot.isAllow()) {
                Minecraft mc = Minecraft.getInstance();
                LocalPlayer player = mc.player;
                if (player != null) {
                    ItemStack gunStack = player.getMainHandItem();
                    IGun igun = IGun.getIGunOrNull(gunStack);
                    if (igun != null) {
                        ResourceLocation gunId = igun.getGunId(gunStack);
                        CustomSlotDefinition def = CustomSlotManager.getSlot(gunId, selected);
                        if (def != null && !def.isCustom()) {
                            try {
                                if (AttachmentType.valueOf(def.type.toUpperCase(Locale.US)) == type) {
                                    RefitTransform.changeRefitScreenView(type);
                                    this.init();
                                    return;
                                }
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                    }
                }
            }
            onPress.onPress(btn);
        });
    }
}