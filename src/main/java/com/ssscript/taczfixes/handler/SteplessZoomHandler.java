package com.ssscript.taczfixes.handler;

import com.ssscript.taczfixes.Config;
import com.ssscript.taczfixes.util.CustomSlotStorage;
import com.ssscript.taczfixes.util.ScopeSwitchState;
import com.ssscript.taczfixes.util.SteplessConfig;
import com.ssscript.taczfixes.util.SteplessDisplayAccessor;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.display.attachment.AttachmentDisplay;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Optional;

public class SteplessZoomHandler {
    private static ResourceLocation activeScopeId = null;
    private static float currentZoom = 1.0f;

    @SubscribeEvent
    public void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !player.isAlive()) return;
        if (!(player instanceof IClientPlayerGunOperator operator) || !operator.isAim()) return;

        ItemStack stack = player.getMainHandItem();
        if (IGun.getIGunOrNull(stack) == null) return;

        SteplessConfig cfg = getConfigFor(stack);
        if (cfg == null) return;

        double delta = event.getScrollDelta();
        if (delta == 0) return;

        float dir = delta > 0 ? 1.0f : -1.0f;
        float steps = (float) Math.abs(delta);
        float multiplier;
        if (Screen.hasControlDown()) {
            multiplier = Config.STEPLESS_ZOOM_CTRL_MULTIPLIER.get().floatValue();
        } else if (Screen.hasAltDown()) {
            multiplier = Config.STEPLESS_ZOOM_ALT_MULTIPLIER.get().floatValue();
        } else {
            multiplier = 1.0f;
        }
        currentZoom = cfg.clampZoom(currentZoom + dir * cfg.speed * steps * multiplier);
        event.setCanceled(true);
    }

    public static float getSteplessZoom(ItemStack stack) {
        SteplessConfig cfg = getConfigFor(stack);
        if (cfg == null) return -1.0f;
        return currentZoom;
    }

    private static SteplessConfig getConfigFor(ItemStack stack) {
        if (!Config.STEPLESS_ZOOM_ENABLED.get()) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return null;

        ResourceLocation slotId = null;
        String active = ScopeSwitchState.getActiveSlot(stack);
        if (active != null) {
            ItemStack scope = CustomSlotStorage.get(stack, active);
            if (!scope.isEmpty()) {
                IAttachment attachment = IAttachment.getIAttachmentOrNull(scope);
                if (attachment != null) {
                    slotId = attachment.getAttachmentId(scope);
                }
            }
        }
        if (slotId == null || DefaultAssets.isEmptyAttachmentId(slotId)) {
            slotId = gun.getAttachmentId(stack, AttachmentType.SCOPE);
        }
        if (slotId == null || slotId.equals(DefaultAssets.EMPTY_ATTACHMENT_ID)) {
            slotId = gun.getBuiltInAttachmentId(stack, AttachmentType.SCOPE);
        }
        if (slotId == null || DefaultAssets.isEmptyAttachmentId(slotId)) return null;

        Optional<CommonAttachmentIndex> indexOpt = TimelessAPI.getCommonAttachmentIndex(slotId);
        if (indexOpt.isEmpty()) return null;
        ResourceLocation displayId = indexOpt.get().getPojo().getDisplay();
        if (displayId == null) return null;

        AttachmentDisplay display = ClientAssetsManager.INSTANCE.getAttachmentDisplay(displayId);
        if (display == null) return null;
        if (!(display instanceof SteplessDisplayAccessor accessor)) return null;

        SteplessConfig cfg = accessor.getStepless();
        if (cfg == null || !cfg.enable) return null;

        if (!displayId.equals(activeScopeId)) {
            activeScopeId = displayId;
            currentZoom = cfg.clampZoom(cfg.zoom_default);
        }
        return cfg;
    }
}
