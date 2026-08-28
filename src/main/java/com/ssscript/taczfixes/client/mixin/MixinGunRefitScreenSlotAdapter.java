package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.CustomSlotGuiState;
import com.ssscript.taczfixes.common.network.ClientMessageSetCustomSlotAdapter;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.util.SlotAdapterHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GunRefitScreen.class)
public abstract class MixinGunRefitScreenSlotAdapter extends Screen {

    protected MixinGunRefitScreenSlotAdapter(LocalPlayer player) {
        super(Component.literal(""));
    }

    @Shadow(remap = false) private int currentPage;

    @ModifyVariable(method = "addSlotAdapterButtons", at = @At("HEAD"), argsOnly = true, remap = false)
    private ItemStack taczfixes$substituteAttachmentItem(ItemStack attachmentItem,
                                                         com.tacz.guns.api.item.attachment.AttachmentType type) {
        String selected = CustomSlotGuiState.get();
        if (selected == null) return attachmentItem;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return attachmentItem;
        ItemStack gun = player.getMainHandItem();
        if (gun.isEmpty()) return attachmentItem;
        return CustomSlotStorage.get(gun, selected);
    }

    @Redirect(method = "addSlotAdapterButtons", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/util/SlotAdapterHelper;getSelectedAvailableSlotAdapter(Lnet/minecraft/world/item/ItemStack;Lcom/tacz/guns/api/item/attachment/AttachmentType;)Lnet/minecraft/resources/ResourceLocation;"), remap = false)
    private ResourceLocation taczfixes$customSelectedAdapter(ItemStack gun,
                                                             com.tacz.guns.api.item.attachment.AttachmentType type) {
        String selected = CustomSlotGuiState.get();
        if (selected == null) {
            return SlotAdapterHelper.getSelectedAvailableSlotAdapter(gun, type);
        }
        return CustomSlotStorage.getAdapter(gun, selected);
    }

    @ModifyVariable(method = "addSlotAdapterButtons", at = @At("HEAD"), argsOnly = true, ordinal = 1, remap = false)
    private int taczfixes$shiftAdapterY(int y) {
        if (y < 42 && taczfixes$isCustomSlotOccupied()) {
            return 42;
        }
        return y;
    }

    @Unique
    private static boolean taczfixes$isCustomSlotOccupied() {
        String selected = CustomSlotGuiState.get();
        if (selected == null) return false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        ItemStack gun = player.getMainHandItem();
        if (gun.isEmpty()) return false;
        return !CustomSlotStorage.get(gun, selected).isEmpty();
    }


    @Inject(method = "lambda$addSlotAdapterButtons$22", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$customAdapterClick(Inventory inventory,
                                              com.tacz.guns.api.item.attachment.AttachmentType type,
                                              ResourceLocation adapterId, Button button, CallbackInfo ci) {
        if (!button.active) {
            ci.cancel();
            return;
        }
        taczfixes$handleCustomAdapterClick(inventory, type, adapterId, ci);
    }

    @Inject(method = "lambda$addSlotAdapterButtons$21", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$customAdapterNoneClick(Inventory inventory,
                                                  com.tacz.guns.api.item.attachment.AttachmentType type,
                                                  Button button, CallbackInfo ci) {
        if (!button.active) {
            ci.cancel();
            return;
        }
        taczfixes$handleCustomAdapterClick(inventory, type, null, ci);
    }

    @Unique
    private void taczfixes$handleCustomAdapterClick(Inventory inventory,
                                                    com.tacz.guns.api.item.attachment.AttachmentType type,
                                                    ResourceLocation adapterId, CallbackInfo ci) {
        String selected = CustomSlotGuiState.get();
        if (selected == null) return;
        ItemStack gun = inventory.getItem(inventory.selected);
        // 业务层校验: 槽位有配件时, 目标适配器/无适配器必须允许该配件, 否则拒绝写入
        ItemStack item = CustomSlotStorage.get(gun, selected);
        if (!item.isEmpty()) {
            com.tacz.guns.api.item.IAttachment ia = com.tacz.guns.api.item.IAttachment.getIAttachmentOrNull(item);
            ResourceLocation itemId = ia != null ? ia.getAttachmentId(item) : null;
            boolean allowed;
            if (adapterId == null) {
                // 无适配器: 配件必须直连允许
                allowed = itemId != null && SlotAdapterHelper.allowsDirectAttachment(gun, itemId);
            } else {
                allowed = itemId != null && com.tacz.guns.api.TimelessAPI.getCommonSlotAdapterIndex(adapterId)
                        .map(index -> index.allowsAttachment(itemId)).orElse(false);
            }
            if (!allowed) return;
        }
        CustomSlotStorage.setAdapter(gun, selected, adapterId);
        NetworkHandler.CHANNEL.sendToServer(new ClientMessageSetCustomSlotAdapter(selected, adapterId));
        this.currentPage = 0;
        this.init();
        ci.cancel();
    }
}
