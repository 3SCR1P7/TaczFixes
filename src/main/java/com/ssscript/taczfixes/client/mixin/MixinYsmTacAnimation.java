package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.resource.index.CommonGunIndex;
import com.ssscript.taczfixes.common.compat.YsmCompatibilityDiagnostics;
import com.ssscript.taczfixes.common.compat.YsmDualWieldCompat;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.tacz.guns.api.entity.IGunOperator;

@Pseudo
@Mixin(targets = {"com.elfmcys.yesstevemodel.ooOoOo0o0OO0ooOO00Oo0ooO"}, remap = false)
public abstract class MixinYsmTacAnimation {
    private static final String RPG_TYPE = "rpg";
    private static final String[] TAC_GUN_ANIMATION_PREFIXES = {"tac:climbing:fire:", "tac:hold:fire:", "tac:aim:fire:", "tac:climbing:", "tac:reload:", "tac:climb:", "tac:hold:", "tac:aim:", "tac:run:"};

    @Redirect(method = {"Oo0Oo0o00O00Oo0OOoOOoooo(Lcom/elfmcys/yesstevemodel/OO00O0o0OooOOOo00OO00o00;Lnet/minecraft/world/item/ItemStack;)Lcom/elfmcys/yesstevemodel/O0oOo0OoO0O0o0000o0O00o0;"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/resource/index/CommonGunIndex;getType()Ljava/lang/String;", remap = false), require = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = false)
    private static String taczDualWield$selectRpgType(CommonGunIndex gunIndex, @Coerce Object animationEvent, ItemStack stack) {
        try {
            if (YsmDualWieldCompat.isDualWieldAnimationEvent(animationEvent)) {
                return RPG_TYPE;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return gunIndex.getType();
    }

    @ModifyVariable(method = {"o0OOooo0o0OO00OoOOOo0o0O(Lcom/elfmcys/yesstevemodel/OO00O0o0OooOOOo00OO00o00;Ljava/lang/String;Lcom/elfmcys/yesstevemodel/OooOO0OOOoO0oOO0OOOO0Ooo;)Lcom/elfmcys/yesstevemodel/O0oOo0OoO0O0o0000o0O00o0;"}, at = @At("HEAD"), argsOnly = true, ordinal = ServerMessageOffhandActionResult.ACTION_SHOOT, require = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = false)
    private static String taczDualWield$forceSelectedAnimationToRpg(String animationName, @Coerce Object animationEvent) {
        try {
            if (!YsmDualWieldCompat.isDualWieldAnimationEvent(animationEvent)) {
                return animationName;
            }
        } catch (ReflectiveOperationException ignored) {
            return animationName;
        }
        for (String prefix : TAC_GUN_ANIMATION_PREFIXES) {
            String idSpecificPrefix = prefix.substring(0, prefix.length() - 1) + "$";
            if (animationName.startsWith(prefix) || animationName.startsWith(idSpecificPrefix)) {
                YsmCompatibilityDiagnostics.markRpgPoseSelected();
                return prefix + "rpg";
            }
        }
        return animationName;
    }

    @Redirect(method = {"o0OOooo0o0OO00OoOOOo0o0O(Lcom/elfmcys/yesstevemodel/OO00O0o0OooOOOo00OO00o00;Lnet/minecraft/world/item/ItemStack;)Lcom/elfmcys/yesstevemodel/O0oOo0OoO0O0o0000o0O00o0;"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/entity/IGunOperator;getSynShootCoolDown()J", remap = false), require = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = false)
    private static long taczDualWield$includeOffhandShootCoolDown(IGunOperator operator, @Coerce Object animationEvent, ItemStack stack) {
        try {
            return Math.max(operator.getSynShootCoolDown(), YsmDualWieldCompat.getOffhandShootCoolDownForAnimationEvent(animationEvent));
        } catch (ReflectiveOperationException ignored) {
            return operator.getSynShootCoolDown();
        }
    }
}
