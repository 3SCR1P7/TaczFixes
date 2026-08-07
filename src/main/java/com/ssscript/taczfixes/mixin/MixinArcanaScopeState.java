package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.compat.ArcanaThermalState;
import group.taczexpands.dist.binq9IpL;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 捕获 Arcana 瞄具视野状态：Scope 渲染通道开始时(SYqi1im0(true))，用
 * binq9IpL.PtqK81kG(true) 判定当前瞄具是否热成像（data 中 "thermal_imaging":
 * true），仅热成像瞄具视野记录为 active；渲染结束(SYqi1im0(false))恢复 false。
 */
@Mixin(targets = "group/taczexpands/dist/binq9IpL", remap = false)
public class MixinArcanaScopeState {

    @Inject(method = "SYqi1im0", at = @At("HEAD"))
    private void taczfixes$captureScopeViewActive(boolean active, CallbackInfo ci) {
        if (!active) {
            ArcanaThermalState.scopeViewActive = false;
            return;
        }
        ArcanaThermalState.scopeViewActive = ((binq9IpL) (Object) this).PtqK81kG(true);
    }
}
