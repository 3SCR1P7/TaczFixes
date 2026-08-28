package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.item.ModernKineticGunItem;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 开火模式解锁/禁用: 切换开火模式时, 循环范围使用
 * 配件 fire_mode_enable/fire_mode_disable 调整后的模式集合。
 * 若当前模式已被禁用, 则切换到原集合顺序中的下一个可用模式。
 */
@Mixin(ModernKineticGunItem.class)
public class MixinModernKineticGunItemFireSelect {

    @Inject(method = "fireSelect", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$adjustedFireModeCycle(ShooterDataHolder dataHolder, ItemStack gunItem, CallbackInfo ci) {
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) {
            return;
        }
        GunData gunData = TimelessAPI.getCommonGunIndex(iGun.getGunId(gunItem))
                .map(index -> index.getGunData())
                .orElse(null);
        if (gunData == null) {
            return;
        }
        List<FireMode> original = gunData.getFireModeSet();
        List<FireMode> adjusted = AttachmentTaczFixesManager.adjustFireModeSet(gunItem, original);
        if (adjusted == null || adjusted.isEmpty()) {
            return;
        }
        FireMode current = iGun.getFireMode(gunItem);
        FireMode next = taczfixes$nextFireMode(original, adjusted, current);
        if (next != null && next != current) {
            iGun.setFireMode(gunItem, next);
        }
        ci.cancel();
    }

    @Nullable
    private static FireMode taczfixes$nextFireMode(List<FireMode> original, List<FireMode> adjusted, FireMode current) {
        if (adjusted.isEmpty()) {
            return null;
        }
        int idx = adjusted.indexOf(current);
        if (idx >= 0) {
            return adjusted.get((idx + 1) % adjusted.size());
        }
        // 当前模式被禁用: 从原集合顺序中找当前之后的第一个可用模式
        int curIdx = original.indexOf(current);
        if (curIdx >= 0) {
            for (int step = 1; step <= original.size(); step++) {
                FireMode candidate = original.get((curIdx + step) % original.size());
                if (adjusted.contains(candidate)) {
                    return candidate;
                }
            }
        }
        return adjusted.get(0);
    }
}
