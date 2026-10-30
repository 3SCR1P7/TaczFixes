package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.util.LimbDamageFactorHelper;
import com.tacz.guns.client.tooltip.ClientGunTooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.text.DecimalFormat;

@Mixin(value = ClientGunTooltip.class, remap = false)
public class MixinClientGunTooltipLimbDamage {
    @Unique
    private static final DecimalFormat TACZFIXES_LIMB_FORMAT = new DecimalFormat("#.##%");

    @Shadow(remap = false)
    @Final
    private ItemStack gun;

    @Shadow(remap = false)
    private MutableComponent weight;

    @Shadow(remap = false)
    private MutableComponent tips;

    @Shadow(remap = false)
    private MutableComponent packInfo;

    @Shadow(remap = false)
    private int maxWidth;

    @Unique
    private Component taczfixes$limbDamageLine;

    @Inject(method = "getText", at = @At("TAIL"), remap = false)
    private void taczfixes$buildLimbDamageLine(CallbackInfo ci) {
        if (!Config.LIMB_DAMAGE_SHOW_IN_CHARTS.get() || weight == null || gun == null) {
            return;
        }
        float factor = LimbDamageFactorHelper.getFinalFactor(gun);
        taczfixes$limbDamageLine = Component.translatable("tooltip.taczfixes.limb_damage",
                TACZFIXES_LIMB_FORMAT.format(factor));
        maxWidth = Math.max(maxWidth, Minecraft.getInstance().font.width(taczfixes$limbDamageLine));
    }

    @Inject(method = "m_142103_", at = @At("RETURN"), cancellable = true, remap = false)
    private void taczfixes$addLimbDamageHeight(CallbackInfoReturnable<Integer> cir) {
        if (taczfixes$limbDamageLine != null) {
            cir.setReturnValue(cir.getReturnValue() + 10);
        }
    }

    @Redirect(method = "m_142440_",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;m_272077_(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I",
                    remap = false),
            remap = false)
    private int taczfixes$drawLimbDamageLine(Font font, Component text, float x, float y, int color,
                                             boolean dropShadow, Matrix4f matrix, MultiBufferSource buffer,
                                             Font.DisplayMode displayMode, int backgroundColor, int packedLight) {
        if (taczfixes$limbDamageLine != null) {
            if (text == weight) {
                font.drawInBatch(taczfixes$limbDamageLine, x, y, 0xFFAA00,
                        false, matrix, buffer, displayMode, backgroundColor, packedLight);
                return font.drawInBatch(text, x, y + 10.0F, color,
                        dropShadow, matrix, buffer, displayMode, backgroundColor, packedLight);
            }
            if (text == tips || text == packInfo) {
                return font.drawInBatch(text, x, y + 10.0F, color,
                        dropShadow, matrix, buffer, displayMode, backgroundColor, packedLight);
            }
        }
        return font.drawInBatch(text, x, y, color,
                dropShadow, matrix, buffer, displayMode, backgroundColor, packedLight);
    }
}
