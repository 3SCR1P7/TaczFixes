package com.ssscript.taczfixes.client.render;

import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.util.ChargeStorage;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.overlay.HeatBarOverlay;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunHeatData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;

@OnlyIn(Dist.CLIENT)
public final class DualOffhandHeatBar {
    private static final float BOTH_HANDS_OFFSET = 24.0f;
    private static final HeatBarOverlay NATIVE_HEAT_BAR = new HeatBarOverlay();
    private static float heatScale = 0.25f;

    private DualOffhandHeatBar() {
    }

    public static void render(ForgeGui gui, GuiGraphics graphics, int width, int height) {
        if (!RenderConfig.GUN_HUD_ENABLE.get().booleanValue()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !DualWieldClient.isDualMode(player)) {
            return;
        }
        ItemStack offhandStack = player.getOffhandItem();
        IGun offhandGun = IGun.getIGunOrNull(offhandStack);
        if (offhandGun == null) {
            return;
        }
        GunData offhandData = TimelessAPI.getClientGunIndex(offhandGun.getGunId(offhandStack))
                .map(index -> index.getGunData()).orElse(null);
        GunHeatData heatData = offhandData == null ? null : offhandData.getHeatData();
        boolean heatBar = heatData != null && offhandGun.hasHeatData(offhandStack) && heatData.getHeatMax() > 0.0f;
        boolean chargeBar = usesChargeBar(offhandStack);
        if (!heatBar && !chargeBar) {
            return;
        }
        float percent;
        boolean locked;
        if (chargeBar) {
            int max = ChargeStorage.getMax(offhandStack);
            percent = max <= 0 ? 0.0f : Mth.clamp(ChargeStorage.get(offhandStack) / (float) max, 0.0f, 1.0f);
            locked = false;
        } else {
            percent = Mth.clamp(offhandGun.getHeatAmount(offhandStack) / heatData.getHeatMax(), 0.0f, 1.0f);
            locked = offhandGun.isOverheatLocked(offhandStack);
        }
        float offsetY = mainHandHasHeatBar(player) ? BOTH_HANDS_OFFSET : 0.0f;
        animate(percent);
        graphics.pose().pushPose();
        graphics.pose().scale(heatScale, heatScale, 1.0f);
        NATIVE_HEAT_BAR.renderOverheat(percent, graphics,
                (int) (width / heatScale),
                (int) ((height + offsetY * 2.0f) / heatScale),
                locked, gui.getGuiTicks());
        graphics.pose().popPose();
    }

    private static boolean mainHandHasHeatBar(LocalPlayer player) {
        ItemStack mainStack = player.getMainHandItem();
        IGun mainGun = IGun.getIGunOrNull(mainStack);
        if (mainGun == null) {
            return false;
        }
        if (usesChargeBar(mainStack)) {
            return true;
        }
        GunData mainData = TimelessAPI.getClientGunIndex(mainGun.getGunId(mainStack))
                .map(index -> index.getGunData()).orElse(null);
        GunHeatData heatData = mainData == null ? null : mainData.getHeatData();
        return heatData != null && mainGun.hasHeatData(mainStack) && heatData.getHeatMax() > 0.0f;
    }

    private static boolean usesChargeBar(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        GunTaczFixesData.ChargeConfig cfg = ChargeStorage.config(stack);
        return cfg != null && Boolean.TRUE.equals(cfg.overheat_bar) && ChargeStorage.getMax(stack) > 0;
    }

    private static void animate(float percent) {
        float target = percent / 8.0f + 0.75f;
        if (heatScale < target) {
            heatScale += 0.05f;
        }
        if (heatScale > target) {
            heatScale -= 0.025f;
        }
        if (heatScale > target - 0.03f && heatScale < target + 0.055f) {
            heatScale = target;
        }
    }
}
