package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.tacz.guns.api.item.IGun;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 标准槽配件卸下后清理来源配件移除导致的孤儿自定义槽。 */
@Mixin(targets = "com.tacz.guns.network.message.ClientMessageUnloadAttachment", remap = false)
public class MixinClientMessageUnloadAttachmentOrphanCleanup {
    @Inject(method = "lambda$handle$0", at = @At("RETURN"), remap = false)
    private static void taczfixes$orphanCleanup(NetworkEvent.Context context,
                                                com.tacz.guns.network.message.ClientMessageUnloadAttachment message,
                                                CallbackInfo ci) {
        taczfixes$cleanup(context);
    }

    @Unique
    private static void taczfixes$cleanup(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null) return;
        for (ItemStack stack : player.getInventory().items) {
            taczfixes$cleanGun(player, stack);
        }
        taczfixes$cleanGun(player, player.getMainHandItem());
        taczfixes$cleanGun(player, player.getOffhandItem());
    }

    @Unique
    private static void taczfixes$cleanGun(ServerPlayer player, ItemStack stack) {
        if (stack != null && !stack.isEmpty() && stack.getItem() instanceof IGun) {
            CustomSlotManager.cascadeUnloadDependents(player, stack);
            CustomSlotManager.cascadeUnloadConflicts(player, stack);
        }
    }
}
