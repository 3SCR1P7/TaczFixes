package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.Config;
import com.ssscript.taczfixes.compat.ArcanaThermalState;
import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 Arcana 热成像瞄具视野内不渲染 LrTactical 烟雾弹的烟雾粒子。
 * SmokeCloudParticle.m_5744_ 为渲染方法（运行时 SRG 名）。
 */
@Mixin(targets = "me/xjqsh/lrtactical/client/particle/SmokeCloudParticle", remap = false)
public class MixinLrtSmokeParticleHide {

    @Inject(method = "m_5744_", at = @At("HEAD"), cancellable = true)
    private void taczfixes$hideSmokeInArcanaThermal(CallbackInfo ci) {
        if (!Config.HIDE_SMOKE_IN_ARCANA_THERMAL.get()) return;
        if (!ArcanaThermalState.isScopeViewActive()) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        IGunOperator gunOperator = IGunOperator.fromLivingEntity(player);
        if (gunOperator == null || gunOperator.getSynAimingProgress() <= 0) return;

        ci.cancel();
    }
}
