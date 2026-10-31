package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.tacz.guns.api.item.IGun;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 标准槽配件卸下后清理来源配件移除导致的孤儿自定义槽。 */
@Mixin(targets = "com.tacz.guns.network.message.ClientMessageUnloadAttachment", remap = false)
public class MixinClientMessageUnloadAttachmentOrphanCleanup {
    @Inject(method = "lambda$handle$2", at = @At("RETURN"), remap = false)
    private static void taczfixes$orphanCleanup(IPayloadContext context,
                                                com.tacz.guns.network.message.ClientMessageUnloadAttachment message,
                                                CallbackInfo ci) {
        taczfixes$cleanup(context);
    }

    @Unique
    private static void taczfixes$cleanup(IPayloadContext context) {
        ServerPlayer player = context.player() instanceof ServerPlayer p ? p : null;
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
