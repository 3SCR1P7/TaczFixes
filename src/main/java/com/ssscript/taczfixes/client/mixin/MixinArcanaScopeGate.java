package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.Config;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.item.ModernKineticGunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "group/taczexpands/dist/binq9IpL", remap = false)
public class MixinArcanaScopeGate {

    @Inject(method = "Iwtki3eC", at = @At("HEAD"), cancellable = true)
    private void taczfixes$onIwtki3eC(CallbackInfoReturnable<Boolean> cir) {
        if (!Config.DISABLE_ARCANA_MAGNIFICATION_FOR_SIGHT.get()) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;
        if (!(stack.getItem() instanceof ModernKineticGunItem gun)) return;

        String active = ScopeSwitchState.getActiveSlot(stack);
        if (active != null) {
            ItemStack activeItem = CustomSlotStorage.get(stack, active);
            if (!activeItem.isEmpty()) {
                IAttachment attachment = IAttachment.getIAttachmentOrNull(activeItem);
                ResourceLocation actId = attachment == null ? null : attachment.getAttachmentId(activeItem);
                if (actId != null && !DefaultAssets.isEmptyAttachmentId(actId)) {
                    ResourceLocation finalActId = actId;
                    TimelessAPI.getClientAttachmentIndex(finalActId).ifPresent(index -> {
                        if (!index.isScope()) {
                            cir.setReturnValue(false);
                        }
                    });
                    return;
                }
            }
        }

        ResourceLocation id = gun.getAttachmentId(stack, AttachmentType.SCOPE);
        if (id == null || id.equals(DefaultAssets.EMPTY_ATTACHMENT_ID)) {
            id = gun.getBuiltInAttachmentId(stack, AttachmentType.SCOPE);
        }
        if (id == null || id.equals(DefaultAssets.EMPTY_ATTACHMENT_ID)) {
            cir.setReturnValue(false);
            return;
        }

        ResourceLocation finalId = id;
        TimelessAPI.getClientAttachmentIndex(finalId).ifPresent(index -> {
            if (!index.isScope()) {
                cir.setReturnValue(false);
            }
        });
    }
}
