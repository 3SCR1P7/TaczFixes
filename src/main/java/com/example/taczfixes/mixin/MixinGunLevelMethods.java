package com.example.taczfixes.mixin;

import com.example.taczfixes.Config;
import com.tacz.guns.item.ModernKineticGunItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModernKineticGunItem.class)
public class MixinGunLevelMethods {
    @Inject(method = "getLevel", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$getLevel(int exp, CallbackInfoReturnable<Integer> cir) {
        int maxLevel = Config.GUN_LEVEL_MAX_LEVEL.get();
        int base = Config.GUN_LEVEL_BASE_KILLS.get();
        int inc = Config.GUN_LEVEL_INCREMENT.get();
        if (exp <= 0 || exp < base) { cir.setReturnValue(0); return; }
        int level;
        if (inc == 0) {
            level = exp / base;
        } else {
            double disc = 1.0 + 8.0 * (double) (exp - base) / (double) inc;
            if (disc < 0) { cir.setReturnValue(0); return; }
            level = (int) Math.floor((1.0 + Math.sqrt(disc)) / 2.0);
        }
        cir.setReturnValue(Math.min(level, maxLevel));
    }

    @Inject(method = "getExp", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$getExp(int level, CallbackInfoReturnable<Integer> cir) {
        int maxLevel = Config.GUN_LEVEL_MAX_LEVEL.get();
        int base = Config.GUN_LEVEL_BASE_KILLS.get();
        int inc = Config.GUN_LEVEL_INCREMENT.get();
        if (level <= 0) { cir.setReturnValue(0); return; }
        if (level > maxLevel) { cir.setReturnValue(Integer.MAX_VALUE); return; }
        int exp = (inc == 0) ? base * level : base + inc * level * (level - 1) / 2;
        cir.setReturnValue(exp);
    }

    @Inject(method = "getMaxLevel", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$getMaxLevel(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Config.GUN_LEVEL_MAX_LEVEL.get());
    }
}
