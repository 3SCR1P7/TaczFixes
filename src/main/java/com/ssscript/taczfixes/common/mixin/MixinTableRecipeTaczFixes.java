package com.ssscript.taczfixes.common.mixin;

import com.google.gson.annotations.SerializedName;
import com.ssscript.taczfixes.common.data.GunSmithTableEnergyConfig;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(TableRecipe.class)
public abstract class MixinTableRecipeTaczFixes implements GunSmithTableEnergyConfig.TableRecipeAccess {

    @Unique
    @SerializedName("taczfixes")
    private GunSmithTableEnergyConfig.Recipe taczfixes$energyConfig;

    @Override
    public GunSmithTableEnergyConfig.Recipe taczfixes$getEnergyConfig() {
        return this.taczfixes$energyConfig;
    }
}
