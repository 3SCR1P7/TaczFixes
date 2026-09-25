package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.util.PausableClock;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 暂停时冻结动画状态机退出判定所用的时间(墙钟改为可暂停时间)。 */
@Mixin(value = AnimateGeoItemRenderer.class, remap = false)
public class MixinAnimateGeoItemRendererPause {

    @Redirect(method = "needReInit", at = @At(value = "INVOKE", target = "Ljava/lang/System;currentTimeMillis()J"), remap = false)
    private long taczfixes$pausableNow() {
        return PausableClock.millis();
    }
}
