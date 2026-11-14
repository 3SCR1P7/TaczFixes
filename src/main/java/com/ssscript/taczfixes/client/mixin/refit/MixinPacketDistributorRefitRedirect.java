package com.ssscript.taczfixes.client.mixin.refit;

import com.ssscript.taczfixes.client.util.CustomSlotGuiState;
import com.ssscript.taczfixes.client.util.TaczFixesClientState;
import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.mixin.MixinClientMessageRefitGunAccessor;
import com.ssscript.taczfixes.common.network.ClientMessageInstallCustomSlot;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.common.util.TaczItemCompat;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.network.message.ClientMessageRefitGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.1 counterpart of the 1.20.1 {@code MixinSimpleChannelRefitRedirect}: TaCZ now sends its
 * refit packet through {@code PacketDistributor.sendToServer}, so intercept it there.
 * <ul>
 *     <li>No custom slot selected: reject the install client-side when it would exceed the gun's
 *     custom refit point budget (TaCZ itself does not know about that budget).</li>
 *     <li>Custom slot selected: rewrite the native refit request into our dedicated custom-slot
 *     install message so the attachment lands in the custom slot.</li>
 * </ul>
 */
@SuppressWarnings({"target", "mapping", "public-target", "unresolvable-target", "visibility", "constraints", "overwrite", "default-package", "raw-types"})
@Mixin(targets = "net.neoforged.neoforge.network.PacketDistributor", remap = false)
public class MixinPacketDistributorRefitRedirect {

    @Inject(method = "sendToServer", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$checkRefitPointOnSend(CustomPacketPayload payload,
                                                        CustomPacketPayload[] extras,
                                                        CallbackInfo ci) {
        if (!(payload instanceof ClientMessageRefitGun refit)) return;
        if (CustomSlotGuiState.get() != null) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack gunStack = player.getMainHandItem();
        Integer total = TaczFixesDataManager.getGunRefitPoint(gunStack);
        if (total == null) return;
        int slotIndex = ((MixinClientMessageRefitGunAccessor) refit).getAttachmentSlotIndex();
        ItemStack attachmentStack = player.getInventory().getItem(slotIndex);
        IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentStack);
        if (attachment == null) return;
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return;
        AttachmentType type = attachment.getType(attachmentStack);
        ItemStack installed = TaczItemCompat.getAttachment(gun, gunStack, type);
        int oldConsume = installed.isEmpty() ? 0 : AttachmentTaczFixesManager.getRefitPointConsume(installed);
        int used = AttachmentTaczFixesManager.getRefitPointUsed(gunStack);
        int add = AttachmentTaczFixesManager.getRefitPointConsume(attachmentStack);
        if (used + add > total + oldConsume) {
            taczfixes$clearFocusedAttachmentButtons(mc);
            TaczFixesClientState.markRejectFocusClear();
            ci.cancel();
        }
    }

    @Inject(method = "sendToServer", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$redirectCustomSlotSend(CustomPacketPayload payload,
                                                         CustomPacketPayload[] extras,
                                                         CallbackInfo ci) {
        if (!(payload instanceof ClientMessageRefitGun refit)) return;
        String selected = CustomSlotGuiState.get();
        if (selected == null) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack gunStack = player.getMainHandItem();
        IGun igun = IGun.getIGunOrNull(gunStack);
        if (igun == null) return;
        ResourceLocation gunId = igun.getGunId(gunStack);
        CustomSlotDefinition def = CustomSlotManager.getSlot(gunStack, selected);
        if (def == null) {
            CustomSlotGuiState.reset();
            return;
        }
        int slotIndex = ((MixinClientMessageRefitGunAccessor) refit).getAttachmentSlotIndex();
        ItemStack item = player.getInventory().getItem(slotIndex);
        IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
        if (attachment == null) {
            return;
        }
        boolean match = CustomSlotManager.matchesSlot(def, gunId, attachment.getAttachmentId(item), attachment.getType(item));
        if (!match) {
            CustomSlotGuiState.reset();
            return;
        }
        ci.cancel();
        NetworkHandler.CHANNEL.sendToServer(new ClientMessageInstallCustomSlot(slotIndex, selected));
    }

    @Unique
    private static void taczfixes$clearFocusedAttachmentButtons(Minecraft mc) {
        if (!(mc.screen instanceof GunRefitScreen screen)) return;
        for (GuiEventListener listener : screen.children()) {
            if (listener instanceof AbstractWidget widget && widget.isFocused()) {
                widget.setFocused(false);
            }
        }
    }
}
