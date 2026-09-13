package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.CustomFireModeManager;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.item.ModernKineticGunItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 自定义开火模式: 当前模式为自定义 id 时, getFireMode 按其 type 返回 TACZ 核心枚举。 */
@Mixin(ModernKineticGunItem.class)
public class MixinGunItemDataAccessor {

    // 注意: 此方法与 MixinModernKineticGunItemFireSelect 中同名 @Unique 实现一致,
    // 运行时其中一个会被 mixin 丢弃, 功能不受影响。

    @Unique
    public String taczfixes$getCustomFireModeId(ItemStack stack) {
        if (stack == null) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        String custom = tag.getString("TaczFixesCustomFireMode");
        return custom.isEmpty() ? null : custom;
    }

    @Unique
    public void taczfixes$setCustomFireModeId(ItemStack stack, String id) {
        if (stack == null) return;
        stack.getOrCreateTag().putString("TaczFixesCustomFireMode", id == null ? "" : id);
    }

    /** 覆盖接口 default: 自定义 id 存在时按其 type 映射为核心枚举; 否则原逻辑。 */
    @Unique
    public FireMode getFireMode(ItemStack stack) {
        String id = taczfixes$getCustomFireModeId(stack);
        if (id != null) {
            String type = resolveCustomType(stack, id);
            if (type != null) {
                FireMode result = switch (type) {
                    case "auto" -> FireMode.AUTO;
                    case "semi" -> FireMode.SEMI;
                    case "burst" -> FireMode.BURST;
                    default -> FireMode.UNKNOWN;
                };
                return result;
            }
        }
        CompoundTag tag = stack == null ? null : stack.getTag();
        if (tag == null || !tag.contains("GunFireMode", 8)) {
            return FireMode.UNKNOWN;
        }
        try {
            return FireMode.valueOf(tag.getString("GunFireMode"));
        } catch (Exception ex) {
            return FireMode.UNKNOWN;
        }
    }

    /** 覆盖接口 default: 存核心枚举名(自定义 id 单独存)。 */
    @Unique
    public void setFireMode(ItemStack stack, FireMode mode) {
        if (stack == null) return;
        stack.getOrCreateTag().putString("GunFireMode", mode == null ? "UNKNOWN" : mode.name());
    }

    @Unique
    private String resolveCustomType(ItemStack stack, String id) {
        IGun gun = stack == null ? null : IGun.getIGunOrNull(stack);
        if (gun == null) return null;
        ResourceLocation gunId = gun.getGunId(stack);
        if (gunId == null) return null;
        ResourceLocation dataId = TaczFixesDataManager.resolveDataId(gunId);
        var mode = CustomFireModeManager.mode(dataId, id);
        return mode == null ? null : mode.type;
    }
}
