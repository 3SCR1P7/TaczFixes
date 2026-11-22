package com.ssscript.taczfixes.client.handler;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.List;
import java.util.Map;

/**
 * 本模组枪械附魔的效果描述: 显示在物品栏 tooltip 中对应附魔名的下一行。
 * 文案来自语言文件 {@code enchantment.taczfixes.<附魔id>.desc}。
 */
public class GunEnchantmentTooltipHandler {
    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !GunEnchantmentHelper.isEnabled()) {
            return;
        }
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        if (enchantments.isEmpty()) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        for (Map.Entry<Holder<Enchantment>, Integer> entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            int level = entry.getValue();
            ResourceLocation id = holder.unwrapKey().map(key -> key.location()).orElse(null);
            if (id == null || !TaczFixesMod.MOD_ID.equals(id.getNamespace())) {
                continue;
            }
            String descriptionKey = "enchantment." + id.getNamespace() + "." + id.getPath() + ".desc";
            if (!I18n.exists(descriptionKey)) {
                continue;
            }
            Component description = Component.translatable(descriptionKey).withStyle(ChatFormatting.DARK_GRAY);
            insertUnderEnchantment(tooltip, Enchantment.getFullname(holder, level).getString(), description);
        }
    }

    /** 插到该附魔名那一行下面; 附魔被 HideFlags 隐藏时找不到对应行, 描述也就不显示。 */
    private static void insertUnderEnchantment(List<Component> tooltip, String enchantmentName,
                                               Component description) {
        for (int i = 0; i < tooltip.size(); i++) {
            if (enchantmentName.equals(tooltip.get(i).getString())) {
                tooltip.add(i + 1, description);
                return;
            }
        }
    }
}
