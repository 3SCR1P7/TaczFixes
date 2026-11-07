package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.util.GunLevelFormula;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.tooltip.ClientGunTooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientGunTooltip.class)
public class MixinGunTooltipLevel {
    @Shadow(remap = false) private MutableComponent levelInfo;
    @Shadow(remap = false) private IGun iGun;
    @Shadow(remap = false) private ItemStack gun;

    @Inject(method = "getText", at = @At("TAIL"), remap = false)
    private void taczfixes$fixLevelPercentage(CallbackInfo ci) {
        int exp = iGun.getExp(gun);
        int maxLevel = Config.GUN_LEVEL_MAX_LEVEL.get();
        int level = exp <= 0 ? 0 : GunLevelFormula.levelForXp(exp);

        String text;
        MutableComponent styled;
        if (level >= maxLevel) {
            text = String.format("%d (MAX)", level);
            styled = Component.translatable("tooltip.tacz.gun.level")
                .append(Component.literal(text).withStyle(ChatFormatting.DARK_PURPLE));
        } else {
            int expForLevel = GunLevelFormula.xpForLevel(level);
            int expForNextLevel = GunLevelFormula.xpForLevel(level + 1);
            int totalForLevel = Math.max(0, expForNextLevel - expForLevel);
            float pct = totalForLevel > 0 ? (float) (exp - expForLevel) / totalForLevel * 100.0f : 0.0f;
            pct = Math.max(0.0f, Math.min(100.0f, pct));
            text = String.format("%d (%.1f%%)", level, pct);
            styled = Component.translatable("tooltip.tacz.gun.level")
                .append(Component.literal(text).withStyle(ChatFormatting.YELLOW));
        }
        levelInfo = styled;
    }
}
