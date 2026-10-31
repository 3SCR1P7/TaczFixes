package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.util.ItemNbt;
import com.ssscript.taczfixes.client.client.DualFocusAimKey;
import com.ssscript.taczfixes.client.client.DualWieldClient;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.tacz.guns.client.input.ZoomKey", remap = false)
public class MixinZoomKey {

    @Inject(method = "onZoomPress", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$onZoomPress(CallbackInfoReturnable<Boolean> callback) {
        if (taczfixes$customSlotZoom()) {
            callback.setReturnValue(true);
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
    }

    private static boolean taczfixes$customSlotZoom() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isDeadOrDying()) return false;
        ItemStack gun = player.getMainHandItem();
        if (IGun.getIGunOrNull(gun) == null) return false;
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (operator == null) return false;
        String active = ScopeSwitchState.getActiveSlot(gun);
        if (active == null) return false;
        ItemStack scope = CustomSlotStorage.get(gun, active);
        if (scope.isEmpty()) return false;
        IAttachment attachment = IAttachment.getIAttachmentOrNull(scope);
        if (attachment == null) return false;
        ResourceLocation id = attachment.getAttachmentId(scope);
        if (id == null || DefaultAssets.isEmptyAttachmentId(id)) return false;
        if (TimelessAPI.getClientAttachmentIndex(id).map(ClientAttachmentIndex::getZoom).orElse(null) == null) {
            return false;
        }
        CompoundTag tag = ItemNbt.getOrCreate(scope);
        int number = AttachmentItemDataAccessor.getZoomNumberFromTag(tag);
        AttachmentItemDataAccessor.setZoomNumberToTag(tag, (number + 1) % 2147483646);
        CompoundTag gunTag = ItemNbt.getOrCreate(gun);
        CompoundTag slots = gunTag.getCompound(CustomSlotStorage.TAG_KEY);
        slots.put(active, ItemNbt.saveStack(scope));
        gunTag.put(CustomSlotStorage.TAG_KEY, slots);
        com.ssscript.taczfixes.common.network.NetworkHandler.CHANNEL.sendToServer(
                new com.ssscript.taczfixes.common.network.ClientMessageCustomSlotZoom(active));
        return true;
    }
}
