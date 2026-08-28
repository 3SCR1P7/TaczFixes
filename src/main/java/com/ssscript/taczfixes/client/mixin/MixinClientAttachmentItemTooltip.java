package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.data.AttachmentTaczFixesData;
import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.tooltip.ClientAttachmentItemTooltip;
import com.tacz.guns.resource.pojo.data.attachment.Modifier;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Mixin(value = ClientAttachmentItemTooltip.class, remap = false)
public class MixinClientAttachmentItemTooltip {
    private static final Style GOOD = Style.EMPTY.withColor(ChatFormatting.GREEN);
    private static final Style BAD = Style.EMPTY.withColor(ChatFormatting.RED);

    @Shadow
    @Final
    private ResourceLocation attachmentId;

    @Shadow
    @Final
    private List<Component> components;

    @Inject(method = "addText", at = @At("RETURN"), remap = false)
    private void taczfixes$appendTaczFixesLines(AttachmentType type, CallbackInfo ci) {
        if (attachmentId == null) return;
        AttachmentTaczFixesData data = AttachmentTaczFixesManager.resolveData(attachmentId);
        if (data == null) return;
        appendIncreaseIsBad(data.friction, "taczfixes.tooltip.friction");
        appendIncreaseIsBad(data.gravity, "taczfixes.tooltip.gravity");
        appendIncreaseIsBad(data.sprint_time, "taczfixes.tooltip.sprint_time");
        appendIncreaseIsBad(data.reload_time, "taczfixes.tooltip.reload_time");
        appendIncreaseIsBad(data.manual_action_time, "taczfixes.tooltip.manual_action_time");
        appendIncreaseIsBad(data.jump_inaccuracy == null ? null : data.jump_inaccuracy.multiplier,
                "taczfixes.tooltip.jump_inaccuracy");
        appendIncreaseIsGood(data.ammo_amount, "taczfixes.tooltip.ammo_amount");
        appendIncreaseIsGood(data.limb_factor, "taczfixes.tooltip.limb_factor");
        appendFireModes(data.fire_mode_enable, "taczfixes.tooltip.fire_mode_enable", GOOD);
        appendFireModes(data.fire_mode_disable, "taczfixes.tooltip.fire_mode_disable", BAD);
    }

    /** 数值增大为负面: 增大显示红色(+), 减小显示绿色(-)。 */
    private void appendIncreaseIsBad(Modifier modifier, String labelKey) {
        if (modifier == null) return;
        double factor = AttachmentPropertyManager.eval(List.of(modifier), 1.0);
        if (Math.abs(factor - 1.0) < 0.0001) return;
        addEffectLine(labelKey, factor > 1.0 ? "+" : "-", factor > 1.0 ? BAD : GOOD);
    }

    /** 弹容量: 增大显示绿色(+), 减小显示红色(-)。 */
    private void appendIncreaseIsGood(Modifier modifier, String labelKey) {
        if (modifier == null) return;
        double factor = AttachmentPropertyManager.eval(List.of(modifier), 1.0);
        if (Math.abs(factor - 1.0) < 0.0001) return;
        addEffectLine(labelKey, factor > 1.0 ? "+" : "-", factor > 1.0 ? GOOD : BAD);
    }

    private void addEffectLine(String labelKey, String sign, Style style) {
        components.add(Component.literal(sign + " ").withStyle(style)
                .append(Component.translatable(labelKey).withStyle(style)));
    }

    private void appendFireModes(List<String> modes, String labelKey, Style style) {
        if (modes == null || modes.isEmpty()) return;
        List<String> names = new ArrayList<>();
        for (String mode : modes) {
            String name = formatFireMode(mode);
            if (name != null) {
                names.add(name);
            }
        }
        if (names.isEmpty()) return;
        for (String name : names) {
            components.add(Component.literal("+ ").withStyle(style)
                    .append(Component.translatable(labelKey).withStyle(style))
                    .append(Component.literal(" ").withStyle(style))
                    .append(Component.literal(name).withStyle(style)));
        }
    }

    private static String formatFireMode(String name) {
        try {
            com.tacz.guns.api.item.gun.FireMode mode =
                    com.tacz.guns.api.item.gun.FireMode.valueOf(name.trim().toUpperCase(Locale.ROOT));
            return switch (mode) {
                case AUTO -> Component.translatable("taczfixes.tooltip.fire_mode_auto").getString();
                case BURST -> Component.translatable("taczfixes.tooltip.fire_mode_burst").getString();
                case SEMI -> Component.translatable("taczfixes.tooltip.fire_mode_semi").getString();
                default -> null;
            };
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
