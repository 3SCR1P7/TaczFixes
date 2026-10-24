package com.ssscript.taczfixes.client.util;

import net.minecraft.resources.ResourceLocation;

public interface AutoCraftScreenAccess {
    void taczfixes$applyAutoCraftState(boolean enabled, ResourceLocation recipeId,
                                       boolean enoughMaterials, float progress, boolean coolingDown);
}
