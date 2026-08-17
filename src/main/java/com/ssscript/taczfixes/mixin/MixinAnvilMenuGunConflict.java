package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.util.GunEnchantmentHelper;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(AnvilMenu.class)
public class MixinAnvilMenuGunConflict {
    @Shadow
    private DataSlot cost;

    @Unique
    private boolean taczfixes$gunCreative;

    @Inject(method = "createResult", at = @At("HEAD"))
    private void taczfixes$markGunAnvil(CallbackInfo ci) {
        ItemStack target = ((AnvilMenu) (Object) this).getSlot(0).getItem();
        GunEnchantmentHelper.setGunEnchanting(!target.isEmpty() && target.getItem() instanceof IGun);
    }

    @Redirect(method = "createResult",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/Enchantment;getRarity()Lnet/minecraft/world/item/enchantment/Enchantment$Rarity;"))
    private Enchantment.Rarity taczfixes$gunEnchantmentRarityForAnvilCost(Enchantment enchantment) {
        if (GunEnchantmentHelper.isGunEnchanting() && GunEnchantmentHelper.isConfiguredAnvilEnchantment(enchantment)) {
            return Enchantment.Rarity.COMMON;
        }
        return enchantment.getRarity();
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
        int gunLevel = target.getOrCreateTag().getInt("GunLevel");
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
        Map<Enchantment, Integer> sacrificeEnchants = EnchantmentHelper.getEnchantments(sacrifice);
        Map<Enchantment, Integer> targetEnchants = EnchantmentHelper.getEnchantments(target);
        Map<Enchantment, Integer> resultEnchants = EnchantmentHelper.getEnchantments(result);
        int bonus = 0;
        for (Map.Entry<Enchantment, Integer> entry : sacrificeEnchants.entrySet()) {
            Enchantment enchantment = entry.getKey();
            int multiplier = GunEnchantmentHelper.getAnvilCostMultiplier(enchantment);
            if (multiplier <= 1 || !resultEnchants.containsKey(enchantment)) {
                continue;
            }
            int sacrificeLevel = entry.getValue();
            int targetLevel = targetEnchants.getOrDefault(enchantment, 0);
            int mergedLevel = sacrificeLevel == targetLevel ? sacrificeLevel + 1 : Math.max(sacrificeLevel, targetLevel);
            mergedLevel = Math.min(mergedLevel, enchantment.getMaxLevel());
            bonus += (multiplier - 1) * mergedLevel;
        }
        if (bonus > 0) {
            this.cost.set(this.cost.get() + bonus);
        }
        if (!this.taczfixes$gunCreative && this.cost.get() > target.getOrCreateTag().getInt("GunLevel") + 39) {
            menu.getSlot(2).set(ItemStack.EMPTY);
        }
        this.taczfixes$gunCreative = false;
        GunEnchantmentHelper.setGunEnchanting(false);
    }
}
