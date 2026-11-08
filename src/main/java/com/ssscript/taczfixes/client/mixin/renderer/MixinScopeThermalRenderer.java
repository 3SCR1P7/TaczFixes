package com.ssscript.taczfixes.client.mixin.renderer;

import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.client.renderer.scope.ScopeThermalRenderer;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.index.ClientAttachmentVariantIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 自定义槽瞄具的热成像判定。
 *
 * <p>TaCZ 的 {@code ScopeThermalRenderer.hasThermalScope} 只读取原生 SCOPE 槽(或其原厂件),
 * 因此装在自定义槽里并处于激活状态的热成像瞄具不会生成热成像掩膜:
 * 热成像收集/渲染被关闭, {@code ScopeLensRenderer} 拿到的 ThermalReady 为 0,
 * scope_lens 着色器退回普通成像。自定义槽瞄具激活时按该瞄具自身(含 variant)的
 * vision_mode 覆盖判定, 使自定义槽与标准槽的热成像行为一致。</p>
 */
@Mixin(value = ScopeThermalRenderer.class, remap = false)
public class MixinScopeThermalRenderer {

    @Inject(method = "hasThermalScope", at = @At("RETURN"), cancellable = true, remap = false)
    private static void taczfixes$customSlotThermalScope(ItemStack gunItem, CallbackInfoReturnable<Boolean> cir) {
        if (gunItem == null || gunItem.isEmpty()) return;
        String active = ScopeSwitchState.getActiveSlot(gunItem);
        if (active == null) return;
        cir.setReturnValue(taczfixes$isThermalOptic(gunItem, active));
    }

    @Unique
    private static boolean taczfixes$isThermalOptic(ItemStack gun, String activeSlot) {
        ItemStack item = CustomSlotStorage.get(gun, activeSlot);
        if (item.isEmpty()) return false;
        IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
        if (attachment == null) return false;
        ResourceLocation id = attachment.getAttachmentId(item);
        if (id == null) return false;
        ClientAttachmentIndex index = TimelessAPI.getClientAttachmentIndex(id).orElse(null);
        if (index == null) return false;
        // 与原生 hasThermalScope 一致: overlay 型 variant 仍使用本体热成像配置, replace 型只看 variant。
        ClientAttachmentVariantIndex variant = TimelessAPI.getClientAttachmentVariantIndex(item).orElse(null);
        if (variant != null && variant.getAttachmentModel() != null && variant.getModelTexture() != null) {
            boolean variantThermal = variant.isScope() && variant.getScopeLens().getVisionMode().isThermal();
            if (variant.isReplaceMode()) {
                return variantThermal;
            }
            if (variantThermal) {
                return true;
            }
        }
        return index.isScope() && index.getScopeLens().getVisionMode().isThermal();
    }
}
