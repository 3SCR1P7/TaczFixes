package com.ssscript.taczfixes.common.util;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.pojo.data.attachment.MeleeData;
import com.tacz.guns.resource.pojo.data.gun.GunDefaultMeleeData;
import com.tacz.guns.resource.pojo.data.gun.GunMeleeData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** 枪械近战总冷却(毫秒) = 枪械近战冷却 + 对应近战数据冷却(枪口/枪托/默认, 与服务端判定一致)。 */
public final class MeleeCooldownHelper {

    private MeleeCooldownHelper() {
    }

    public static long totalCooldownMillis(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return 0L;
        }
        GunMeleeData melee = gunMeleeData(gun, stack);
        float seconds = melee == null ? 0.0f : melee.getCooldown();
        MeleeData muzzle = attachmentMeleeData(gun.getAttachmentId(stack, AttachmentType.MUZZLE));
        if (muzzle != null) {
            seconds += muzzle.getCooldown();
        } else {
            MeleeData stock = attachmentMeleeData(gun.getAttachmentId(stack, AttachmentType.STOCK));
            if (stock != null) {
                seconds += stock.getCooldown();
            } else {
                GunDefaultMeleeData defaultMelee = melee == null ? null : melee.getDefaultMeleeData();
                if (defaultMelee == null) {
                    return 0L;
                }
                seconds += defaultMelee.getCooldown();
            }
        }
        return Math.max(0L, (long) (seconds * 1000.0f));
    }

    private static GunMeleeData gunMeleeData(IGun gun, ItemStack stack) {
        return TimelessAPI.getCommonGunIndex(gun.getGunId(stack))
                .map(index -> index.getGunData().getMeleeData())
                .orElse(null);
    }

    private static MeleeData attachmentMeleeData(ResourceLocation attachmentId) {
        if (attachmentId == null || DefaultAssets.isEmptyAttachmentId(attachmentId)) {
            return null;
        }
        return TimelessAPI.getCommonAttachmentIndex(attachmentId)
                .map(index -> index.getData().getMeleeData())
                .orElse(null);
    }
}
