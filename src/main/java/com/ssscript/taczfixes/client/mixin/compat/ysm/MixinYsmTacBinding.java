package com.ssscript.taczfixes.client.mixin.compat.ysm;

import com.ssscript.taczfixes.common.compat.YsmDualWieldCompat;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.resource.index.CommonGunIndex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * YSM 的 TaCZ Molang 绑定 (1.21.1 NeoForge 构建: com.elfmcys.yesstevemodel.oo0OO0O00oO0oO0OooO0Oo00)。
 * <p>把双持时公开给动画控制器的 {@code tac_gun_type} 改成 rpg (共用可镜像的持枪姿势),
 * 并让 {@code tac_is_fire} 同时反映副手的开火冷却。
 */
@Pseudo
@Mixin(targets = {"com.elfmcys.yesstevemodel.oo0OO0O00oO0oO0OooO0Oo00"}, remap = false)
public abstract class MixinYsmTacBinding {
    @Redirect(
            method = {"oOo0OO0O0o000OO0O000oo0o(Lcom/elfmcys/yesstevemodel/O000o00000000OOoO0OooOO0;)Ljava/lang/String;"},
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/resource/index/CommonGunIndex;getType()Ljava/lang/String;", remap = false),
            require = ServerMessageOffhandActionResult.ACTION_SHOOT,
            remap = false)
    private static String taczDualWield$publishRpgType(CommonGunIndex gunIndex, @Coerce Object molangContext) {
        try {
            if (YsmDualWieldCompat.isDualWieldMolangContext(molangContext)) {
                return "rpg";
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return gunIndex.getType();
    }

    @Redirect(
            method = {"oOOO00ooO0oOOoOOo0OoOOOo(Lcom/elfmcys/yesstevemodel/O000o00000000OOoO0OooOO0;)Ljava/lang/Object;"},
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/entity/IGunOperator;getSynShootCoolDown()J", remap = false),
            require = ServerMessageOffhandActionResult.ACTION_SHOOT,
            remap = false)
    private static long taczDualWield$publishOffhandFireState(IGunOperator operator, @Coerce Object molangContext) {
        try {
            return Math.max(operator.getSynShootCoolDown(), YsmDualWieldCompat.getOffhandShootCoolDownForMolangContext(molangContext));
        } catch (ReflectiveOperationException ignored) {
            return operator.getSynShootCoolDown();
        }
    }
}
