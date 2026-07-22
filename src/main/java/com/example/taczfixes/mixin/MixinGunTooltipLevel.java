package com.example.taczfixes.mixin;

import com.example.taczfixes.Config;
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
        int base = Config.GUN_LEVEL_BASE_KILLS.get();
        int inc = Config.GUN_LEVEL_INCREMENT.get();

        int level;
        if (exp <= 0 || exp < base) {
            level = 0;
        } else if (inc == 0) {
            level = exp / base;
            if (level > maxLevel) level = maxLevel;
        } else {
            double disc = 1.0 + 8.0 * (double) (exp - base) / (double) inc;
            level = disc < 0 ? 0 : (int) Math.floor((1.0 + Math.sqrt(disc)) / 2.0);
            if (level > maxLevel) level = maxLevel;
        }

        int expForLevel = level <= 0 ? 0 : (inc == 0 ? base * level : base + inc * level * (level - 1) / 2);

        String text;
        MutableComponent styled;
        if (level >= maxLevel) {
            text = String.format("%d (MAX)", level);
            styled = Component.translatable("tooltip.tacz.gun.level")
                .append(Component.literal(text).withStyle(ChatFormatting.DARK_PURPLE));
        } else {
            int expForNextLevel = (inc == 0) ? base * (level + 1) : base + inc * (level + 1) * level / 2;
            int totalForLevel = expForNextLevel - expForLevel;
            float pct = totalForLevel > 0 ? (float) (exp - expForLevel) / totalForLevel * 100.0f : 0.0f;
            text = String.format("%d (%.1f%%)", level, pct);
            styled = Component.translatable("tooltip.tacz.gun.level")
                .append(Component.literal(text).withStyle(ChatFormatting.YELLOW));
        }
        levelInfo = styled;
    }
}
