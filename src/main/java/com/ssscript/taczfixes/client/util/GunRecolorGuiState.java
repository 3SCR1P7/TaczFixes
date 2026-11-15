package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.client.camo.DynamicCamo;
import com.ssscript.taczfixes.common.util.GunColorStorage;
import com.ssscript.taczfixes.common.util.TaczItemCompat;
import com.ssscript.taczfixes.common.util.ItemNbt;
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
    private static RecolorActionButton camoPrev;
    private static RecolorActionButton camoNext;
    private static RecolorCamoButton camoLabel;
    private static List<DynamicCamo.Entry> camoEntries = List.of();
    private static int camoIndex;

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

    /** 若此刻打开调色面板, 调色对象会是谁: 自定义槽物理配件 > 标准槽配件 > 枪械本体。 */
    public static ItemStack resolveTarget(ItemStack gun) {
        if (gun == null || gun.isEmpty()) {
            return ItemStack.EMPTY;
        }
        String slotId = CustomSlotGuiState.get();
        if (slotId != null) {
            ItemStack physical = CustomSlotStorage.getPhysical(gun, slotId);
            return physical.isEmpty() ? gun : physical;
        }
        AttachmentType type = RefitTransform.getCurrentTransformType();
        if (type != null && type != AttachmentType.NONE) {
            IGun igun = IGun.getIGunOrNull(gun);
            if (igun != null) {
                ItemStack attachment = TaczItemCompat.getAttachment(igun, gun, type);
                if (!attachment.isEmpty()) {
                    return attachment;
                }
            }
        }
        return gun;
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
                ItemStack attachment = TaczItemCompat.getAttachment(igun, gun, type);
                if (!attachment.isEmpty()) {
                    targetSlot = "t:" + type.name().toLowerCase(Locale.US);
                    targetItem = attachment;
                }
            }
        }
        GunRecolorManager.applyClusterCountFor(target());
        reloadCamoList();
        camoIndex = camoIndexOfCurrent();
    }

    /** 退出改装界面时调用: 关闭状态并同步到服务端。 */
    public static void closeAndSend() {
        if (!open) {
            return;
        }
        send();
        close();
    }

    /** 直接关闭并重置状态(不发送)。用于安装涂装模块后强制让位。 */
    public static void close() {
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
        camoPrev = null;
        camoNext = null;
        camoLabel = null;
        camoEntries = List.of();
        camoIndex = 0;
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
                targetSlot == null ? "" : targetSlot, GunRecolorManager.rawTargets(target),
                GunColorStorage.getCamo(target)));
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
            CompoundTag tag = ItemNbt.getOrCreate(gun);
            CompoundTag attachmentTag = new CompoundTag();
            targetItem.save(com.ssscript.taczfixes.common.util.RegistryAccessHelper.provider(), attachmentTag);
            tag.put(com.tacz.guns.api.item.nbt.GunItemDataAccessor.GUN_ATTACHMENT_BASE + typeName, attachmentTag);
            return;
        }
        CompoundTag tag = ItemNbt.getOrCreate(gun);
        CompoundTag slots = tag.contains(CustomSlotStorage.TAG_KEY, 10)
                ? tag.getCompound(CustomSlotStorage.TAG_KEY) : new CompoundTag();
        slots.put(targetSlot, ItemNbt.saveStack(targetItem));
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
        camoPrev = null;
        camoNext = null;
        camoLabel = null;
        selected = -1;
        reloadCamoList();
        camoIndex = camoIndexOfCurrent();
        background = new RecolorPanelBackground(X, Y, W, 100);
        add(adder, background);

        // 分组数量滑条: 拖动时实时改变分组数(下方行数/布局每帧动态刷新)
        int range = GunRecolorManager.MAX_CLUSTERS - GunRecolorManager.MIN_CLUSTERS;
        clusterSlider = new RecolorSlider(X + 2, Y + 4, W - 4, SLIDER_H,
                (GunRecolorManager.clusterCount() - GunRecolorManager.MIN_CLUSTERS) / (double) range,
                () -> {
                    int count = clusterCountOf(clusterSlider.value());
                    if (count != GunRecolorManager.clusterCount()) {
                        GunRecolorManager.setClusterCount(target(), count);
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

        // 动态迷彩: ◀ 名称 ▶, 名称按钮点一下切到下一种。枪包里没有迷彩时整行隐藏。
        camoPrev = new RecolorActionButton(X + 2, Y, 18, 18, Component.literal("<"),
                button -> cycleCamo(-1));
        camoLabel = new RecolorCamoButton(X + 2, Y, W - 4, 18, button -> cycleCamo(1));
        camoNext = new RecolorActionButton(X + 2, Y, 18, 18, Component.literal(">"),
                button -> cycleCamo(1));
        camoLabel.setMessage(camoLabelText());
        add(adder, camoPrev);
        add(adder, camoLabel);
        add(adder, camoNext);

        int buttonWidth = (W - 4 - 8) / 3;
        add(adder, new RecolorActionButton(X + 2, Y, buttonWidth, 18,
                Component.translatable("gui.taczfixes.recolor.reset_group"), button -> resetSelected()));
        add(adder, new RecolorActionButton(X + 2 + buttonWidth + 4, Y, buttonWidth, 18,
                Component.translatable("gui.taczfixes.recolor.reset_all"), button -> resetAll()));
        add(adder, new RecolorActionButton(X + 2 + 2 * (buttonWidth + 4), Y, buttonWidth, 18,
                Component.translatable("gui.taczfixes.recolor.done"), button -> done(rebuild)));
        for (AbstractWidget widget : PANEL) {
            // 迷彩行的左右箭头单独定位, 不参与底部按钮行的纵向排布。
            if (widget instanceof RecolorActionButton && widget != camoPrev && widget != camoNext) {
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
        int camoY = sliderY + 3 * (SLIDER_H + 3) + 6;
        boolean camoVisible = !camoEntries.isEmpty();
        if (camoPrev != null && camoLabel != null && camoNext != null) {
            int labelWidth = W - 4 - 42;
            camoPrev.setX(X + 2);
            camoLabel.setX(X + 2 + 21);
            camoLabel.setWidth(labelWidth);
            camoNext.setX(X + 2 + 21 + labelWidth + 3);
            camoPrev.setY(camoY);
            camoLabel.setY(camoY);
            camoNext.setY(camoY);
            camoPrev.visible = camoVisible;
            camoLabel.visible = camoVisible;
            camoNext.visible = camoVisible;
            camoPrev.active = camoVisible;
            camoLabel.active = camoVisible;
            camoNext.active = camoVisible;
        }
        int buttonY = camoY + (camoVisible ? 22 : 0) + 6;
        for (AbstractWidget button : ACTION_BUTTONS) {
            button.setY(buttonY);
        }
        if (background != null) {
            background.setHeight(buttonY + 18 + 6 - Y);
        }
    }

    private static void reloadCamoList() {
        camoEntries = DynamicCamo.entries();
    }

    private static int camoIndexOfCurrent() {
        DynamicCamo.Entry entry = DynamicCamo.byId(GunColorStorage.getCamo(target()));
        return entry == null ? 0 : entry.index();
    }

    private static Component camoLabelText() {
        String name;
        if (camoIndex <= 0) {
            name = Component.translatable("gui.taczfixes.recolor.camo_none").getString();
        } else {
            DynamicCamo.Entry entry = DynamicCamo.byIndex(camoIndex);
            name = entry == null ? String.valueOf(camoIndex)
                    : Component.translatable(entry.nameKey()).getString();
        }
        return Component.translatable("gui.taczfixes.recolor.camo", name);
    }

    private static void cycleCamo(int delta) {
        int count = camoEntries.size() + 1;
        if (count <= 1) {
            return;
        }
        camoIndex = ((camoIndex + delta) % count + count) % count;
        DynamicCamo.Entry entry = camoIndex <= 0 ? null : DynamicCamo.byIndex(camoIndex);
        GunColorStorage.setCamo(target(), entry == null ? "" : entry.id().toString());
        writeBack();
        if (camoLabel != null) {
            camoLabel.setMessage(camoLabelText());
        }
        send();
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

    private static void resetAll() {
        ItemStack target = target();
        if (target.isEmpty()) {
            return;
        }
        GunRecolorManager.resetAll(target);
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
        float[] hsl = GunRecolorManager.rgbToHsv(((rgb >> 16) & 255) / 255.0F,
                ((rgb >> 8) & 255) / 255.0F, (rgb & 255) / 255.0F);
        hueSlider.setSilently(hsl[0]);
        saturationSlider.setSilently(hsl[1]);
        lightnessSlider.setSilently(hsl[2]);
    }

    private static void applySliders() {
        if (selected < 0 || hueSlider == null) {
            return;
        }
        int rgb = GunRecolorManager.hsvToRgb((float) hueSlider.value(),
                (float) saturationSlider.value(), (float) lightnessSlider.value());
        GunRecolorManager.setTarget(target(), selected, rgb);
        writeBack();
    }
}
