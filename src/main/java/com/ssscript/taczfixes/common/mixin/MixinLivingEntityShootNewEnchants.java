package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.handler.NewGunEnchantmentHandler;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.entity.shooter.LivingEntityShoot;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

/** 被常凪冻结的目标无法开火。封焰时刻的射速变化在 GunData#getShootInterval 上处理(与"效率"附魔同一处)。 */
@Mixin(LivingEntityShoot.class)
public class MixinLivingEntityShootNewEnchants {
    @Shadow(remap = false)
    private ShooterDataHolder data;

    @Shadow(remap = false)
    private LivingEntity shooter;

    @Inject(method = "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)Lcom/tacz/guns/api/entity/ShootResult;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$blockFrozenShoot(Supplier<Float> pitch, Supplier<Float> yaw, long timestamp,
                                            float chargeProgress, boolean needConsumeAmmo,
                                            CallbackInfoReturnable<ShootResult> cir) {
        if (this.shooter != null && NewGunEnchantmentHandler.isFrozen(this.shooter)) {
            cir.setReturnValue(ShootResult.COOL_DOWN);
        }
    }
}
