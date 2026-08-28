package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 跑射延迟(sprint_time)适配: LivingEntityAim.tickSprint 以 GunData.getSprintTime()
 * 作为冲刺开火冷却的上限, 配件的 taczfixes/sprint_time 修饰符按倍率缩放该值。
 * 与 reload_time/manual_action_time 同一模式: 各配件修饰符对基准值求值, 再乘回基准值。
 */
@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntityAim", remap = false)
public abstract class MixinLivingEntityAimSprintTime {

    @Shadow(remap = false)
    private LivingEntity shooter;

    @Redirect(method = "lambda$tickSprint$1",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/resource/pojo/data/gun/GunData;getSprintTime()F",
                    remap = false), remap = false)
    private float taczfixes$scaleSprintTime(GunData gunData) {
        float base = gunData.getSprintTime();
        ItemStack gun = GunEnchantmentHelper.getGunStack(this.shooter);
        return AttachmentTaczFixesManager.applySprintTime(gun, base);
    }
}
