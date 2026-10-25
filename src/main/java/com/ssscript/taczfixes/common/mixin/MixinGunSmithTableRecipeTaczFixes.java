package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.GunSmithTableEnergyConfig;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GunSmithTableRecipe.class)
public abstract class MixinGunSmithTableRecipeTaczFixes implements GunSmithTableEnergyConfig.RecipeAccess {

    @Unique
    private GunSmithTableEnergyConfig.Recipe taczfixes$energyConfig;

    @Inject(method = "<init>(Lnet/minecraft/resources/ResourceLocation;Lcom/tacz/guns/resource/pojo/data/recipe/TableRecipe;)V",
            at = @At("RETURN"), remap = false)
    private void taczfixes$copyEnergyConfig(ResourceLocation id, TableRecipe tableRecipe, CallbackInfo ci) {
        if (tableRecipe instanceof GunSmithTableEnergyConfig.TableRecipeAccess access) {
            this.taczfixes$energyConfig = access.taczfixes$getEnergyConfig();
        }
    }

    @Override
    public GunSmithTableEnergyConfig.Recipe taczfixes$getEnergyConfig() {
        return this.taczfixes$energyConfig;
    }
}
