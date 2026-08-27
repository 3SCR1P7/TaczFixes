package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.network.ClientMessageInstallCustomSlot;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.client.util.CustomSlotGuiState;
import com.ssscript.taczfixes.client.util.TaczFixesClientState;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.ssscript.taczfixes.common.util.LiberateCompat;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.components.refit.InventoryAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.RefitTurnPageButton;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.sound.SoundManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(GunRefitScreen.class)
public abstract class MixinGunRefitScreenAttachmentList extends Screen {

    protected MixinGunRefitScreenAttachmentList(LocalPlayer player) {
        super(Component.literal(""));
    }

    @Inject(method = "addInventoryAttachmentButtons", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$customInventoryButtons(CallbackInfo ci) {
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
        if (def == null) return;
        Inventory inventory = LiberateCompat.getVirtualInventory(player.getInventory());
        int x = this.width - 30;
        int y0 = 50;
        List<Integer> matched = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
            if (attachment == null) continue;
            if (!igun.allowAttachment(gunStack, stack)) continue;
            if (!CustomSlotManager.matchesSlot(def, gunId, attachment.getAttachmentId(stack), attachment.getType(stack))) {
                continue;
            }
            matched.add(i);
        }
        int page = CustomSlotGuiState.getPage();
        int perPage = 8;
        int start = page * perPage;
        int shown = 0;
        for (int k = start; k < matched.size() && shown < perPage; k++) {
            final int index = matched.get(k);
            final ItemStack stack = inventory.getItem(index);
            InventoryAttachmentSlot slot = new InventoryAttachmentSlot(x, y0 + shown * 18, index, inventory,
                    btn -> {
                        ItemStack installed = CustomSlotStorage.get(gunStack, selected);
                        int oldConsume = installed.isEmpty() ? 0 : AttachmentTaczFixesManager.getRefitPointConsume(installed);
                        Integer total = TaczFixesDataManager.getGunRefitPoint(gunStack);
                        if (total != null) {
                            int used = AttachmentTaczFixesManager.getRefitPointUsed(gunStack);
                            int add = AttachmentTaczFixesManager.getRefitPointConsume(stack);
                            if (used + add > total + oldConsume) {
                                btn.setFocused(false);
                                TaczFixesClientState.markRejectFocusClear();
                                SoundPlayManager.playerRefitSound(stack, player, SoundManager.INSTALL_SOUND);
                                return;
                            }
                        }
                        SoundPlayManager.playerRefitSound(stack, player, SoundManager.INSTALL_SOUND);
                        NetworkHandler.CHANNEL.sendToServer(new ClientMessageInstallCustomSlot(index, selected));
                    });
            this.addRenderableWidget(slot);
            shown++;
        }
        if (matched.size() > perPage) {
            if (page > 0) {
                this.addRenderableWidget(new RefitTurnPageButton(x, y0 - 10, true,
                        btn -> {
                            CustomSlotGuiState.setPage(page - 1);
                            this.init();
                        }));
            }
            if (matched.size() > (page + 1) * perPage) {
                this.addRenderableWidget(new RefitTurnPageButton(x, y0 + 144 + 2, false,
                        btn -> {
                            CustomSlotGuiState.setPage(page + 1);
                            this.init();
                        }));
            }
        }
        ci.cancel();
    }
}