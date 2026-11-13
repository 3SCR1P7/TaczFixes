package com.ssscript.taczfixes.client.hud;

import com.ssscript.taczfixes.client.client.DualWieldClient;
import com.ssscript.taczfixes.client.client.OffhandDisplayManager;
import com.ssscript.taczfixes.client.handler.AimSwayHandler;
import com.ssscript.taczfixes.client.handler.HoldBreathKeyHandler;
import com.ssscript.taczfixes.client.mixin.MixinGunHudOverlayAccessor;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAmmoIndex;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.client.resource.pojo.display.gun.AmmoCountStyle;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunHeatData;
import com.tacz.guns.resource.pojo.data.gun.GunReloadData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Locale;
import java.util.Optional;

/**
 * 客户端自定义 HUD 生命周期:
 * 手持配置了 custom_hud 的枪时创建 ApricityUI Overlay Document, 切枪/丢枪/离开世界时销毁;
 * 资源重载时重新同步枪包资源并重建。旧 JSON HUD 继续由对应 mixin 渲染。
 */
public final class CustomHudController {

    private static final CustomHudController INSTANCE = new CustomHudController();

    private final ApricityHudBackend backend;
    private String activeKey;
    private CustomHudSnapshot lastSnapshot;

    private CustomHudController() {
        this.backend = ApricityHudSupport.backend();
    }

    public static CustomHudController instance() {
        return INSTANCE;
    }

    /** 资源重载完成后由客户端资源监听器调用(客户端线程)。 */
    public static void onResourceReload() {
        INSTANCE.handleResourceReload();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickInternal();
    }

    @SubscribeEvent
    public void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            reset();
        }
    }

    private void tickInternal() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            reset();
            return;
        }

        CustomHudManager.ActiveGun active = CustomHudManager.activeGun();
        if (active == null) {
            reset();
            return;
        }

        String key = active.key();
        if (!key.equals(activeKey)) {
            closeAui();
            activeKey = key;
            lastSnapshot = null;
            CustomHudManager.setApricityFailed(false);
        }

        CustomHudManager.Kind kind = CustomHudManager.kindFor(active);
        if (kind != CustomHudManager.Kind.APRICITY) {
            closeAui();
            return;
        }
        if (CustomHudManager.isApricityFailed()) {
            closeAui();
            return;
        }
        if (backend == null) {
            markFailed();
            return;
        }

        CustomHudSnapshot snapshot;
        try {
            snapshot = buildSnapshot(active);
        } catch (Throwable ignored) {
            snapshot = null;
        }
        if (snapshot == null) {
            closeAui();
            return;
        }

        // 界面打开时 HUD 不可见; 保留 Document, 关闭界面后的下一 tick 再刷新数据。
        if (minecraft.screen != null) {
            return;
        }

        if (!ApricityHudResources.ensure(active.hudId, backend)) {
            markFailed();
            return;
        }

        String path = ApricityHudResources.logicalPath(active.hudId);
        if (!backend.isOpen() || !path.equals(backend.getOpenPath())) {
            backend.close();
            if (!backend.open(path)) {
                markFailed();
                return;
            }
            lastSnapshot = null;
        }

        if (!snapshot.equals(lastSnapshot)) {
            try {
                backend.update(snapshot);
            } catch (Throwable ignored) {
                markFailed();
                return;
            }
            lastSnapshot = snapshot;
        }
        CustomHudManager.setApricityRendering(true);
    }

    private void handleResourceReload() {
        closeAui();
        activeKey = null;
        lastSnapshot = null;
        CustomHudManager.setApricityFailed(false);
        ApricityHudResources.clearAttempts();
        ApricityHudResources.reloadAll(backend);
    }

    private void closeAui() {
        if (backend != null && backend.isOpen()) {
            backend.close();
        }
        CustomHudManager.setApricityRendering(false);
    }

    private void markFailed() {
        closeAui();
        CustomHudManager.setApricityFailed(true);
    }

    private void reset() {
        closeAui();
        activeKey = null;
        lastSnapshot = null;
        CustomHudManager.setApricityFailed(false);
    }

    private static CustomHudSnapshot buildSnapshot(CustomHudManager.ActiveGun active) {
        LocalPlayer player = active.player;
        ItemStack stack = active.stack;
        IGun gun = active.gun;
        ClientGunIndex index = TimelessAPI.getClientGunIndex(active.gunId).orElse(null);
        GunData gunData = index == null ? null : index.getGunData();
        if (gunData == null) return null;
        GunDisplayInstance display = TimelessAPI.getGunDisplay(stack).orElse(null);

        boolean useInventoryAmmo = gun.useInventoryAmmo(stack);
        boolean overheatLocked = gunData.hasHeatData() && gun.isOverheatLocked(stack);
        boolean barrel = gun.hasBulletInBarrel(stack) && gunData.getBolt() != Bolt.OPEN_BOLT;

        int maxAmmo;
        int inventoryAmmo;
        try {
            GunHudCacheCompat.update(player, stack, gunData, gun, useInventoryAmmo);
            maxAmmo = Math.max(1, MixinGunHudOverlayAccessor.taczfixes$getCacheMaxAmmoCount());
            inventoryAmmo = MixinGunHudOverlayAccessor.taczfixes$getCacheInventoryAmmoCount();
        } catch (Throwable ignored) {
            maxAmmo = Math.max(1, gunData.getAmmoAmount());
            inventoryAmmo = 0;
        }
        int currentAmmo = Math.min((useInventoryAmmo ? inventoryAmmo : gun.getCurrentAmmoCount(stack))
                + (barrel ? 1 : 0), 9999);
        boolean empty = overheatLocked || (currentAmmo < maxAmmo * 0.25D && currentAmmo < 10);

        int percent = (int) Math.round(currentAmmo * 100.0D / maxAmmo);
        percent = Math.max(0, Math.min(999, percent));
        String ammoStyle = display != null && display.getAmmoCountStyle() == AmmoCountStyle.PERCENT
                ? "percent" : "normal";
        String ammoPadded = String.format(Locale.ROOT, "%03d", currentAmmo);
        String ammoText = "percent".equals(ammoStyle)
                ? String.format(Locale.ROOT, "%03d%%", percent) : ammoPadded;

        GunReloadData reloadData = gunData.getReloadData();
        boolean infiniteInventory = reloadData != null && reloadData.isInfinite();
        String inventoryText = infiniteInventory ? "\u221e"
                : (useInventoryAmmo ? "" : String.format(Locale.ROOT, "%04d", inventoryAmmo));

        ResourceLocation ammoId = gunData.getAmmoId();
        String ammoTypeId = ammoId == null ? "" : ammoId.toString();
        String ammoTypeKey = "";
        String ammoTypeName = "";
        if (ammoId != null) {
            Optional<ClientAmmoIndex> ammoIndex = TimelessAPI.getClientAmmoIndex(ammoId);
            if (ammoIndex.isPresent()) {
                ammoTypeKey = ammoIndex.get().getName();
                if (ammoTypeKey != null && !ammoTypeKey.isBlank()) {
                    ammoTypeName = Component.translatable(ammoTypeKey).getString();
                }
            }
            if (ammoTypeKey == null) ammoTypeKey = "";
            if (ammoTypeName.isEmpty()) ammoTypeName = ammoTypeId;
        }

        FireMode fireMode = IGun.getMainHandFireMode(player);
        String mode = fireMode == null ? "semi" : switch (fireMode) {
            case AUTO -> "auto";
            case BURST -> "burst";
            default -> "semi";
        };

        boolean hasHeat = gunData.hasHeatData() && gun.hasHeatData(stack);
        float heatMax = 0.0F;
        float heatValue = 0.0F;
        int heatPercent = 0;
        if (hasHeat) {
            GunHeatData heatData = gunData.getHeatData();
            heatMax = heatData == null ? 0.0F : heatData.getHeatMax();
            if (heatMax > 0.0F) {
                heatValue = Math.max(0.0F, Math.min(heatMax, gun.getHeatAmount(stack)));
                heatPercent = Math.round(heatValue * 100.0F / heatMax);
            } else {
                hasHeat = false;
            }
        }

        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        boolean aiming = operator != null && operator.isAim();
        float aimProgress = operator == null ? 0.0F
                : Math.max(0.0F, Math.min(1.0F, operator.getClientAimingProgress(1.0F)));
        boolean crawling = operator != null && operator.isCrawl();

        boolean reloading = false;
        try {
            IGunOperator gunOperator = IGunOperator.fromLivingEntity(player);
            ReloadState reloadState = gunOperator == null ? null : gunOperator.getSynReloadState();
            reloading = reloadState != null && reloadState.getStateType() != null
                    && reloadState.getStateType().isReloading();
        } catch (Throwable ignored) {
        }
        boolean offhandReloading = false;
        try {
            offhandReloading = OffhandDisplayManager.getClientState().isReloading();
        } catch (Throwable ignored) {
        }

        boolean sprinting = player.isSprinting();
        boolean holdBreath = false;
        try {
            holdBreath = HoldBreathKeyHandler.HOLD_BREATH_KEY.isDown()
                    && AimSwayHandler.canHoldBreath(player);
        } catch (Throwable ignored) {
        }
        boolean dualWield = DualWieldClient.isDualMode(player);
        boolean sneaking = player.isShiftKeyDown();

        String hudTexture = display == null || display.getHUDTexture() == null
                ? "" : display.getHUDTexture().toString();
        String hudEmptyTexture = display == null || display.getHudEmptyTexture() == null
                ? "" : display.getHudEmptyTexture().toString();
        boolean showCrosshair = display == null || display.isShowCrosshair();

        return new CustomHudSnapshot(
                active.gunId.toString(),
                ammoText,
                ammoPadded,
                currentAmmo,
                maxAmmo,
                percent,
                ammoStyle,
                empty,
                useInventoryAmmo,
                infiniteInventory,
                inventoryAmmo,
                inventoryText,
                ammoTypeId,
                ammoTypeKey,
                ammoTypeName,
                mode,
                hasHeat,
                heatValue,
                heatMax,
                heatPercent,
                overheatLocked,
                aiming,
                aimProgress,
                reloading,
                offhandReloading,
                sprinting,
                holdBreath,
                dualWield,
                crawling,
                sneaking,
                hudTexture,
                hudEmptyTexture,
                showCrosshair);
    }
}
