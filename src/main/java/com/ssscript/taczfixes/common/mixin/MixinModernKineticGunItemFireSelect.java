package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.data.CustomFireModeManager;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.item.ModernKineticGunItem;
import com.tacz.guns.resource.pojo.data.gun.BurstData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 开火模式解锁/禁用 + 自定义开火模式:
 * 切换开火模式时, 循环范围使用配件调整后的模式集合, 并追加 taczfixes.fire_mode 自定义模式。
 * 自定义模式 id 存于 NBT; 核心模式仍存枚举名。
 */
@Mixin(ModernKineticGunItem.class)
public class MixinModernKineticGunItemFireSelect {

    @Unique
    public String taczfixes$fireSelectCustomId(ItemStack stack) {
        if (stack == null) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        String custom = tag.getString("TaczFixesCustomFireMode");
        return custom.isEmpty() ? null : custom;
    }

    @Unique
    public void taczfixes$fireSelectSetCustomId(ItemStack stack, String id) {
        if (stack == null) return;
        stack.getOrCreateTag().putString("TaczFixesCustomFireMode", id == null ? "" : id);
    }

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
            adjusted = original;
        }

        // 自定义模式列表
        ResourceLocation dataId = TaczFixesDataManager.resolveDataId(iGun.getGunId(gunItem));
        List<String> customIds = new ArrayList<>(CustomFireModeManager.ids(dataId));

        // 组装循环项
        List<String> items = new ArrayList<>();
        for (FireMode m : adjusted) {
            items.add(m.name().toLowerCase());
        }
        for (String id : customIds) {
            items.add(id);
        }
        if (items.isEmpty()) {
            return;
        }

        String currentId = taczfixes$fireSelectCustomId(gunItem);
        int idx;
        if (currentId != null) {
            idx = items.indexOf(currentId);
            if (idx < 0) idx = 0;
        } else {
            FireMode current = iGun.getFireMode(gunItem);
            idx = items.indexOf(current.name().toLowerCase());
            if (idx < 0) idx = 0;
        }
        String next = items.get((idx + 1) % items.size());
        if (CustomFireModeManager.hasCustomMode(dataId, next)) {
            taczfixes$fireSelectSetCustomId(gunItem, next);
            iGun.setFireMode(gunItem, FireMode.UNKNOWN);
        } else {
            taczfixes$fireSelectSetCustomId(gunItem, null);
            iGun.setFireMode(gunItem, FireMode.valueOf(next.toUpperCase()));
        }
        ci.cancel();
    }
}
