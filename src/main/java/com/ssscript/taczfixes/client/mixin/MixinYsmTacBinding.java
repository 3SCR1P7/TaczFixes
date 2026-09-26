package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.ssscript.taczfixes.common.compat.YsmDualWieldCompat;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = {"com.elfmcys.yesstevemodel.O0OoOo00OoOOoOo0oo0O00oo"}, remap = false)
public abstract class MixinYsmTacBinding {
    @Redirect(method = {"Oo0Oo0o00O00Oo0OOoOOoooo(Lcom/elfmcys/yesstevemodel/oo0oOO0000o0Ooooo0OoOo0O;)Ljava/lang/String;"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/resource/index/CommonGunIndex;getType()Ljava/lang/String;", remap = false), require = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = false)
    private static String taczDualWield$publishRpgType(CommonGunIndex gunIndex, @Coerce Object molangContext) {
        try {
            if (YsmDualWieldCompat.isDualWieldMolangContext(molangContext)) {
                return "rpg";
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return gunIndex.getType();
    }

    @Redirect(method = {"oooooooOOoOOoO00OooOo00O(Lcom/elfmcys/yesstevemodel/oo0oOO0000o0Ooooo0OoOo0O;)Ljava/lang/Object;"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/entity/IGunOperator;getSynShootCoolDown()J", remap = false), require = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = false)
    private static long taczDualWield$publishOffhandFireState(IGunOperator operator, @Coerce Object molangContext) {
        try {
            return Math.max(operator.getSynShootCoolDown(), YsmDualWieldCompat.getOffhandShootCoolDownForMolangContext(molangContext));
        } catch (ReflectiveOperationException ignored) {
            return operator.getSynShootCoolDown();
        }
    }
}
