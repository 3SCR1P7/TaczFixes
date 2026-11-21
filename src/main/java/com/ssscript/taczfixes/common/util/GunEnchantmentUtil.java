package com.ssscript.taczfixes.common.util;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunHeatData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegistryObject;

/** 新增附魔共用的枪械状态查询: 附魔等级、过热进度、蓄力能力/状态。 */
public final class GunEnchantmentUtil {
    private GunEnchantmentUtil() {
    }

    public static int level(ItemStack gun, RegistryObject<Enchantment> enchantment) {
        if (gun.isEmpty() || enchantment == null) {
            return 0;
        }
        return GunEnchantmentHelper.getLevel(gun, enchantment.get());
    }

    public static GunData gunData(ItemStack gun) {
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            return null;
        }
        ResourceLocation gunId = iGun.getGunId(gun);
        if (gunId == null) {
            return null;
        }
        return TimelessAPI.getCommonGunIndex(gunId).map(index -> index.getGunData()).orElse(null);
    }

    /** 枪械过热进度 0~1; 没有过热数据时返回 0。 */
    public static float heatProgress(ItemStack gun) {
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null || !iGun.hasHeatData(gun)) {
            return 0.0F;
        }
        GunData gunData = gunData(gun);
        GunHeatData heatData = gunData == null ? null : gunData.getHeatData();
        if (heatData == null || heatData.getHeatMax() <= 0.0F) {
            return 0.0F;
        }
        return Mth.clamp(iGun.getHeatAmount(gun) / heatData.getHeatMax(), 0.0F, 1.0F);
    }

    /** 枪械是否有过热功能。 */
    public static boolean hasHeatFeature(ItemStack gun) {
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null || !iGun.hasHeatData(gun)) {
            return false;
        }
        GunData gunData = gunData(gun);
        GunHeatData heatData = gunData == null ? null : gunData.getHeatData();
        return heatData != null && heatData.getHeatMax() > 0.0F;
    }

    /** 枪械是否有蓄力功能(任一开火模式下配置了蓄力数据)。 */
    public static boolean hasChargeFeature(ItemStack gun) {
        GunData gunData = gunData(gun);
        if (gunData == null) {
            return false;
        }
        for (FireMode fireMode : FireMode.values()) {
            if (gunData.getChargeData(fireMode) != null) {
                return true;
            }
        }
        return false;
    }

    /** 手持该枪的射手当前是否正在蓄力。 */
    public static boolean isCharging(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        // 服务端无法直接读到客户端蓄力状态, 使用客户端同步过来的按键/蓄力标记。
        if (entity instanceof net.minecraft.server.level.ServerPlayer player
                && PlayerInputState.isCharging(player.getUUID())) {
            return true;
        }
        try {
            IGunOperator operator = IGunOperator.fromLivingEntity(entity);
            if (operator == null) {
                return false;
            }
            ShooterDataHolder holder = operator.getDataHolder();
            return holder != null && holder.chargeProgress > 0.0F;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
