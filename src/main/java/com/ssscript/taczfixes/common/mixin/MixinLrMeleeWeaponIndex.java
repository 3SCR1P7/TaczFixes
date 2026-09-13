package com.ssscript.taczfixes.common.mixin;

import com.google.gson.JsonElement;
import com.ssscript.taczfixes.common.data.LrMeleeStaminaManager;
import me.xjqsh.lrtactical.item.index.MeleeWeaponIndex;
import me.xjqsh.lrtactical.item.melee.MeleeWeaponData;
import me.xjqsh.lrtactical.item.melee.MeleeWeaponType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 读取 lrtactical 近战武器 index 中的 taczfixes.stamina_consume。 */
@Mixin(targets = "me.xjqsh.lrtactical.item.index.MeleeWeaponIndex", remap = false)
public class MixinLrMeleeWeaponIndex {

    @Inject(method = "deserialize", at = @At("HEAD"), remap = false)
    private static <T extends MeleeWeaponData> void taczfixes$parseStaminaConsume(
            MeleeWeaponType<T> type, JsonElement json, String name, String tooltip,
            ResourceLocation id, Item baseItem, CallbackInfoReturnable<MeleeWeaponIndex<T>> cir) {
        LrMeleeStaminaManager.parse(id, json);
    }
}
