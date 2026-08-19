package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.client.util.CustomSlotGuiState;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.gui.components.refit.HSVSliderGroup;
import com.tacz.guns.util.LaserColorUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.Color;
import java.util.Locale;

@Mixin(HSVSliderGroup.class)
public class MixinHSVSliderGroupCustomSlot {

    @Shadow(remap = false)
    private Inventory inventory;

    @Shadow(remap = false)
    private int gunItemIndex;

    @Shadow(remap = false)
    private AttachmentType type;

    @Shadow(remap = false)
    private HSVSliderGroup.LaserColorSlider hueSlider;

    @Shadow(remap = false)
    private HSVSliderGroup.LaserColorSlider saturationSlider;

    @Inject(method = "apply", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$applyCustomSlotLaser(CallbackInfo ci) {
        ItemStack custom = taczfixes$customLaser();
        if (custom.isEmpty()) return;
        IAttachment attachment = IAttachment.getIAttachmentOrNull(custom);
        if (attachment == null) return;
        int color = Color.HSBtoRGB((float) this.hueSlider.getValue(),
                (float) this.saturationSlider.getValue(), 1f);
        attachment.setLaserColor(custom, color);
        taczfixes$writeBack(custom);
        ci.cancel();
    }

    @Inject(method = "getColor", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$getCustomSlotLaserColor(AttachmentType t, CallbackInfoReturnable<Integer> cir) {
        ItemStack custom = taczfixes$customLaser();
        if (custom.isEmpty()) return;
        cir.setReturnValue(LaserColorUtil.getLaserColor(custom));
    }

    @Unique
    private ItemStack taczfixes$customLaser() {
        if (this.inventory == null) return ItemStack.EMPTY;
        String slotId = CustomSlotGuiState.get();
        if (slotId == null) return ItemStack.EMPTY;
        ItemStack gun = this.inventory.getItem(this.gunItemIndex);
        IGun igun = IGun.getIGunOrNull(gun);
        if (igun == null) return ItemStack.EMPTY;
        ResourceLocation gunId = igun.getGunId(gun);
        CustomSlotDefinition def = CustomSlotManager.getSlot(gunId, slotId);
        if (def == null) return ItemStack.EMPTY;
        AttachmentType defType;
        try {
            defType = def.isCustom() ? AttachmentType.NONE
                    : AttachmentType.valueOf(def.type.toUpperCase(Locale.US));
        } catch (IllegalArgumentException ex) {
            return ItemStack.EMPTY;
        }
        if (defType != this.type) return ItemStack.EMPTY;
        ItemStack item = CustomSlotStorage.get(gun, slotId);
        if (item.isEmpty()) return ItemStack.EMPTY;
        if (IAttachment.getIAttachmentOrNull(item) == null) return ItemStack.EMPTY;
        return item;
    }

    @Unique
    private void taczfixes$writeBack(ItemStack item) {
        ItemStack gun = this.inventory.getItem(this.gunItemIndex);
        CompoundTag tag = gun.getOrCreateTag();
        CompoundTag slots = tag.contains(CustomSlotStorage.TAG_KEY, 10)
                ? tag.getCompound(CustomSlotStorage.TAG_KEY) : new CompoundTag();
        slots.put(CustomSlotGuiState.get(), item.save(new CompoundTag()));
        tag.put(CustomSlotStorage.TAG_KEY, slots);
    }
}