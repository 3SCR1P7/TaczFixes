package com.ssscript.taczfixes.client.mixin.compat.ysm;

import com.ssscript.taczfixes.common.compat.YsmCompatibilityDiagnostics;
import com.ssscript.taczfixes.common.compat.YsmDualWieldCompat;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * YSM 的 TaCZ 动画选择桥 (1.21.1 NeoForge 构建: com.elfmcys.yesstevemodel.O0o0ooOoo00o0000OO0Oo0oo)。
 * <p>双持时强制走 RPG 姿势 (rpg 动画共用右手持枪姿势, 可被左臂镜像复用), 并把副手的开火冷却并入
 * {@code tac_is_fire} 的判断, 让副手开火也能触发 YSM 的 tac:*:fire 动画。
 */
@Pseudo
@Mixin(targets = {"com.elfmcys.yesstevemodel.O0o0ooOoo00o0000OO0Oo0oo"}, remap = false)
public abstract class MixinYsmTacAnimation {
    private static final String RPG_TYPE = "rpg";
    private static final String[] TAC_GUN_ANIMATION_PREFIXES = {
            "tac:climbing:fire:", "tac:hold:fire:", "tac:aim:fire:",
            "tac:climbing:", "tac:reload:", "tac:climb:", "tac:hold:", "tac:aim:", "tac:run:"};

    @Redirect(
            method = {"oOo0OO0O0o000OO0O000oo0o(Lcom/elfmcys/yesstevemodel/Oo0Oo0O0OoOoO0oooO00O0o0;Lnet/minecraft/world/item/ItemStack;)Lcom/elfmcys/yesstevemodel/O0o0ooO0oOoOoo0O000O0000;"},
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/resource/index/CommonGunIndex;getType()Ljava/lang/String;", remap = false),
            require = ServerMessageOffhandActionResult.ACTION_SHOOT,
            remap = false)
    private static String taczDualWield$selectRpgType(CommonGunIndex gunIndex, @Coerce Object animationEvent, ItemStack stack) {
        try {
            if (YsmDualWieldCompat.isDualWieldAnimationEvent(animationEvent)) {
                return RPG_TYPE;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return gunIndex.getType();
    }

    @ModifyVariable(
            method = {"oOo0OO0O0o000OO0O000oo0o(Lcom/elfmcys/yesstevemodel/Oo0Oo0O0OoOoO0oooO00O0o0;Ljava/lang/String;Lcom/elfmcys/yesstevemodel/ooOoOOooOoooOoooo0oO0Oo0;)Lcom/elfmcys/yesstevemodel/O0o0ooO0oOoOoo0O000O0000;"},
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = ServerMessageOffhandActionResult.ACTION_SHOOT,
            require = ServerMessageOffhandActionResult.ACTION_SHOOT,
            remap = false)
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

    @Redirect(
            method = {"oOoo00O0o0oO0o0oO00OO0O0(Lcom/elfmcys/yesstevemodel/Oo0Oo0O0OoOoO0oooO00O0o0;Lnet/minecraft/world/item/ItemStack;)Lcom/elfmcys/yesstevemodel/O0o0ooO0oOoOoo0O000O0000;"},
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/entity/IGunOperator;getSynShootCoolDown()J", remap = false),
            require = ServerMessageOffhandActionResult.ACTION_SHOOT,
            remap = false)
    private static long taczDualWield$includeOffhandShootCoolDown(IGunOperator operator, @Coerce Object animationEvent, ItemStack stack) {
        try {
            return Math.max(operator.getSynShootCoolDown(), YsmDualWieldCompat.getOffhandShootCoolDownForAnimationEvent(animationEvent));
        } catch (ReflectiveOperationException ignored) {
            return operator.getSynShootCoolDown();
        }
    }
}
