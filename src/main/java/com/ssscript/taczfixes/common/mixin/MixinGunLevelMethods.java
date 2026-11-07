package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.util.GunLevelFormula;
import com.tacz.guns.item.ModernKineticGunItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 用可配置的 Lua 风格经验公式(gun_level.xp_formula)替换原版“击杀数/每级增量”等级算法。
 * xp_formula 的 y(x) 表示“从 0 级升到 x 级所需的累计枪械经验值”。
 */
@Mixin(ModernKineticGunItem.class)
public class MixinGunLevelMethods {
    @Inject(method = "getLevel", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$getLevel(int exp, CallbackInfoReturnable<Integer> cir) {
        if (exp <= 0) {
            cir.setReturnValue(0);
            return;
        }
        cir.setReturnValue(GunLevelFormula.levelForXp(exp));
    }

    @Inject(method = "getExp", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$getExp(int level, CallbackInfoReturnable<Integer> cir) {
        if (level <= 0) {
            cir.setReturnValue(0);
            return;
        }
        if (level > Config.GUN_LEVEL_MAX_LEVEL.get()) {
            cir.setReturnValue(Integer.MAX_VALUE);
            return;
        }
        cir.setReturnValue(GunLevelFormula.xpForLevel(level));
    }

    @Inject(method = "getMaxLevel", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$getMaxLevel(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Config.GUN_LEVEL_MAX_LEVEL.get());
    }
}
