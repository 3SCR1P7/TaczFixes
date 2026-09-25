package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** 水下开火限制。玩家眼部浸入水中时按配置与枪械 data 判定是否禁止开火。 */
public final class UnderwaterShooting {

    private UnderwaterShooting() {
    }

    /**
     * true 表示当前禁止开火:
     * 眼部不在水中返回 false; 枪械 data 的 allow_shooting_underwater 优先(true 允许/false 禁止),
     * 未配置时跟随配置文件 prevent_shooting_underwater。
     */
    public static boolean isBlocked(LivingEntity shooter, ItemStack gunStack) {
        if (shooter == null || gunStack == null || gunStack.isEmpty()) {
            return false;
        }
        if (!shooter.isEyeInFluid(FluidTags.WATER)) {
            return false;
        }
        Boolean gunAllow = TaczFixesDataManager.resolveAllowShootingUnderwater(gunStack);
        if (gunAllow != null) {
            return !gunAllow;
        }
        return Config.PREVENT_SHOOTING_UNDERWATER.get();
    }
}
