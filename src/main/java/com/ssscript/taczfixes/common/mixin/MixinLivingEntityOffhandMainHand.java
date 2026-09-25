package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.OffhandShooterManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TaCZ: Arcana 技能桥接:
 * 副手处理上下文内把 getMainHandItem() 解析为副手枪械, 使 Arcana 的 YAML 技能匹配、
 * 变量 (getMainHandNBT 等)、条件与动作在副手开火/换弹/拉栓/近战时作用于副手枪械。
 */
@Mixin(LivingEntity.class)
public class MixinLivingEntityOffhandMainHand {

    /**
     * 重入保护: getActiveOffhandGunStack -> isCurrentOffhandContext -> DualWieldEligibility.isDualWielding
     * 内部会再次调用 getMainHandItem(), 不加保护会无限递归 (StackOverflowError) 导致副手无法开火。
     */
    private static final ThreadLocal<Boolean> RESOLVING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "getMainHandItem", at = @At("HEAD"), cancellable = true)
    private void taczfixes$useOffhandAsMainHand(CallbackInfoReturnable<ItemStack> cir) {
        if (Boolean.TRUE.equals(RESOLVING.get())) {
            return;
        }
        RESOLVING.set(Boolean.TRUE);
        try {
            ItemStack offhand = OffhandShooterManager.getActiveOffhandGunStack((LivingEntity) (Object) this);
            if (!offhand.isEmpty()) {
                cir.setReturnValue(offhand);
            }
        } finally {
            RESOLVING.set(Boolean.FALSE);
        }
    }
}
