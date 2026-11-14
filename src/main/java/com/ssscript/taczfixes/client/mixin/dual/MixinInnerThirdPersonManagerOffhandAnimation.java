package com.ssscript.taczfixes.client.mixin.dual;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.third.InnerThirdPersonManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 副手为枪械、主手为物品时, TaCZ 的第三人称持枪动画只读取主手物品, 因此不会播放任何持枪动画。
 * 这里在动画取枪械显示时回退到副手枪械, 使副手枪在第三人称也有持枪动画。
 */
@Mixin(value = InnerThirdPersonManager.class, remap = false)
public class MixinInnerThirdPersonManagerOffhandAnimation {

    @Redirect(method = "setRotationAnglesHead",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getMainHandItem()Lnet/minecraft/world/item/ItemStack;",
                    remap = true),
            remap = false)
    private static ItemStack taczfixes$offhandGunAnimation(LivingEntity entity) {
        ItemStack main = entity.getMainHandItem();
        if (IGun.getIGunOrNull(main) == null) {
            ItemStack offhand = entity.getOffhandItem();
            if (IGun.getIGunOrNull(offhand) != null) {
                return offhand;
            }
        }
        return main;
    }
}
