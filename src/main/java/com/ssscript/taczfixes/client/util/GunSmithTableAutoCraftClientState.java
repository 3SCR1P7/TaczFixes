package com.ssscript.taczfixes.client.util;

import com.tacz.guns.client.gui.GunSmithTableScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public final class GunSmithTableAutoCraftClientState {

    private GunSmithTableAutoCraftClientState() {
    }

    public static void handle(int containerId, boolean enabled, ResourceLocation recipeId,
                              boolean enoughMaterials, float progress, boolean coolingDown) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof GunSmithTableScreen screen)) {
            return;
        }
        if (screen.getMenu().containerId != containerId) {
            return;
        }
        if (screen instanceof AutoCraftScreenAccess access) {
            access.taczfixes$applyAutoCraftState(enabled, recipeId, enoughMaterials, progress, coolingDown);
        }
    }
}
