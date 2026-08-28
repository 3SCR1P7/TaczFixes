package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 改装界面数据图表: 跑射延迟(sprint_time)条目.
 * 原版仅显示基础值 %.2fs; taczfixes 显示 "当前值 (差值)", 柱状图以绿色表示
 * 缩短部分、红色表示延长部分(与通用属性条一致: 缩短为正向)。
 * 实现: 拦截 getSprintTime 计算修饰值; 拦截文本 String.format 替换为带差值的字符串;
 * 拦截 base 条绘制 m_280509_ 改画 default+diff 两段。
 */
@Mixin(targets = "com.tacz.guns.client.gui.components.refit.GunPropertyDiagrams", remap = false)
public class MixinGunPropertyDiagrams {

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
                    target = "Lnet/minecraft/client/gui/GuiGraphics;m_280509_(IIIII)V", remap = false), remap = false)
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
}
