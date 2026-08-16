package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.util.CustomSlotStorage;
import com.ssscript.taczfixes.util.ScopeSwitchState;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.tacz.guns.client.input.ZoomKey", remap = false)
public class MixinZoomKey {

    @Inject(method = "doZoomLogic", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$customSlotZoom(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isDeadOrDying()) return;
        ItemStack gun = player.getMainHandItem();
        if (IGun.getIGunOrNull(gun) == null) return;
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (operator == null) return;
        String active = ScopeSwitchState.getActiveSlot(gun);
        if (active == null) return;
        ItemStack scope = CustomSlotStorage.get(gun, active);
        if (scope.isEmpty()) return;
        IAttachment attachment = IAttachment.getIAttachmentOrNull(scope);
        if (attachment == null) return;
        ResourceLocation id = attachment.getAttachmentId(scope);
        if (id == null || DefaultAssets.isEmptyAttachmentId(id)) return;
        if (TimelessAPI.getClientAttachmentIndex(id).map(ClientAttachmentIndex::getZoom).orElse(null) == null) {
            return;
        }
        CompoundTag tag = scope.getOrCreateTag();
        int number = AttachmentItemDataAccessor.getZoomNumberFromTag(tag);
        AttachmentItemDataAccessor.setZoomNumberToTag(tag, (number + 1) % 2147483646);
        CompoundTag gunTag = gun.getOrCreateTag();
        CompoundTag slots = gunTag.getCompound(CustomSlotStorage.TAG_KEY);
        slots.put(active, scope.save(new CompoundTag()));
        gunTag.put(CustomSlotStorage.TAG_KEY, slots);
        com.ssscript.taczfixes.network.NetworkHandler.CHANNEL.sendToServer(
                new com.ssscript.taczfixes.network.ClientMessageCustomSlotZoom(active));
        ci.cancel();
    }
}