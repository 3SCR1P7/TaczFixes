package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModernKineticGunScriptAPI.class)
public class MixinGunScriptAPIGetBoltTime {
    @Shadow(remap = false)
    private LivingEntity shooter;

    @Inject(method = "getBoltTime", at = @At("RETURN"), cancellable = true, remap = false)
    private void taczfixes$shortenBoltTime(CallbackInfoReturnable<Long> cir) {
        long elapsed = cir.getReturnValue();
        if (elapsed <= 0) {
            return;
        }
        ItemStack gun = GunEnchantmentHelper.getGunStack(shooter);
        // 拉栓时间倍率(配件 data manual_action_time 对基准值 1 求值)
        double divisor = AttachmentTaczFixesManager.getManualActionTimeFactor(gun);
        if (divisor <= 0.0d) {
            // 时长被减为 0 或以下: 返回极大流逝值, 状态机立即判定拉栓完成(不等待)
            cir.setReturnValue(Long.MAX_VALUE);
            return;
        }
        // 效率附魔与配件一同生效
        if (GunEnchantmentHelper.isEnabled()) {
            divisor *= GunEnchantmentHelper.getEfficiencyBoltTimeFactor(gun);
        }
        if (divisor == 1.0d) {
            return;
        }
        // getBoltTime 返回已流逝时间, 除以时间倍率即放大流逝值, 使动作提前完成
        cir.setReturnValue(Math.max(0L, (long) (elapsed / divisor)));
    }
}
