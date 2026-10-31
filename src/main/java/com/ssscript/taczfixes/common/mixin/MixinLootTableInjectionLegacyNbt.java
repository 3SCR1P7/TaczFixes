package com.ssscript.taczfixes.common.mixin;

import com.google.gson.JsonElement;
import com.ssscript.taczfixes.common.util.LegacyLootFunctionFixer;
import com.tacz.guns.resource.pojo.data.loot.LootTableInjection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = LootTableInjection.class, remap = false)
public class MixinLootTableInjectionLegacyNbt {

    @ModifyVariable(method = "fromJson", at = @At("HEAD"), argsOnly = true, index = 1, remap = false)
    private static JsonElement taczfixes$convertLegacySetNbt(JsonElement element) {
        return LegacyLootFunctionFixer.fix(element);
    }
}
