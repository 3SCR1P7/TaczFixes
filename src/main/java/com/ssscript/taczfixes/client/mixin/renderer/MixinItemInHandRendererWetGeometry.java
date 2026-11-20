package com.ssscript.taczfixes.client.mixin.renderer;

import com.ssscript.taczfixes.client.render.PbrRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第一人称手部渲染期间提交的 PBR 几何属于"手持物": 潮湿状态跟随玩家自己的淋雨/入水状态,
 * 而不是像世界里的方块/实体那样按各自表面接触到的雨水计算。
 * 标记在提交几何时打进 RenderType 变体(见 PbrRenderer.resolve), 因此即便加速渲染把
 * 实际绘制延后到帧末, 绘制时依然能取到正确的湿润参数。
 */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRendererWetGeometry {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void taczfixes$beginHandGeometry(CallbackInfo ci) {
        PbrRenderer.beginHandGeometry();
    }

    @Inject(method = "renderHandsWithItems", at = @At("RETURN"))
    private void taczfixes$endHandGeometry(CallbackInfo ci) {
        PbrRenderer.endHandGeometry();
    }
}
