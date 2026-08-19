package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.Config;
import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModernKineticGunScriptAPI.class)
public class MixinGunScriptAPIOverloadConsume {
    @Shadow(remap = false)
    private LivingEntity shooter;

    @Shadow(remap = false)
    private ItemStack itemStack;

    @Inject(method = "reduceAmmoOnce", at = @At("RETURN"), remap = false)
    private void taczfixes$overloadConsumeExtra(CallbackInfoReturnable<Boolean> cir) {
        if (!GunEnchantmentHelper.isEnabled()) {
            return;
        }
        if (shooter == null || itemStack == null || itemStack.isEmpty() || cir.getReturnValue() != Boolean.TRUE) {
            return;
        }
        int level = GunEnchantmentHelper.getOverloadLevel(itemStack);
        if (level <= 0) {
            com.ssscript.taczfixes.common.util.OverloadDamage.setFactor(shooter, 1.0F);
            return;
        }
        double k = Config.ENCH_OVERLOAD_DAMAGE_PERCENT.get();
        int unbreaking = GunEnchantmentHelper.getLevel(itemStack, Enchantments.UNBREAKING);
        double saveChance = Math.min(Config.ENCH_UNBREAKING_NO_CONSUME_CHANCE.get() * unbreaking, 1.0);

        int success = 0;
        ModernKineticGunScriptAPI api = (ModernKineticGunScriptAPI) (Object) this;
        for (int i = 0; i < level; i++) {
            if (unbreaking > 0 && shooter.getRandom().nextDouble() < saveChance) {
                success++;
                continue;
            }
            if (!consumeOne(api)) {
                break;
            }
            success++;
        }
        float factor = 1.0F;
        if (success > 0) {
            factor = (float) (1.0 + success * k / 100.0);
        }
        com.ssscript.taczfixes.common.util.OverloadDamage.setFactor(shooter, factor);
    }

    @Unique
    private static boolean consumeOne(ModernKineticGunScriptAPI api) {
        if (api.getAmmoCountInMagazine() > 0) {
            return api.removeAmmoFromMagazine(1) > 0;
        }
        if (api.useInventoryAmmo() && api.hasAmmoToConsume()) {
            return api.consumeAmmoFromPlayer(1) > 0;
        }
        return false;
    }
}