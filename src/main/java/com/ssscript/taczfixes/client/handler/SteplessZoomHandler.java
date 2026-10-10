package com.ssscript.taczfixes.client.handler;

import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.ssscript.taczfixes.client.util.ScopeViewHelper;
import com.ssscript.taczfixes.common.util.SteplessConfig;
import com.ssscript.taczfixes.common.util.SteplessDisplayAccessor;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
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
    private static ResourceLocation globalScopeId = null;
    private static SteplessConfig globalConfig = null;

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
        if (cfg == null) {
            cfg = getGlobalConfig(slotId);
        }
        if (cfg == null || !cfg.enable) return null;

        if (!displayId.equals(activeScopeId)) {
            activeScopeId = displayId;
            currentZoom = cfg.clampZoom(cfg.zoom_default);
        }
        return cfg;
    }

    /**
     * 全局无极变倍: 未配置 stepless 字段的瞄具在开启全局开关后自动启用。
     * 条件: scope 类型、能切换倍率、不是组合瞄具(多个不同视图)。
     * zoom_min/zoom_max 取可切换倍率的最小/最大值, zoom_default 取列表第一项, speed 取全局配置。
     */
    private static SteplessConfig getGlobalConfig(ResourceLocation slotId) {
        if (!Config.STEPLESS_ZOOM_GLOBAL_ENABLED.get()) return null;
        ClientAttachmentIndex index = TimelessAPI.getClientAttachmentIndex(slotId).orElse(null);
        if (index == null || !index.isScope()) return null;
        float[] zooms = index.getZoom();
        if (zooms == null || zooms.length == 0) return null;
        if (ScopeViewHelper.isCombinedSight(index)) return null;

        SteplessConfig cfg = globalConfig;
        if (cfg == null || !slotId.equals(globalScopeId)) {
            float min = zooms[0];
            float max = zooms[0];
            for (float zoom : zooms) {
                min = Math.min(min, zoom);
                max = Math.max(max, zoom);
            }
            cfg = new SteplessConfig();
            cfg.enable = true;
            cfg.zoom_min = min;
            cfg.zoom_max = max;
            cfg.zoom_default = zooms[0];
            globalConfig = cfg;
            globalScopeId = slotId;
        }
        cfg.speed = Config.STEPLESS_ZOOM_GLOBAL_SPEED.get().floatValue();
        return cfg;
    }
}
