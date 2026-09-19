package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.display.gun.AmmoCountStyle;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunHeatData;
import com.tacz.guns.util.AttachmentDataUtils;
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import java.text.DecimalFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.Item;

@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = {Dist.CLIENT}, bus = Mod.EventBusSubscriber.Bus.MOD)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/render/OffhandGunHudOverlay.class */
public final class OffhandGunHudOverlay implements IGuiOverlay {
    private static final int MAX_AMMO_COUNT = 9999;
    private static final int HUD_LEFT = 18;
    private static int cacheMaxAmmoCount;
    private static int cacheInventoryAmmoCount;

    @Nullable
    private static ResourceLocation cachedGunId;
    private static final ResourceLocation SEMI = new ResourceLocation("tacz", "textures/hud/fire_mode_semi.png");
    private static final ResourceLocation AUTO = new ResourceLocation("tacz", "textures/hud/fire_mode_auto.png");
    private static final ResourceLocation BURST = new ResourceLocation("tacz", "textures/hud/fire_mode_burst.png");
    private static final DecimalFormat CURRENT_AMMO_FORMAT = new DecimalFormat("000");
    private static final DecimalFormat CURRENT_AMMO_FORMAT_PERCENT = new DecimalFormat("000%");
    private static final DecimalFormat INVENTORY_AMMO_FORMAT = new DecimalFormat("0000");
    private static long checkAmmoTimestamp = -1;

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("offhand_gun_hud", new OffhandGunHudOverlay());
    }

    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        ItemStack stack;
        IGun gun;
        int ammoCount;
        int ammoCountColor;
        String currentAmmoCountText;
        String str;
        if (!((Boolean) RenderConfig.GUN_HUD_ENABLE.get()).booleanValue()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!(player instanceof IClientPlayerGunOperator) || !DualWieldClient.isDualMode(player) || (gun = IGun.getIGunOrNull((stack = player.getOffhandItem()))) == null) {
            return;
        }
        ResourceLocation gunId = gun.getGunId(stack);
        GunData gunData = (GunData) TimelessAPI.getClientGunIndex(gunId).map((v0) -> {
            return v0.getGunData();
        }).orElse(null);
        GunDisplayInstance display = (GunDisplayInstance) TimelessAPI.getGunDisplay(stack).orElse(null);
        if (gunData == null || display == null) {
            return;
        }
        boolean useInventoryAmmo = gun.useInventoryAmmo(stack);
        updateAmmoCache(player, stack, gunData, gun, gunId, useInventoryAmmo);
        boolean useDummyAmmo = gun.useDummyAmmo(stack);
        boolean overheatLocked = gunData.hasHeatData() && gun.isOverheatLocked(stack);
        boolean closedBoltRound = gun.hasBulletInBarrel(stack) && gunData.getBolt() != Bolt.OPEN_BOLT;
        if (useInventoryAmmo) {
            ammoCount = cacheInventoryAmmoCount + (closedBoltRound ? 1 : 0);
        } else {
            ammoCount = gun.getCurrentAmmoCount(stack) + (closedBoltRound ? 1 : 0);
        }
        int ammoCount2 = Mth.clamp(ammoCount, 0, MAX_AMMO_COUNT);
        if ((ammoCount2 < cacheMaxAmmoCount * 0.25f && ammoCount2 < 10) || overheatLocked) {
            ammoCountColor = 16733525;
        } else if (useInventoryAmmo && useDummyAmmo) {
            ammoCountColor = 5636095;
        } else if (useInventoryAmmo) {
            ammoCountColor = 16777045;
        } else {
            ammoCountColor = 16777215;
        }
        int inventoryAmmoCountColor = (useInventoryAmmo || !useDummyAmmo) ? 11184810 : 5636095;
        if (display.getAmmoCountStyle() == AmmoCountStyle.PERCENT) {
            float ratio = ammoCount2 / (cacheMaxAmmoCount == 0 ? 1.0f : cacheMaxAmmoCount);
            currentAmmoCountText = CURRENT_AMMO_FORMAT_PERCENT.format(ratio);
        } else {
            currentAmmoCountText = CURRENT_AMMO_FORMAT.format(ammoCount2);
        }
        if (useInventoryAmmo) {
            str = "";
        } else {
            str = INVENTORY_AMMO_FORMAT.format(cacheInventoryAmmoCount);
        }
        String inventoryAmmoCountText = str;
        if (!useInventoryAmmo && gunData.getReloadData().isInfinite()) {
            inventoryAmmoCountText = "∞";
        }
        com.ssscript.taczfixes.common.data.GunTaczFixesData.ChargeConfig chargeCfg =
                com.ssscript.taczfixes.common.util.ChargeStorage.config(stack);
        boolean chargeHud = chargeCfg != null && Boolean.TRUE.equals(chargeCfg.replace_ammo_hud)
                && com.ssscript.taczfixes.common.util.ChargeStorage.getMax(stack) > 0;
        int chargePercent = 0;
        int gunIconAmmo = ammoCount2;
        if (chargeHud) {
            int chargeMax = com.ssscript.taczfixes.common.util.ChargeStorage.getMax(stack);
            chargePercent = chargeMax <= 0 ? 0 : Math.round(com.ssscript.taczfixes.common.util.ChargeStorage.get(stack) * 100.0f / chargeMax);
            currentAmmoCountText = String.format("%03d%%", Integer.valueOf(chargePercent));
            inventoryAmmoCountText = "";
            ammoCountColor = (chargePercent < 25 || overheatLocked) ? 16733525 : 16777215;
            gunIconAmmo = chargePercent <= 0 ? 0 : 1;
        }
        String ammoTypeName = com.ssscript.taczfixes.client.util.GunsmithLibAmmoNameCompat.resolve(stack);
        Component sideLabel = ammoTypeName != null && !ammoTypeName.isBlank()
                ? Component.literal(ammoTypeName)
                : Component.translatable("taczfixes.hud.left");
        renderNumbers(graphics, minecraft.font, height, currentAmmoCountText, inventoryAmmoCountText, ammoCountColor, inventoryAmmoCountColor, sideLabel);
        renderGunAndFireMode(graphics, minecraft.font, stack, gun, display, height, currentAmmoCountText, gunIconAmmo, overheatLocked);
        renderHeat(gui, graphics, stack, gun, gunData, height);
    }

    private static void renderNumbers(GuiGraphics graphics, Font font, int height, String currentAmmoCountText, String inventoryAmmoCountText, int ammoCountColor, int inventoryAmmoCountColor, Component sideLabel) {
        graphics.fill(61, height - 43, 62, height - 25, -1);
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.scale(1.5f, 1.5f, 1.0f);
        graphics.drawString(font, currentAmmoCountText, 44.0f, (height - 43) / 1.5f, ammoCountColor, false);
        poseStack.popPose();
        poseStack.pushPose();
        poseStack.scale(0.8f, 0.8f, 1.0f);
        float reserveX = 68.0f + (font.width(currentAmmoCountText) * 1.5f);
        graphics.drawString(font, inventoryAmmoCountText, reserveX / 0.8f, (height - 43) / 0.8f, inventoryAmmoCountColor, false);
        poseStack.popPose();
        poseStack.pushPose();
        poseStack.scale(0.5f, 0.5f, 1.0f);
        graphics.drawString(font, sideLabel, 132, (int) ((height - 29.0f) / 0.5f), -5592406, false);
        poseStack.popPose();
    }

    private static void renderGunAndFireMode(GuiGraphics graphics, Font font, ItemStack stack, IGun gun, GunDisplayInstance display, int height, String currentAmmoCountText, int ammoCount, boolean overheatLocked) {
        ResourceLocation resourceLocation;
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ResourceLocation hudTexture = display.getHUDTexture();
        ResourceLocation emptyTexture = display.getHudEmptyTexture();
        if (ammoCount <= 0 || overheatLocked) {
            if (emptyTexture == null) {
                RenderSystem.setShaderColor(1.0f, 0.3f, 0.3f, 1.0f);
            } else {
                hudTexture = emptyTexture;
            }
        }
        graphics.blit(hudTexture, HUD_LEFT, height - 44, 0.0f, 0.0f, 39, 13, 39, 13);
        FireMode fireMode = gun.getFireMode(stack);
        switch (AnonymousClass1.$SwitchMap$com$tacz$guns$api$item$gun$FireMode[fireMode.ordinal()]) {
            case ServerMessageOffhandActionResult.ACTION_RELOAD /* 1 */:
                resourceLocation = AUTO;
                break;
            case ServerMessageOffhandActionResult.ACTION_BOLT /* 2 */:
                resourceLocation = BURST;
                break;
            default:
                resourceLocation = SEMI;
                break;
        }
        ResourceLocation fireModeTexture = resourceLocation;
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        int fireModeX = (int) (67.5f + (font.width(currentAmmoCountText) * 1.5f));
        graphics.blit(fireModeTexture, fireModeX, height - 38, 0.0f, 0.0f, 10, 10, 10, 10);
    }

    /* renamed from: com.ssscript.taczfixes.client.render.OffhandGunHudOverlay$1, reason: invalid class name */
    /* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/render/OffhandGunHudOverlay$1.class */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$tacz$guns$api$item$gun$FireMode = new int[FireMode.values().length];

        static {
            try {
                $SwitchMap$com$tacz$guns$api$item$gun$FireMode[FireMode.AUTO.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$tacz$guns$api$item$gun$FireMode[FireMode.BURST.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
        }
    }

    private static void renderHeat(ForgeGui gui, GuiGraphics graphics, ItemStack stack, IGun gun, GunData gunData, int height) {
        GunHeatData heatData = gunData.getHeatData();
        if (heatData == null || !gun.hasHeatData(stack) || heatData.getHeatMax() <= 0.0f) {
            return;
        }
        float percent = Mth.clamp(gun.getHeatAmount(stack) / heatData.getHeatMax(), 0.0f, 1.0f);
        boolean locked = gun.isOverheatLocked(stack);
        int color = getHeatColor(percent, locked, gui.getGuiTicks());
        int barTop = height - 22;
        graphics.fill(HUD_LEFT, barTop, HUD_LEFT + 100, barTop + 4, -1610612736);
        graphics.fill(19, barTop + 1, 19 + Math.round(percent * (100 - 2)), barTop + 3, color);
    }

    private static int getHeatColor(float percent, boolean locked, int tickCount) {
        if (locked) {
            return tickCount % 20 < 10 ? -65536 : -256;
        }
        if (percent < 0.4f) {
            return -1;
        }
        if (percent <= 0.65f) {
            return FastColor.ARGB32.lerp((percent * 4.0f) - 1.6f, -1, -256);
        }
        return FastColor.ARGB32.lerp((percent - 0.65f) / 0.35f, -256, -65536);
    }

    private static void updateAmmoCache(LocalPlayer player, ItemStack stack, GunData gunData, IGun gun, ResourceLocation gunId, boolean useInventoryAmmo) {
        long now = System.currentTimeMillis();
        if (!gunId.equals(cachedGunId)) {
            cachedGunId = gunId;
            checkAmmoTimestamp = -1L;
        }
        if (now - checkAmmoTimestamp <= 50) {
            return;
        }
        checkAmmoTimestamp = now;
        cacheMaxAmmoCount = AttachmentDataUtils.getAmmoCountWithAttachment(stack, gunData);
        if (IGunOperator.fromLivingEntity(player).needCheckAmmo()) {
            if (gun.useDummyAmmo(stack)) {
                cacheInventoryAmmoCount = gun.getDummyAmmoAmount(stack);
            } else {
                cacheInventoryAmmoCount = countInventoryAmmo(stack, player.getInventory());
            }
        } else {
            cacheInventoryAmmoCount = MAX_AMMO_COUNT;
        }
        if (useInventoryAmmo) {
            gun.setCurrentAmmoCount(stack, cacheInventoryAmmoCount);
        }
    }

    private static int countInventoryAmmo(ItemStack gunStack, Inventory inventory) {
        int result = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack inventoryStack = inventory.getItem(slot);
            Item iAmmoM_41720_ = inventoryStack.getItem();
            if (iAmmoM_41720_ instanceof IAmmo) {
                IAmmo ammo = (IAmmo) iAmmoM_41720_;
                if (ammo.isAmmoOfGun(gunStack, inventoryStack)) {
                    result += inventoryStack.getCount();
                }
            }
            Item iAmmoBoxM_41720_ = inventoryStack.getItem();
            if (iAmmoBoxM_41720_ instanceof IAmmoBox) {
                IAmmoBox ammoBox = (IAmmoBox) iAmmoBoxM_41720_;
                if (!ammoBox.isAmmoBoxOfGun(gunStack, inventoryStack)) {
                    continue;
                } else {
                    if (ammoBox.isAllTypeCreative(inventoryStack) || ammoBox.isCreative(inventoryStack)) {
                        return MAX_AMMO_COUNT;
                    }
                    result += ammoBox.getAmmoCount(inventoryStack);
                }
            }
        }
        return Math.min(result, MAX_AMMO_COUNT);
    }
}
