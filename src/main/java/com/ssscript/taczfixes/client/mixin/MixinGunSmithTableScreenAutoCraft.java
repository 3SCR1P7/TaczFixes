package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.AutoCraftButton;
import com.ssscript.taczfixes.client.util.AutoCraftScreenAccess;
import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.network.ClientMessageAutoCraft;
import com.ssscript.taczfixes.common.network.ClientMessageAutoCraftStateRequest;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GunSmithTableScreen.class)
public abstract class MixinGunSmithTableScreenAutoCraft extends Screen implements AutoCraftScreenAccess {

    private static final ResourceLocation TACZFIXES_NO_RECIPE = new ResourceLocation("taczfixes", "none");

    @Shadow(remap = false)
    @Nullable
    private GunSmithTableRecipe selectedRecipe;

    @Unique
    private boolean taczfixes$autoCraft;
    @Unique
    private boolean taczfixes$enoughMaterials;
    @Unique
    private boolean taczfixes$coolingDown;
    @Unique
    private float taczfixes$progress;
    @Unique
    private boolean taczfixes$stateRequested;
    @Unique
    private boolean taczfixes$applyingServerState;
    @Unique
    @Nullable
    private ResourceLocation taczfixes$autoRecipeId;
    @Unique
    @Nullable
    private Component taczfixes$autoCraftName;
    @Unique
    @Nullable
    private AutoCraftButton taczfixes$autoButton;

    protected MixinGunSmithTableScreenAutoCraft() {
        super(Component.literal(""));
    }

    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void taczfixes$requestAutoCraftState(CallbackInfo ci) {
        if (taczfixes$stateRequested || !Config.AUTO_CRAFT_ENABLED.get()) {
            return;
        }
        taczfixes$stateRequested = true;
        NetworkHandler.CHANNEL.sendToServer(
                new ClientMessageAutoCraftStateRequest(((GunSmithTableScreen) (Object) this).getMenu().containerId));
    }

    @Inject(method = "addCraftButton", at = @At("TAIL"), remap = false)
    private void taczfixes$addAutoCraftButton(CallbackInfo ci) {
        if (!Config.AUTO_CRAFT_ENABLED.get()) {
            return;
        }
        if (taczfixes$autoCraft && !taczfixes$applyingServerState) {
            if (selectedRecipe == null) {
                taczfixes$clearAutoCraft(true);
            } else {
                ResourceLocation recipeId = selectedRecipe.m_6423_();
                if (!recipeId.equals(taczfixes$autoRecipeId)) {
                    taczfixes$autoRecipeId = recipeId;
                    taczfixes$autoCraftName = selectedRecipe.getOutput().getHoverName();
                    taczfixes$enoughMaterials = false;
                    taczfixes$coolingDown = false;
                    taczfixes$progress = 0.0F;
                    taczfixes$send(true);
                }
            }
        }

        int guiLeft = (this.width - 344) / 2;
        int guiTop = (this.height - 186) / 2;
        AutoCraftButton button = new AutoCraftButton(guiLeft + 264, guiTop + 161,
                b -> taczfixes$toggleAutoCraft());
        button.setStatus(taczfixes$autoCraft, taczfixes$enoughMaterials, taczfixes$coolingDown);
        button.setTooltip(Tooltip.create(Component.translatable("taczfixes.gun_smith_table.auto_craft")));
        taczfixes$autoButton = button;
        this.addRenderableWidget(button);
    }

    @Unique
    private void taczfixes$toggleAutoCraft() {
        if (taczfixes$autoCraft) {
            taczfixes$clearAutoCraft(true);
        } else if (selectedRecipe != null) {
            taczfixes$autoCraft = true;
            taczfixes$enoughMaterials = false;
            taczfixes$coolingDown = false;
            taczfixes$progress = 0.0F;
            taczfixes$autoRecipeId = selectedRecipe.m_6423_();
            taczfixes$autoCraftName = selectedRecipe.getOutput().getHoverName();
            taczfixes$send(true);
        }
        ((GunSmithTableScreen) (Object) this).m_7856_();
    }

    @Unique
    private void taczfixes$clearAutoCraft(boolean notifyServer) {
        if (!taczfixes$autoCraft && taczfixes$autoRecipeId == null && taczfixes$autoCraftName == null) {
            return;
        }
        taczfixes$autoCraft = false;
        taczfixes$enoughMaterials = false;
        taczfixes$coolingDown = false;
        taczfixes$progress = 0.0F;
        taczfixes$autoRecipeId = null;
        taczfixes$autoCraftName = null;
        if (notifyServer) {
            taczfixes$send(false);
        }
    }

    @Unique
    private void taczfixes$send(boolean enabled) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        int containerId = ((GunSmithTableScreen) (Object) this).getMenu().containerId;
        NetworkHandler.CHANNEL.sendToServer(
                new ClientMessageAutoCraft(containerId, taczfixes$autoRecipeId, enabled));
    }

    @Override
    public void taczfixes$applyAutoCraftState(boolean enabled, ResourceLocation recipeId,
                                              boolean enoughMaterials, float progress,
                                              boolean coolingDown) {
        if (!enabled || recipeId == null || TACZFIXES_NO_RECIPE.equals(recipeId)) {
            if (taczfixes$autoCraft || taczfixes$autoRecipeId != null || taczfixes$autoCraftName != null) {
                taczfixes$clearAutoCraft(false);
                taczfixes$refreshScreen();
            }
            return;
        }

        boolean recipeChanged = !recipeId.equals(taczfixes$autoRecipeId);
        taczfixes$autoCraft = true;
        taczfixes$enoughMaterials = enoughMaterials;
        taczfixes$coolingDown = coolingDown;
        taczfixes$progress = Math.max(0.0F, Math.min(1.0F, progress));
        if (recipeChanged || taczfixes$autoCraftName == null) {
            taczfixes$autoRecipeId = recipeId;
            taczfixes$autoCraftName = taczfixes$resolveRecipeName(recipeId);
            if (selectedRecipe == null || !recipeId.equals(selectedRecipe.m_6423_())) {
                ((GunSmithTableScreen) (Object) this).getMenu().requestRecipeSelection(recipeId);
            }
            taczfixes$refreshScreen();
        } else if (taczfixes$autoButton != null) {
            taczfixes$autoButton.setStatus(true, enoughMaterials, coolingDown);
        }
    }

    @Unique
    private void taczfixes$refreshScreen() {
        taczfixes$applyingServerState = true;
        try {
            ((GunSmithTableScreen) (Object) this).m_7856_();
        } finally {
            taczfixes$applyingServerState = false;
        }
    }

    @Unique
    private static Component taczfixes$resolveRecipeName(ResourceLocation recipeId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            Recipe<?> recipe = minecraft.level.getRecipeManager().byKey(recipeId).orElse(null);
            if (recipe instanceof GunSmithTableRecipe tableRecipe) {
                return tableRecipe.getOutput().getHoverName();
            }
        }
        return Component.literal(recipeId.toString());
    }

    @Inject(method = "m_88315_", at = @At("TAIL"), remap = false)
    private void taczfixes$renderAutoCraftStatus(GuiGraphics graphics, int mouseX, int mouseY,
                                                  float partialTick, CallbackInfo ci) {
        if (!taczfixes$autoCraft || taczfixes$autoCraftName == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int guiLeft = (this.width - 344) / 2;
        int guiTop = (this.height - 186) / 2;
        int y = Math.max(2, guiTop - 12);
        int color = taczfixes$coolingDown
                ? 0xFFFFFF55
                : (taczfixes$enoughMaterials ? 0xFF55FF55 : 0xFFFF5555);
        int percent = Math.max(0, Math.min(100, Math.round(taczfixes$progress * 100.0F)));
        Component text = Component.translatable(
                "taczfixes.gun_smith_table.auto_crafting", taczfixes$autoCraftName, percent + "%");
        graphics.pose().pushPose();
        graphics.pose().translate(guiLeft, y, 0.0F);
        graphics.pose().scale(0.75F, 0.75F, 1.0F);
        graphics.drawString(minecraft.font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }
}
