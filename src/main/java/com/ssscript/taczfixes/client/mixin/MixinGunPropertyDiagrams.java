package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.RefitDiagramPreview;
import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.util.LimbDamageFactorHelper;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.text.DecimalFormat;
import java.util.Locale;

/**
 * 改装界面数据图表: 跑射延迟(sprint_time)条目.
 * 原版仅显示基础值 %.2fs; taczfixes 显示 "当前值 (差值)", 柱状图以绿色表示
 * 缩短部分、红色表示延长部分(与通用属性条一致: 缩短为正向)。
 * 实现: 拦截 getSprintTime 计算修饰值; 拦截文本 String.format 替换为带差值的字符串;
 * 拦截 base 条绘制 fill 改画 default+diff 两段。
 */
@Mixin(targets = "com.tacz.guns.client.gui.components.refit.GunPropertyDiagrams", remap = false)
public class MixinGunPropertyDiagrams {

    @Unique
    private static final DecimalFormat TACZFIXES_LIMB_CHART_FORMAT = new DecimalFormat("0.00");

    @Unique
    private static boolean taczfixes$sprintActive = false;
    @Unique
    private static int taczfixes$sprintFillCount = 0;
    @Unique
    private static int taczfixes$sprintFullBarEnd = 0;
    @Unique
    private static float taczfixes$sprintDefault = 0.0f;
    @Unique
    private static float taczfixes$sprintModified = 0.0f;

    @Inject(method = "lambda$draw$3", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/resource/pojo/data/gun/GunData;getSprintTime()F", remap = false), remap = false)
    private static void taczfixes$captureSprint(com.tacz.guns.api.item.IGun iGun, ItemStack gunItem, int y, int x,
                                                net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                                com.tacz.guns.resource.modifier.AttachmentCacheProperty cache,
                                                com.tacz.guns.resource.index.CommonGunIndex gunIndex, CallbackInfo ci) {
        taczfixes$sprintActive = true;
        taczfixes$sprintFillCount = 0;
    }

    /** 悬浮候选配件时, 显示图表改用"卸下该槽原配件 + 安装此配件"后的属性缓存。 */
    @Redirect(method = "draw",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/api/entity/IGunOperator;getCacheProperty()Lcom/tacz/guns/resource/modifier/AttachmentCacheProperty;",
                    remap = false), remap = false)
    private static com.tacz.guns.resource.modifier.AttachmentCacheProperty taczfixes$previewCache(
            com.tacz.guns.api.entity.IGunOperator operator) {
        com.tacz.guns.resource.modifier.AttachmentCacheProperty preview =
                com.ssscript.taczfixes.client.util.RefitDiagramPreview.previewCache();
        return preview != null ? preview : operator.getCacheProperty();
    }

    @Redirect(method = "lambda$draw$3",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/resource/pojo/data/gun/GunData;getSprintTime()F", remap = false), remap = false)
    private static float taczfixes$diagramSprintTime(GunData gunData) {
        float base = gunData.getSprintTime();
        taczfixes$sprintDefault = base;
        float modified = base;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            modified = AttachmentTaczFixesManager.applySprintTime(mc.player.getMainHandItem(), base);
        }
        taczfixes$sprintModified = modified;
        return modified;
    }

    @Inject(method = "lambda$draw$3", at = @At("TAIL"), remap = false)
    private static void taczfixes$endSprint(com.tacz.guns.api.item.IGun iGun, ItemStack gunItem, int y, int x,
                                            net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                            com.tacz.guns.resource.modifier.AttachmentCacheProperty cache,
                                            com.tacz.guns.resource.index.CommonGunIndex gunIndex, CallbackInfo ci) {
        taczfixes$sprintActive = false;
    }

    @Redirect(method = "lambda$draw$3",
            at = @At(value = "INVOKE",
                    target = "Ljava/lang/String;format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", remap = false), remap = false)
    private static String taczfixes$sprintText(String format, Object[] args) {
        if ("%.2fs".equals(format) && taczfixes$sprintActive) {
            float diff = taczfixes$sprintModified - taczfixes$sprintDefault;
            if (Math.abs(diff) < 0.0005f) {
                return String.format("%.2fs", taczfixes$sprintModified);
            }
            // 缩短(负差值)=绿, 延长(正差值)=红
            String colorCode = diff < 0 ? "§a" : "§c";
            return String.format("%.2f " + colorCode + "(%+.2f)", taczfixes$sprintModified, diff);
        }
        return String.format(format, args);
    }

    @Redirect(method = "lambda$draw$3",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", remap = false), remap = false)
    private static void taczfixes$sprintBar(net.minecraft.client.gui.GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        if (taczfixes$sprintActive) {
            if (taczfixes$sprintFillCount == 0) {
                // 背景条: 记录全条右端(barEnd)
                taczfixes$sprintFillCount = 1;
                taczfixes$sprintFullBarEnd = x2;
                graphics.fill(x1, y1, x2, y2, color);
                return;
            }
            taczfixes$sprintFillCount = 2;
            taczfixes$sprintActive = false;
            float def = taczfixes$sprintDefault;
            float mod = taczfixes$sprintModified;
            int barStart = x1;
            int barEnd = taczfixes$sprintFullBarEnd;
            if (Math.abs(mod - def) < 0.0005f || def <= 0.0005f) {
                // 无变化: 画初始条长(defLen 对应 0.5 满条基准), 即原版加配件前的白色长度
                int len = barEnd - barStart;
                int defLen = (int) Math.round(len * ((double) def / 0.5d));
                graphics.fill(barStart, y1, Math.min(barStart + defLen, barEnd), y2, color);
                return;
            }
            // 条长规则: 白条 = 初始长度(defLen) 不变, 差值条(绿/红) 追加在右侧;
            // 若 白+差 > 满条, 按 白:差 = def:(mod-def) 比例压缩到满条。
            // 例1: def 0.2s(40px), mod 0.25s → 白40 + 红10(追加)。
            // 例2: def 0.2s(40px), mod 1.0s → 白+红超满条 → 白20 + 红80。
            int len = barEnd - barStart;
            int defLen = (int) Math.round(len * ((double) def / 0.5d));
            int modLen = (int) Math.round(len * ((double) mod / 0.5d));
            if (mod < def) {
                // 缩短: 绿条从右往左替换白条 —— 白段 = defLen × (mod/def) 左侧,
                // 绿段 = 白端(右侧)到 defLen 末端(初始条内部), 即以初始条为界, 右侧部分变绿
                int whiteLen = (int) Math.round(defLen * ((double) mod / def));
                int greenLeft = Math.min(barStart + whiteLen, barEnd);
                int greenRight = Math.min(barStart + defLen, barEnd);
                graphics.fill(barStart, y1, greenLeft, y2, color);
                if (greenRight > greenLeft) {
                    graphics.fill(greenLeft, y1, greenRight, y2, 0xFF55FF55);
                }
            } else {
                // 延长: 红条追加在右侧 —— 白段 = defLen(初始, 不变),
                // 红段 = defLen 到 modLen(超满条截断)
                int redLeft = Math.min(barStart + defLen, barEnd);
                int redRight = Math.min(barStart + modLen, barEnd);
                graphics.fill(barStart, y1, redLeft, y2, color);
                if (redRight > redLeft) {
                    graphics.fill(redLeft, y1, redRight, y2, 0xFFFF5555);
                }
            }
            return;
        }
        graphics.fill(x1, y1, x2, y2, color);
    }

    @Inject(method = "getHidePropertyButtonYOffset", at = @At("RETURN"), cancellable = true, remap = false)
    private static void taczfixes$addLimbDamageRowHeight(CallbackInfoReturnable<Integer> cir) {
        if (!Config.LIMB_DAMAGE_SHOW_IN_CHARTS.get()) {
            return;
        }
        // 多留一行给四肢伤害，并补偿 TaCZ 实际绘制行数与声明行数不一致的情况。
        cir.setReturnValue(cir.getReturnValue() + 20);
    }

    @Unique
    private static int taczfixes$actualDiagramRows(ItemStack gunItem) {
        int fallback = 0;
        int actual = 0;
        LocalPlayer player = Minecraft.getInstance().player;
        AttachmentCacheProperty cache = player == null ? null : IGunOperator.fromLivingEntity(player).getCacheProperty();
        GunData gunData = null;
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun != null) {
            gunData = TimelessAPI.getCommonGunIndex(gun.getGunId(gunItem))
                    .map(index -> index.getGunData()).orElse(null);
        }
        for (IAttachmentModifier<?, ?> modifier : AttachmentPropertyManager.getModifiers().values()) {
            fallback += modifier.getDiagramsDataSize();
            try {
                if (gunData != null) {
                    actual += modifier.getPropertyDiagramsData(gunItem, gunData, cache).size();
                }
            } catch (RuntimeException ignored) {
            }
        }
        return Math.max(actual, fallback);
    }

    @Inject(method = "draw", at = @At("TAIL"), remap = false)
    private static void taczfixes$drawLimbDamageRow(GuiGraphics graphics, Font font, int x, int y, CallbackInfo ci) {
        if (!Config.LIMB_DAMAGE_SHOW_IN_CHARTS.get()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack gunItem = player.getMainHandItem();
        if (IGun.getIGunOrNull(gunItem) == null) {
            return;
        }
        ItemStack chartGun = RefitDiagramPreview.previewGun();
        if (chartGun.isEmpty()) {
            chartGun = gunItem;
        }

        int rows = taczfixes$actualDiagramRows(chartGun);
        int lineY = y + 35 + rows * 10;
        int barStartX = x + 83;
        int barEndX = barStartX + 120;
        int nameTextStartX = x + 5;
        int valueTextStartX = x + 210;

        float base = LimbDamageFactorHelper.clamp(LimbDamageFactorHelper.getBaseFactor(chartGun));
        float modified = LimbDamageFactorHelper.getFinalFactor(chartGun);
        float diff = modified - base;
        int defaultLength = barStartX + (int) (120 * (double) base);
        int modifiedLength = barStartX + (int) (120 * (double) modified);

        graphics.drawString(font, Component.translatable("gui.taczfixes.property_diagrams.limb_damage"),
                nameTextStartX, lineY, 0xCCCCCC, false);
        graphics.fill(barStartX, lineY + 2, barEndX, lineY + 6, 0xFF000000);
        graphics.fill(barStartX, lineY + 2, defaultLength, lineY + 6, 0xFFFFFFFF);
        if (diff > 0.0005F) {
            graphics.fill(defaultLength, lineY + 2, Math.min(modifiedLength, barEndX), lineY + 6, 0xFF55FF55);
        } else if (diff < -0.0005F) {
            graphics.fill(Math.max(modifiedLength, barStartX), lineY + 2, defaultLength, lineY + 6, 0xFFFF5555);
        }

        String value = "x" + TACZFIXES_LIMB_CHART_FORMAT.format(modified);
        if (diff > 0.0005F || diff < -0.0005F) {
            double percent = base > 0.0001F ? (double) diff / base * 100.0D : (double) diff * 100.0D;
            String delta = String.format(Locale.ROOT, "%+.1f%%", percent);
            value += (diff > 0.0F ? " \u00a7a(" : " \u00a7c(") + delta + ")";
        }
        graphics.drawString(font, value, valueTextStartX, lineY, 0xCCCCCC, false);
    }
}
