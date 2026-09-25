package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.VirtualAttachments;
import com.tacz.guns.network.message.ClientMessageUnloadAttachment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientMessageUnloadAttachment.class)
public class MixinClientMessageUnloadAttachment {

    /** 虚拟配件模式: 拆下的配件不返还背包(直接消失)。 */
    @Redirect(method = "lambda$handle$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;m_36054_(Lnet/minecraft/world/item/ItemStack;)Z"),
            remap = false)
    private static boolean taczfixes$virtualNoReturn(Inventory inventory, ItemStack stack,
                                                    NetworkEvent.Context context,
                                                    ClientMessageUnloadAttachment message) {
        if (VirtualAttachments.isActive(context.getSender())) {
            return true;
        }
        return inventory.add(stack);
    }
}
