package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.util.DurabilityStorage;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/** 工作台合并相同枪械以修复耐久(同原版工具规则, 5% 上限加成)。 */
@Mixin(RepairItemRecipe.class)
public class MixinRepairItemRecipeGunMerge {

    @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
    private void taczfixes$matchesGunMerge(CraftingContainer container, Level level,
                                           CallbackInfoReturnable<Boolean> cir) {
        ItemStack[] pair = taczfixes$pair(container);
        if (pair != null && taczfixes$canMerge(pair[0], pair[1])) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "assemble", at = @At("HEAD"), cancellable = true)
    private void taczfixes$assembleGunMerge(CraftingContainer container, RegistryAccess access,
                                            CallbackInfoReturnable<ItemStack> cir) {
        ItemStack[] pair = taczfixes$pair(container);
        if (pair == null || !taczfixes$canMerge(pair[0], pair[1])) {
            return;
        }
        int max = DurabilityStorage.getMax(pair[0]);
        int merged = Math.min(max, DurabilityStorage.get(pair[0]) + DurabilityStorage.get(pair[1])
                + max * 5 / 100);
        ItemStack result = pair[0].copyWithCount(1);
        DurabilityStorage.set(result, merged);
        result.setRepairCost(0);
        // 同原版修复配方: 仅保留诅咒附魔
        Map<Enchantment, Integer> curses = new HashMap<>();
        for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(result).entrySet()) {
            if (entry.getKey().isCurse()) {
                curses.put(entry.getKey(), entry.getValue());
            }
        }
        EnchantmentHelper.setEnchantments(curses, result);
        cir.setReturnValue(result);
    }

    /** 取出容器中的两件物品; 非恰好两件返回 null。 */
    private static ItemStack[] taczfixes$pair(CraftingContainer container) {
        ItemStack first = null;
        ItemStack second = null;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (first == null) {
                first = stack;
            } else if (second == null) {
                second = stack;
            } else {
                return null;
            }
        }
        return first == null || second == null ? null : new ItemStack[]{first, second};
    }

    private static boolean taczfixes$canMerge(ItemStack first, ItemStack second) {
        if (first.getCount() != 1 || second.getCount() != 1 || !first.is(second.getItem())) {
            return false;
        }
        GunTaczFixesData.DurabilityConfig cfg = DurabilityStorage.config(first);
        return cfg != null && Boolean.TRUE.equals(cfg.allow_merge)
                && DurabilityStorage.getMax(first) > 0 && DurabilityStorage.getMax(second) > 0;
    }
}
