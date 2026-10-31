package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.util.ItemNbt;
import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.util.DurabilityStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.GrindstoneEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import net.neoforged.fml.LogicalSide;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import com.tacz.guns.api.item.IGun;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;

/** 枪械耐久: 铁砧材料修复/同枪合并, 砂轮同枪合并。 */
public class GunDurabilityHandler {

    @SubscribeEvent
    public void onGunFire(GunFireEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) {
            return;
        }
        LivingEntity shooter = event.getShooter();
        ItemStack gun = actualGunStack(shooter, event.getGunItemStack());
        GunTaczFixesData.DurabilityConfig cfg = DurabilityStorage.config(gun);
        if (cfg == null || DurabilityStorage.getMax(gun) <= 0) {
            return;
        }
        int current = DurabilityStorage.get(gun);
        String action = DurabilityStorage.damageAction(cfg);
        if (current <= 0) {
            if ("none".equals(action)) {
                return;
            }
            if ("remove".equals(action)) {
                removeBrokenGun(shooter, gun);
            }
            event.setCanceled(true);
            return;
        }
        int consumption = cfg.fire_consumption == null ? 0 : cfg.fire_consumption;
        if (consumption <= 0) {
            return;
        }
        int remaining = DurabilityStorage.consume(gun, consumption);
        if (remaining <= 0 && "remove".equals(action)) {
            removeBrokenGun(shooter, gun);
        } else {
            syncGun(shooter, gun);
        }
    }

    private static ItemStack actualGunStack(LivingEntity shooter, ItemStack eventStack) {
        if (!(shooter instanceof Player player)) {
            return eventStack;
        }
        ItemStack main = player.getMainHandItem();
        if (sameGun(main, eventStack)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (sameGun(off, eventStack)) {
            return off;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (sameGun(stack, eventStack)) {
                return stack;
            }
        }
        return eventStack;
    }

    private static boolean sameGun(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty() || !a.is(b.getItem())) {
            return false;
        }
        IGun ga = IGun.getIGunOrNull(a);
        IGun gb = IGun.getIGunOrNull(b);
        if (ga == null || gb == null) {
            return false;
        }
        var idA = ga.getGunId(a);
        var idB = gb.getGunId(b);
        return idA != null && idA.equals(idB);
    }

    private static void syncGun(LivingEntity shooter, ItemStack gun) {
        if (shooter instanceof ServerPlayer player) {
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
        }
    }

    private static void removeBrokenGun(LivingEntity shooter, ItemStack gun) {
        gun.shrink(gun.getCount());
        if (shooter.level() != null) {
            shooter.level().playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
                    net.minecraft.sounds.SoundEvents.ITEM_BREAK, shooter.getSoundSource(), 0.8f,
                    0.8f + shooter.level().random.nextFloat() * 0.4f);
        }
        syncGun(shooter, gun);
    }


    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty()) {
            return;
        }
        GunTaczFixesData.DurabilityConfig cfg = DurabilityStorage.config(left);
        if (cfg == null) {
            return;
        }
        int max = DurabilityStorage.getMax(left);
        if (max <= 0) {
            return;
        }
        int current = DurabilityStorage.get(left);
        boolean instabuild = event.getPlayer() != null && event.getPlayer().getAbilities().instabuild;
        int renameOps = hasNameChange(event, left) ? 1 : 0;
        int baseCost = (int) Math.max(0L, event.getCost()) + renameOps;
        int tooExpensive = tooExpensiveThreshold(left);

        // 合并相同枪械修复耐久(同原版铁砧规则: 12% 上限加成 + 附魔合并)
        if (Boolean.TRUE.equals(cfg.allow_merge) && right.is(left.getItem())
                && DurabilityStorage.getMax(right) > 0) {
            int merged = Math.min(max, current + DurabilityStorage.get(right) + max * 12 / 100);
            if (merged > current) {
                ItemStack result = left.copy();
                DurabilityStorage.set(result, merged);
                // 原版铁砧附魔规则: 同等级+1, 封顶最大等级, 不兼容附魔计费且不可合并;
                // 全部附魔均不兼容时不产生结果(-1)。
                int enchantCost = mergeEnchantmentsVanilla(left, right, result, instabuild);
                if (enchantCost < 0) {
                    return;
                }
                // 同原版: 合并基础费用 2 + 附魔费用; 费用达到阈值(40 + 枪械等级)且非创造时不产生结果
                int totalCost = baseCost + 2 + enchantCost;
                if (totalCost >= tooExpensive && !instabuild) {
                    return;
                }
                result.set(net.minecraft.core.component.DataComponents.REPAIR_COST, Math.min(40, Math.max(repairCost(left), repairCost(right)) + 2));
                applyName(event, left, result);
                event.setOutput(result);
                event.setCost(totalCost);
                event.setMaterialCost(1);
                return;
            }
        }

        // 材料修复(同原版: 一格内多个材料可一次性消耗多个)
        if (current < max && DurabilityStorage.isRepairMaterial(right, cfg)) {
            int value = cfg.repair_value == null ? 0 : cfg.repair_value;
            if (value > 0) {
                int usable = Math.min(right.getCount(), (max - current + value - 1) / value);
                int totalCost = baseCost + usable;
                if (usable > 0 && (totalCost < tooExpensive || instabuild)) {
                    ItemStack result = left.copy();
                    DurabilityStorage.set(result, Math.min(max, current + usable * value));
                    result.set(net.minecraft.core.component.DataComponents.REPAIR_COST, Math.min(40, repairCost(left) + 2));
                    applyName(event, left, result);
                    event.setOutput(result);
                    event.setCost(totalCost);
                    event.setMaterialCost(usable);
                }
            }
        }
    }

    @SubscribeEvent
    public void onGrindstonePlace(GrindstoneEvent.OnPlaceItem event) {
        ItemStack top = event.getTopItem();
        ItemStack bottom = event.getBottomItem();
        if (top.isEmpty() || bottom.isEmpty() || top.getCount() != 1 || bottom.getCount() != 1) {
            return;
        }
        if (!top.is(bottom.getItem())) {
            return;
        }
        GunTaczFixesData.DurabilityConfig cfg = DurabilityStorage.config(top);
        if (cfg == null || !Boolean.TRUE.equals(cfg.allow_merge)) {
            return;
        }
        int max = DurabilityStorage.getMax(top);
        if (max <= 0 || DurabilityStorage.getMax(bottom) <= 0) {
            return;
        }
        int current = DurabilityStorage.get(top);
        int merged = Math.min(max, current + DurabilityStorage.get(bottom) + max * 5 / 100);
        if (merged <= current) {
            return;
        }
        ItemStack result = top.copyWithCount(1);
        DurabilityStorage.set(result, merged);
        result.set(net.minecraft.core.component.DataComponents.REPAIR_COST, 0);
        // 同原版砂轮: 仅保留诅咒附魔
        ItemEnchantments.Mutable curses = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(result).entrySet()) {
            if (entry.getKey().is(net.minecraft.tags.EnchantmentTags.CURSE)) {
                curses.set(entry.getKey(), entry.getIntValue());
            }
        }
        EnchantmentHelper.setEnchantments(result, curses.toImmutable());
        event.setOutput(result);
        event.setXp(0);
    }

    /** 过于昂贵的费用阈值: 40 + 枪械等级(复用附魔的过于昂贵规则)。 */
    private static int tooExpensiveThreshold(ItemStack gun) {
        net.minecraft.nbt.CompoundTag tag = ItemNbt.get(gun);
        int level = tag == null ? 0 : tag.getInt("GunLevel");
        return 40 + Math.max(0, level);
    }

    private static int repairCost(ItemStack stack) {
        return stack.getOrDefault(net.minecraft.core.component.DataComponents.REPAIR_COST, 0);
    }

    private static boolean hasNameChange(AnvilUpdateEvent event, ItemStack left) {
        String name = event.getName();
        return name != null && !name.isBlank() && !name.equals(left.getHoverName().getString());
    }

    private static void applyName(AnvilUpdateEvent event, ItemStack left, ItemStack result) {
        if (hasNameChange(event, left)) {
            result.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(event.getName()));
        }
    }

    /**
     * 原版铁砧附魔合并规则(移植自 AnvilMenu.createResult):
     * 与已有附魔同等级时 +1 级(封顶最大等级), 否则取较高等级;
     * 与已有附魔不兼容时该附魔无法合并并按原版计入费用;
     * 全部附魔均不兼容时返回 -1(不产生结果)。
     *
     * @return 附魔产生的额外等级费用; -1 表示无法合并
     */
    private static int mergeEnchantmentsVanilla(ItemStack left, ItemStack right, ItemStack result,
                                                boolean instabuild) {
        ItemEnchantments.Mutable map = new ItemEnchantments.Mutable(
                EnchantmentHelper.getEnchantmentsForCrafting(result));
        boolean compatible = false;
        boolean incompatible = false;
        int cost = 0;
        for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(right).entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            int leftLevel = map.getLevel(enchantment);
            int level = entry.getIntValue();
            level = leftLevel == level ? level + 1 : Math.max(level, leftLevel);
            boolean canApply = instabuild || enchantment.value().canEnchant(left);
            for (Holder<Enchantment> existing : map.keySet()) {
                if (!existing.equals(enchantment) && !Enchantment.areCompatible(existing, enchantment)) {
                    canApply = false;
                    cost++;
                }
            }
            if (!canApply) {
                incompatible = true;
                continue;
            }
            compatible = true;
            if (level > enchantment.value().getMaxLevel()) {
                level = enchantment.value().getMaxLevel();
            }
            map.set(enchantment, level);
            int rarityWeight = switch (enchantment.value().getWeight()) {
                case 10 -> 1;
                case 5 -> 2;
                case 2 -> 4;
                default -> 8;
            };
            cost += rarityWeight * level;
        }
        if (incompatible && !compatible) {
            return -1;
        }
        EnchantmentHelper.setEnchantments(result, map.toImmutable());
        return cost;
    }
}
