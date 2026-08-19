package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.common.network.ClientMessageInstallCustomSlot;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.client.util.CustomSlotGuiState;
import com.ssscript.taczfixes.common.mixin.MixinClientMessageRefitGunAccessor;
import com.ssscript.taczfixes.common.util.LiberateCompat;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.network.message.ClientMessageRefitGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.simple.SimpleChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraftforge.network.simple.SimpleChannel", remap = false)
public class MixinSimpleChannelRefitRedirect {

    @Inject(method = "sendToServer", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$onSendToServer(Object message, CallbackInfo ci) {
        if (!(message instanceof ClientMessageRefitGun refit)) return;
        String selected = CustomSlotGuiState.get();
        if (selected == null) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack gunStack = player.getMainHandItem();
        IGun igun = IGun.getIGunOrNull(gunStack);
        if (igun == null) return;
        ResourceLocation gunId = igun.getGunId(gunStack);
        CustomSlotDefinition def = CustomSlotManager.getSlot(gunId, selected);
        if (def == null) {
            CustomSlotGuiState.reset();
            return;
        }
        int slotIndex = ((MixinClientMessageRefitGunAccessor) refit).getAttachmentSlotIndex();
        ItemStack item = LiberateCompat.getVirtualInventory(player.getInventory()).getItem(slotIndex);
        IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
        if (attachment == null) {
            return;
        }
        boolean liberated = LiberateCompat.isLiberated(player);
        boolean match = CustomSlotManager.matchesSlot(def, gunId, attachment.getAttachmentId(item), attachment.getType(item));
        if (!liberated && !match) {
            CustomSlotGuiState.reset();
            return;
        }
        ci.cancel();
        NetworkHandler.CHANNEL.sendToServer(new ClientMessageInstallCustomSlot(slotIndex, selected));
    }
}
