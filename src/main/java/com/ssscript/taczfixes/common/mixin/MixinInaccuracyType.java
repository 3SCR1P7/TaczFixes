package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.register.Config;
import com.ssscript.taczfixes.common.util.ParCoolHelper;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InaccuracyType.class)
public class MixinInaccuracyType {
    @Inject(method = "getInaccuracyType", at = @At("RETURN"), cancellable = true, remap = false)
    private static void taczfixes$parcoolSlideAsMove(LivingEntity entity, CallbackInfoReturnable<InaccuracyType> cir) {
        if (!Config.PARCOOL_SLIDE_AS_MOVE_INACCURACY.get()) return;
        if (cir.getReturnValue() != InaccuracyType.LIE) return;
        if (ParCoolHelper.isCrawling(entity)) {
            cir.setReturnValue(InaccuracyType.MOVE);
        }
    }
}
