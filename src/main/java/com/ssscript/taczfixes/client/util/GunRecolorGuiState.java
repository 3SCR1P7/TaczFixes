package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.common.network.ClientMessageGunColor;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 改装界面"调色"面板状态: 打开后隐藏其它控件, 左侧显示颜色分组与 H/S/L 滑块。
 * 若打开时选中了装有物理配件的槽位, 则调色对象为该配件, 否则为枪械本体。
 */
public final class GunRecolorGuiState {
    private static final int X = 6;
    private static final int Y = 24;
    private static final int W = 164;
    private static final int ROW_H = 20;
    private static final int SLIDER_H = 18;

    private static boolean open;
    private static int selected = -1;
    private static String targetSlot;
    private static ItemStack targetItem = ItemStack.EMPTY;
    private static final List<AbstractWidget> PANEL = new ArrayList<>();
    private static final List<RecolorClusterButton> CLUSTER_ROWS = new ArrayList<>();
    private static final List<AbstractWidget> ACTION_BUTTONS = new ArrayList<>();
    private static RecolorPanelBackground background;
    private static RecolorSlider clusterSlider;
    private static RecolorSlider hueSlider;
    private static RecolorSlider saturationSlider;
    private static RecolorSlider lightnessSlider;

    private GunRecolorGuiState() {
    }

    public static boolean isOpen() {
        return open;
    }

    public static int selected() {
        return selected;
    }

    public static ItemStack gun() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? ItemStack.EMPTY : player.getMainHandItem();
    }

    /** 当前调色对象: 选中槽位的物理配件, 否则枪械本体。 */
    public static ItemStack target() {
        return targetItem.isEmpty() ? gun() : targetItem;
    }

    public static void open() {
        open = true;
        selected = -1;
        PANEL.clear();
        targetSlot = null;
        targetItem = ItemStack.EMPTY;
        ItemStack gun = gun();
        if (gun.isEmpty()) {
            return;
        }
        String slotId = CustomSlotGuiState.get();
        if (slotId != null) {
            // 自定义槽位: 只对物理安装的配件生效(虚拟原厂件无独立物品可持久化)
            ItemStack physical = CustomSlotStorage.getPhysical(gun, slotId);
            if (!physical.isEmpty()) {
                targetSlot = slotId;
                targetItem = physical;
            }
            return;
        }
        // 标准配件槽: 当前 refit 视图类型上装有配件时, 调色对象为该配件
        AttachmentType type = RefitTransform.getCurrentTransformType();
        if (type != null && type != AttachmentType.NONE) {
            IGun igun = IGun.getIGunOrNull(gun);
            if (igun != null) {
                ItemStack attachment = igun.getAttachment(gun, type);
                if (!attachment.isEmpty()) {
                    targetSlot = "t:" + type.name().toLowerCase(Locale.US);
                    targetItem = attachment;
                }
            }
        }
        GunRecolorManager.applyClusterCountFor(target());
    }

    /** 退出改装界面时调用: 关闭状态并同步到服务端。 */
    public static void closeAndSend() {
        if (!open) {
            return;
        }
        send();
        open = false;
        selected = -1;
        targetSlot = null;
        targetItem = ItemStack.EMPTY;
        PANEL.clear();
        CLUSTER_ROWS.clear();
        ACTION_BUTTONS.clear();
        background = null;
        clusterSlider = null;
        hueSlider = null;
        saturationSlider = null;
        lightnessSlider = null;
    }

    private static void done(Runnable rebuild) {
        closeAndSend();
        rebuild.run();
    }

    private static void send() {
        ItemStack target = target();
        if (target.isEmpty()) {
            return;
        }
        // 空数组也要发送: 否则"全部重置"不会被服务端记录, 之后服务端同步物品时会把旧调色板覆盖回来。
        NetworkHandler.CHANNEL.sendToServer(new ClientMessageGunColor(
                targetSlot == null ? "" : targetSlot, GunRecolorManager.rawTargets(target)));
    }

    /** 把配件调色板写回枪械的自定义槽 NBT(客户端预览用), 服务端由消息写回。 */
    private static void writeBack() {
        if (targetSlot == null || targetItem.isEmpty()) {
            return;
        }
        ItemStack gun = gun();
        if (gun.isEmpty()) {
            return;
        }
        if (targetSlot.startsWith("t:")) {
            // 标准配件槽: 直接写枪械的配件 NBT(绕过 allowAttachment 校验, 保证实时预览)
            String typeName = targetSlot.substring(2).toUpperCase(Locale.US);
            CompoundTag tag = gun.getOrCreateTag();
            CompoundTag attachmentTag = new CompoundTag();
            targetItem.save(attachmentTag);
            tag.put(com.tacz.guns.api.item.nbt.GunItemDataAccessor.GUN_ATTACHMENT_BASE + typeName, attachmentTag);
            return;
        }
        CompoundTag tag = gun.getOrCreateTag();
        CompoundTag slots = tag.contains(CustomSlotStorage.TAG_KEY, 10)
                ? tag.getCompound(CustomSlotStorage.TAG_KEY) : new CompoundTag();
        slots.put(targetSlot, targetItem.save(new CompoundTag()));
        tag.put(CustomSlotStorage.TAG_KEY, slots);
    }

    // ---------------- 面板构建 ----------------

    public static void buildPanel(Screen screen, java.util.function.Consumer<AbstractWidget> adder,
                                  Runnable rebuild) {
        PANEL.clear();
        CLUSTER_ROWS.clear();
        ACTION_BUTTONS.clear();
        hueSlider = null;
        saturationSlider = null;
        lightnessSlider = null;
        selected = -1;
        ItemStack target = target();
        background = new RecolorPanelBackground(X, Y, W, 100);
        add(adder, background);

        // 分组数量滑条: 拖动时实时改变分组数(下方行数/布局每帧动态刷新)
        int range = GunRecolorManager.MAX_CLUSTERS - GunRecolorManager.MIN_CLUSTERS;
        clusterSlider = new RecolorSlider(X + 2, Y + 4, W - 4, SLIDER_H,
                (GunRecolorManager.clusterCount() - GunRecolorManager.MIN_CLUSTERS) / (double) range,
                () -> {
                    int count = clusterCountOf(clusterSlider.value());
                    if (count != GunRecolorManager.clusterCount()) {
                        GunRecolorManager.setClusterCount(target, count);
                        writeBack();
                    }
                });
        clusterSlider.label(value -> Component.translatable("gui.taczfixes.recolor.cluster_count",
                String.valueOf(clusterCountOf(value))).getString());
        add(adder, clusterSlider);

        // 预建最大数量的分组行, 由 layout() 控制可见性与位置
        for (int i = 0; i < GunRecolorManager.MAX_CLUSTERS; i++) {
            RecolorClusterButton row = new RecolorClusterButton(X + 2, Y, W - 4, ROW_H - 2, i);
            add(adder, row);
            CLUSTER_ROWS.add(row);
        }
        hueSlider = new RecolorSlider(X + 2, Y, W - 4, SLIDER_H, 0.0D, GunRecolorGuiState::applySliders)
                .onCommit(GunRecolorGuiState::send);
        saturationSlider = new RecolorSlider(X + 2, Y, W - 4, SLIDER_H, 0.0D, GunRecolorGuiState::applySliders)
                .onCommit(GunRecolorGuiState::send);
        lightnessSlider = new RecolorSlider(X + 2, Y, W - 4, SLIDER_H, 0.0D, GunRecolorGuiState::applySliders)
                .onCommit(GunRecolorGuiState::send);
        add(adder, hueSlider);
        add(adder, saturationSlider);
        add(adder, lightnessSlider);

        int buttonWidth = (W - 4 - 8) / 3;
        add(adder, new RecolorActionButton(X + 2, Y, buttonWidth, 18,
                Component.translatable("gui.taczfixes.recolor.reset_group"), button -> resetSelected()));
        add(adder, new RecolorActionButton(X + 2 + buttonWidth + 4, Y, buttonWidth, 18,
                Component.translatable("gui.taczfixes.recolor.reset_all"), button -> {
            GunRecolorManager.resetAll(target);
            writeBack();
            syncSliders();
            send();
        }));
        add(adder, new RecolorActionButton(X + 2 + 2 * (buttonWidth + 4), Y, buttonWidth, 18,
                Component.translatable("gui.taczfixes.recolor.done"), button -> done(rebuild)));
        for (AbstractWidget widget : PANEL) {
            if (widget instanceof RecolorActionButton) {
                ACTION_BUTTONS.add(widget);
            }
        }

        layout();
        if (!CLUSTER_ROWS.isEmpty() && CLUSTER_ROWS.get(0).visible) {
            select(0);
        }
    }

    private static void add(java.util.function.Consumer<AbstractWidget> adder, AbstractWidget widget) {
        adder.accept(widget);
        PANEL.add(widget);
    }

    /** 按当前分组数刷新面板布局(每帧调用, 支持滑条实时改分组数)。 */
    private static void layout() {
        int rows = Math.min(GunRecolorManager.clustersOf(target()).size(), GunRecolorManager.MAX_CLUSTERS);
        int rowY = Y + 4 + (SLIDER_H + 6) + 14;
        for (int i = 0; i < CLUSTER_ROWS.size(); i++) {
            RecolorClusterButton row = CLUSTER_ROWS.get(i);
            boolean visible = i < rows;
            row.visible = visible;
            row.active = visible;
            row.setY(rowY + i * ROW_H);
        }
        if (selected >= rows) {
            selected = rows > 0 ? 0 : -1;
            syncSliders();
        }
        int sliderY = rowY + rows * ROW_H + 6;
        hueSlider.setY(sliderY);
        saturationSlider.setY(sliderY + SLIDER_H + 3);
        lightnessSlider.setY(sliderY + 2 * (SLIDER_H + 3));
        int buttonY = sliderY + 3 * (SLIDER_H + 3) + 6;
        for (AbstractWidget button : ACTION_BUTTONS) {
            button.setY(buttonY);
        }
        if (background != null) {
            background.setHeight(4 + (SLIDER_H + 6) + 14 + rows * ROW_H + 6 + 3 * (SLIDER_H + 3) + 6 + 24);
        }
    }

    private static int clusterCountOf(double value) {
        double clamped = Math.max(0.0D, Math.min(1.0D, value));
        int range = GunRecolorManager.MAX_CLUSTERS - GunRecolorManager.MIN_CLUSTERS;
        return GunRecolorManager.MIN_CLUSTERS + (int) Math.round(clamped * range);
    }

    /** 打开调色面板时移除其它所有界面控件, 只保留面板控件。 */
    public static void sweep(Screen screen) {
        layout();
        List<? extends GuiEventListener> children = screen.children();
        for (Renderable renderable : new ArrayList<>(screen.renderables)) {
            if (renderable instanceof AbstractWidget widget && !(widget instanceof RecolorPanelWidget)) {
                widget.visible = false;
                widget.active = false;
                screen.renderables.remove(renderable);
                children.remove(widget);
            }
        }
    }

    // ---------------- 分组选择与滑块 ----------------

    public static void select(int index) {
        selected = index;
        syncSliders();
    }

    public static int colorOf(int index) {
        ItemStack target = target();
        List<GunRecolorManager.Cluster> clusters = GunRecolorManager.clustersOf(target);
        if (index < 0 || index >= clusters.size()) {
            return 0x808080;
        }
        int[] targets = GunRecolorManager.targetsOf(target);
        if (index < targets.length && targets[index] >= 0) {
            return targets[index];
        }
        return clusters.get(index).rgb();
    }

    public static Component labelOf(int index) {
        ItemStack target = target();
        List<GunRecolorManager.Cluster> clusters = GunRecolorManager.clustersOf(target);
        int count = 0;
        int total = 0;
        for (GunRecolorManager.Cluster cluster : clusters) {
            total += cluster.count();
            if (index >= 0 && index < clusters.size() && cluster == clusters.get(index)) {
                count = cluster.count();
            }
        }
        double percent = total == 0 ? 0.0D : count * 100.0D / total;
        return Component.translatable("gui.taczfixes.recolor.group", String.valueOf(index + 1),
                String.format(java.util.Locale.US, "%.1f%%", percent));
    }

    private static void resetSelected() {
        if (selected < 0) {
            return;
        }
        GunRecolorManager.resetTarget(target(), selected);
        writeBack();
        syncSliders();
        send();
    }

    private static void syncSliders() {
        if (selected < 0 || hueSlider == null) {
            return;
        }
        List<GunRecolorManager.Cluster> clusters = GunRecolorManager.clustersOf(target());
        if (selected >= clusters.size()) {
            return;
        }
        int rgb = colorOf(selected);
        float[] hsl = GunRecolorManager.rgbToHsl(((rgb >> 16) & 255) / 255.0F,
                ((rgb >> 8) & 255) / 255.0F, (rgb & 255) / 255.0F);
        hueSlider.setSilently(hsl[0]);
        saturationSlider.setSilently(hsl[1]);
        lightnessSlider.setSilently(hsl[2]);
    }

    private static void applySliders() {
        if (selected < 0 || hueSlider == null) {
            return;
        }
        int rgb = GunRecolorManager.hslToRgb((float) hueSlider.value(),
                (float) saturationSlider.value(), (float) lightnessSlider.value());
        GunRecolorManager.setTarget(target(), selected, rgb);
        writeBack();
    }
}
