package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ModernKineticGunScriptAPI.class)
public class MixinModernKineticGunScriptAPI {

    @Shadow
    private ItemStack itemStack;

    @Unique
    public String tfGetCustomAttachment(String slotId) {
        ResourceLocation id = CustomSlotStorage.getAttachmentId(itemStack, slotId);
        return id == null ? "tacz:empty" : id.toString();
    }
}