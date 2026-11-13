package com.ssscript.taczfixes.client.mixin.compat;

import com.ssscript.taczfixes.client.client.DualWieldAnimatorContext;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings({"target", "mapping", "public-target", "unresolvable-target", "visibility", "constraints", "overwrite", "default-package", "raw-types"})
@Mixin(targets = {"com.tacz.guns.compat.playeranimator.animation.AdjustmentYRotModifier"}, remap = false)
/* player_animator 的手臂俯仰修正会读取 GunDisplayInstance#is3rdFixedHand, 这里补上双持上下文。 */
public abstract class MixinPlayerAnimatorAdjustment {

    @Shadow(remap = false)
    @Final
    private Player player;

    @Inject(method = {"apply(Ljava/lang/String;)Ljava/util/Optional;"}, at = {@At("HEAD")}, remap = false)
    private void taczfixes$beginDualWieldContext(String partName, CallbackInfoReturnable<Optional> callback) {
        DualWieldAnimatorContext.begin(DualWieldEligibility.isDualWielding(this.player));
    }

    @Inject(method = {"apply(Ljava/lang/String;)Ljava/util/Optional;"}, at = {@At("RETURN")}, remap = false)
    private void taczfixes$endDualWieldContext(String partName, CallbackInfoReturnable<Optional> callback) {
        DualWieldAnimatorContext.end();
    }
}
