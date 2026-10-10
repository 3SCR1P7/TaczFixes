package com.ssscript.taczfixes.client.handler;

import com.ssscript.taczfixes.common.util.DurabilityStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** F3+H 高级提示框下显示枪械耐久(未开启耐久条时的查看途径)。 */
public class GunDurabilityTooltipHandler {
    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || !mc.options.advancedItemTooltips) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || DurabilityStorage.config(stack) == null || DurabilityStorage.getMax(stack) <= 0) {
            return;
        }
        int durability = DurabilityStorage.get(stack);
        // 满耐久不显示
        if (durability >= DurabilityStorage.getMax(stack)) {
            return;
        }
        event.getToolTip().add(Component.translatable("tooltip.taczfixes.durability",
                durability, DurabilityStorage.getMax(stack)).withStyle(ChatFormatting.WHITE));
    }
}
