package com.ssscript.taczfixes.common.mixin;

import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 突破扩容弹匣 3 级上限:
 * - 去掉 getMagExtendLevel 中的 Math.min(level, 3) 截断(独占配件与通用配件两条路径);
 * - 弹容量表 extended_mag_ammo_amount 不足时按最后一项封顶, 避免越界。
 */
@Mixin(AttachmentDataUtils.class)
public class MixinAttachmentDataUtilsExtendedMagLevel {

    @Redirect(method = "getMagExtendLevel", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"), remap = false)
    private static int taczfixes$noCapExclusive(int level, int cap) {
        return level;
    }

    @Redirect(method = "lambda$getMagExtendLevel$1", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"), remap = false)
    private static int taczfixes$noCapCommon(int level, int cap) {
        return level;
    }

    @Redirect(method = "getAmmoCountWithAttachment", at = @At(value = "INVOKE", target = "Lcom/tacz/guns/util/AttachmentDataUtils;getMagExtendLevel(Lnet/minecraft/world/item/ItemStack;Lcom/tacz/guns/resource/pojo/data/gun/GunData;)I"), remap = false)
    private static int taczfixes$clampLevelToAmmoTable(ItemStack gunItem, GunData gunData) {
        int level = AttachmentDataUtils.getMagExtendLevel(gunItem, gunData);
        int[] amounts = gunData.getExtendedMagAmmoAmount();
        if (amounts != null && level > amounts.length) {
            return amounts.length;
        }
        return level;
    }
}
