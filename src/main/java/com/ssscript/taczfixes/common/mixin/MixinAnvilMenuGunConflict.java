package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.neoforged.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.Map;

@Mixin(AnvilMenu.class)
public class MixinAnvilMenuGunConflict {
    @Unique
    private static Field taczfixes$costField;

    @Unique
    private boolean taczfixes$gunCreative;

    @Unique
    private static int taczfixes$getCost(AnvilMenu menu) {
        try {
            if (taczfixes$costField == null) {
                try {
                    taczfixes$costField = ObfuscationReflectionHelper.findField(AnvilMenu.class, "cost");
                } catch (Throwable ignored) {
                    taczfixes$costField = ObfuscationReflectionHelper.findField(AnvilMenu.class, "cost");
                }
            }
            return taczfixes$costField.getInt(menu);
        } catch (Throwable ignored) {
            return menu.getCost();
        }
    }

    @Unique
    private static void taczfixes$setCost(AnvilMenu menu, int value) {
        try {
            if (taczfixes$costField == null) {
                taczfixes$getCost(menu);
            }
            if (taczfixes$costField != null) {
                taczfixes$costField.setInt(menu, value);
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "createResult", at = @At("HEAD"))
    private void taczfixes$markGunAnvil(CallbackInfo ci) {
        ItemStack target = ((AnvilMenu) (Object) this).getSlot(0).getItem();
        GunEnchantmentHelper.setGunEnchanting(!target.isEmpty() && target.getItem() instanceof IGun);
    }

    @Redirect(method = "createResult",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/world/entity/player/Abilities;instabuild:Z",
                    opcode = Opcodes.GETFIELD))
    private boolean taczfixes$redirectInstabuild(Abilities abilities) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        ItemStack target = menu.getSlot(0).getItem();
        if (!(target.getItem() instanceof IGun)) {
            return abilities.instabuild;
        }
        this.taczfixes$gunCreative = abilities.instabuild;
        if (abilities.instabuild) {
            return true;
        }
        int gunLevel = GunEnchantmentHelper.getGunLevel(target);
        return menu.getCost() <= gunLevel + 39;
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void taczfixes$applyGunAnvilCostMultiplier(CallbackInfo ci) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        ItemStack target = menu.getSlot(0).getItem();
        ItemStack sacrifice = menu.getSlot(1).getItem();
        ItemStack result = menu.getSlot(2).getItem();
        if (result.isEmpty() || sacrifice.isEmpty() || !(target.getItem() instanceof IGun)) {
            this.taczfixes$gunCreative = false;
            GunEnchantmentHelper.setGunEnchanting(false);
            return;
        }
        ItemEnchantments sacrificeEnchants = EnchantmentHelper.getEnchantmentsForCrafting(sacrifice);
        ItemEnchantments targetEnchants = EnchantmentHelper.getEnchantmentsForCrafting(target);
        ItemEnchantments resultEnchants = EnchantmentHelper.getEnchantmentsForCrafting(result);
        int bonus = 0;
        for (var entry : sacrificeEnchants.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            ResourceKey<Enchantment> key = enchantment.unwrapKey().orElse(null);
            int multiplier = GunEnchantmentHelper.getAnvilCostMultiplier(key);
            if (multiplier <= 1 || resultEnchants.getLevel(enchantment) <= 0) {
                continue;
            }
            int sacrificeLevel = entry.getIntValue();
            int targetLevel = targetEnchants.getLevel(enchantment);
            int mergedLevel = sacrificeLevel == targetLevel ? sacrificeLevel + 1 : Math.max(sacrificeLevel, targetLevel);
            mergedLevel = Math.min(mergedLevel, enchantment.value().getMaxLevel());
            bonus += (multiplier - 1) * mergedLevel;
        }
        if (bonus > 0) {
            taczfixes$setCost((AnvilMenu) (Object) this, taczfixes$getCost((AnvilMenu) (Object) this) + bonus);
        }
        if (!this.taczfixes$gunCreative
                && taczfixes$getCost((AnvilMenu) (Object) this) > GunEnchantmentHelper.getGunLevel(target) + 39) {
            menu.getSlot(2).set(ItemStack.EMPTY);
        }
        this.taczfixes$gunCreative = false;
        GunEnchantmentHelper.setGunEnchanting(false);
    }
}
