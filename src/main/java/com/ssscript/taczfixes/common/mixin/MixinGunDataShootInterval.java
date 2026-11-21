package com.ssscript.taczfixes.common.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import com.ssscript.taczfixes.common.util.GunEnchantmentUtil;
import com.ssscript.taczfixes.common.util.GunEnchantments;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 射速附魔统一在 GunData#getShootInterval 的 rpm 源头乘因子。
 *
 * <p>为什么在源头而不是覆写 RETURN: Arcana 的 MixinGunData 会在 RETURN 处用捕获到的 rpm 重新算出
 * {@code 60000 / clamp(rpm * modifier)} 并无条件覆写返回值, 在 RETURN 上做手脚会被它整个冲掉;
 * 而这里乘进去的值会存进局部变量 rpm, Arcana 重算时也会带上本模组的因子。</p>
 *
 * <p>服务端 getShootCoolDown / 客户端 getCoolDown、getClientShootCoolDown / 动画 / 脚本 API
 * 都读这个方法, 因此一次修改全链路生效。</p>
 *
 * <ul>
 *   <li>效率: 每级 +config% 射速, 因子 &gt; 1;</li>
 *   <li>封焰时刻: 过热进度超过阈值后射速线性下降, 到 100% 过热时降到 config 指定的最低百分比。</li>
 * </ul>
 */
@Mixin(value = GunData.class, remap = false, priority = 900)
public abstract class MixinGunDataShootInterval {
    private static final String METHOD = "getShootInterval(Lnet/minecraft/world/entity/LivingEntity;"
            + "Lcom/tacz/guns/api/item/gun/FireMode;Lnet/minecraft/world/item/ItemStack;)J";

    /** rpm 来源一: 基础 rpm(无配件缓存时生效)。 */
    @ModifyExpressionValue(
            method = METHOD,
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/resource/pojo/data/gun/GunData;getRoundsPerMinute(Lcom/tacz/guns/api/item/gun/FireMode;)I"),
            require = 1, remap = false)
    private int taczfixes$scaleBaseRpm(int rpm, LivingEntity shooter, FireMode fireMode, ItemStack gunStack) {
        return taczfixes$scaleRpm(rpm, gunStack);
    }

    /** rpm 来源二: 配件缓存 rpm。有缓存时它会覆盖基础 rpm, 是实际生效值。 */
    @ModifyExpressionValue(
            method = METHOD,
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/resource/modifier/AttachmentCacheProperty;getCache(Ljava/lang/String;)Ljava/lang/Object;"),
            require = 1, remap = false)
    private Object taczfixes$scaleCacheRpm(Object cachedRpm, LivingEntity shooter, FireMode fireMode, ItemStack gunStack) {
        if (cachedRpm instanceof Integer rpm) {
            return Integer.valueOf(taczfixes$scaleRpm(rpm.intValue(), gunStack));
        }
        return cachedRpm;
    }

    @Unique
    private int taczfixes$scaleRpm(int rpm, ItemStack gunStack) {
        if (rpm <= 0 || gunStack == null || gunStack.isEmpty() || !GunEnchantmentHelper.isEnabled()) {
            return rpm;
        }
        float factor = GunEnchantmentHelper.getEfficiencyFireRateFactor(gunStack);
        factor *= taczfixes$sealFlareFireRateFactor(gunStack);
        if (Math.abs(factor - 1.0F) <= 0.0001F) {
            return rpm;
        }
        return Math.max(Math.round(rpm * factor), 1);
    }

    /** 封焰时刻: 过热超过阈值后射速线性下降(1.0 -> 最低射速百分比)。没附魔/没过热时返回 1。 */
    @Unique
    private float taczfixes$sealFlareFireRateFactor(ItemStack gunStack) {
        if (GunEnchantmentUtil.level(gunStack, GunEnchantments.SEAL_FLARE) <= 0) {
            return 1.0F;
        }
        float heat = GunEnchantmentUtil.heatProgress(gunStack);
        float threshold = (float) (Config.ENCH_SEAL_FLARE_HEAT_THRESHOLD_PERCENT.get() / 100.0D);
        if (heat <= threshold) {
            return 1.0F;
        }
        float minRate = (float) (Config.ENCH_SEAL_FLARE_MIN_FIRE_RATE_PERCENT.get() / 100.0D);
        float range = Math.max(1.0F - threshold, 0.0001F);
        float t = Math.min((heat - threshold) / range, 1.0F);
        return 1.0F - t * (1.0F - Math.max(minRate, 0.01F));
    }
}
