package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.Config;
import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.common.network.ClientMessageLoadRefitPreset;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.ssscript.taczfixes.common.util.LiberateCompat;
import com.ssscript.taczfixes.common.util.RefitPresetStorage;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.components.refit.InventoryAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.RefitTurnPageButton;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.config.common.GunConfig;
import com.tacz.guns.network.message.ClientMessageRefitGun;
import com.tacz.guns.sound.SoundManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 在改装界面(GunRefitScreen)上叠加"保存/加载改装方案"弹窗。
 * 弹窗以子组件(Button/EditBox/StringWidget)形式加入当前屏幕，由 Screen
 * 默认事件循环分发鼠标/键盘输入，不退出改装界面。模式：
 * NONE 无弹窗；SAVE_NAME 输入方案名；SAVE_OVERWRITE 覆盖确认；
 * LIST 方案列表(加载/导出/导入)；IMPORT 输入改装码。
 */
@Mixin(GunRefitScreen.class)
public abstract class MixinGunRefitScreenPresetButtons extends Screen {

    protected MixinGunRefitScreenPresetButtons(LocalPlayer player) {
        super(Component.literal(""));
    }

    // ==================== 改装方案弹窗 ====================

    private static final int OVERLAY_NONE = 0;
    private static final int OVERLAY_SAVE_NAME = 1;
    private static final int OVERLAY_SAVE_OVERWRITE = 2;
    private static final int OVERLAY_LIST = 3;
    private static final int OVERLAY_IMPORT = 4;

    @Unique
    private int taczfixes$overlayMode = OVERLAY_NONE;
    @Unique
    private ItemStack taczfixes$overlayGunStack;
    @Unique
    private ResourceLocation taczfixes$overlayGunId;
    @Unique
    private Map<String, ResourceLocation> taczfixes$overlayPreset;
    @Unique
    private String taczfixes$overlayPendingName;
    @Unique
    private Component taczfixes$overlayError;
    @Unique
    private final List<Renderable> taczfixes$overlayWidgets = new ArrayList<>();

    @Inject(method = "addAttachmentTypeButtons", at = @At("TAIL"), remap = false)
    private void taczfixes$addPresetButtons(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack gunStack = player.getMainHandItem();
        IGun igun = IGun.getIGunOrNull(gunStack);
        if (igun == null) return;
        ResourceLocation gunId = igun.getGunId(gunStack);
        int y = this.height - 28;
        int right = this.width - 20;
        Component saveLabel = Component.translatable("gui.taczfixes.refit_preset.save");
        int saveW = this.font.width(saveLabel) + 10;
        Component loadLabel = Component.translatable("gui.taczfixes.refit_preset.load");
        int loadW = this.font.width(loadLabel) + 10;
        int loadX = right - saveW - loadW - 4;
        this.addRenderableWidget(new com.ssscript.taczfixes.client.util.TransparentButton(loadLabel,
                b -> openPresetList(gunStack, gunId)).bounds(loadX, y, loadW, 18));
        int saveX = right - saveW;
        this.addRenderableWidget(new com.ssscript.taczfixes.client.util.TransparentButton(saveLabel,
                b -> openPresetSave(gunStack, gunId)).bounds(saveX, y, saveW, 18));
        taczfixes$ensureSearchBox();
        taczfixes$rebuildOverlay();
    }

    // ---------- 打开/关闭弹窗 ----------

    private void openPresetSave(ItemStack gunStack, ResourceLocation gunId) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return;
        taczfixes$overlayGunStack = gunStack;
        taczfixes$overlayGunId = gunId;
        taczfixes$overlayPreset = collectCurrentPreset(gunStack, gunId, gun);
        taczfixes$overlayPendingName = null;
        taczfixes$overlayError = null;
        taczfixes$overlayMode = OVERLAY_SAVE_NAME;
        taczfixes$rebuildOverlay();
    }

    private void openPresetList(ItemStack gunStack, ResourceLocation gunId) {
        taczfixes$overlayGunStack = gunStack;
        taczfixes$overlayGunId = gunId;
        taczfixes$overlayError = null;
        taczfixes$overlayMode = OVERLAY_LIST;
        taczfixes$rebuildOverlay();
    }

    private void openPresetImport() {
        taczfixes$overlayMode = OVERLAY_IMPORT;
        taczfixes$overlayError = null;
        taczfixes$rebuildOverlay();
    }

    private void closeOverlay() {
        taczfixes$removeOverlayWidgets();
        taczfixes$overlayMode = OVERLAY_NONE;
        taczfixes$overlayError = null;
        taczfixes$overlayPendingName = null;
    }

    private void taczfixes$removeOverlayWidgets() {
        for (Renderable r : taczfixes$overlayWidgets) {
            if (r instanceof GuiEventListener listener) {
                this.removeWidget(listener);
            } else {
                this.renderables.remove(r);
            }
        }
        taczfixes$overlayWidgets.clear();
    }

    /** 按当前模式重建弹窗子组件（叠加在当前屏幕上）。 */
    private void taczfixes$rebuildOverlay() {
        taczfixes$removeOverlayWidgets();
        int mode = taczfixes$overlayMode;
        if (mode == OVERLAY_NONE) return;
        int cx = this.width / 2;
        int cy = this.height / 2;
        if (mode == OVERLAY_SAVE_NAME) {
            EditBox box = new EditBox(this.font, cx - 110, cy - 20, 220, 18, Component.literal(""));
            box.setMaxLength(32);
            if (taczfixes$overlayPendingName != null) {
                box.setValue(taczfixes$overlayPendingName);
            }
            Button saveBtn = new com.ssscript.taczfixes.client.util.TransparentButton(
                    Component.translatable("gui.taczfixes.refit_preset.save_confirm"),
                    b -> doSavePreset()).bounds(cx + 5, cy + 14, 100, 20);
            saveBtn.active = false;
            box.setResponder(s -> saveBtn.active = !s.trim().isEmpty());
            this.addRenderableWidget(box);
            this.addRenderableWidget(saveBtn);
            this.setInitialFocus(box);
            taczfixes$overlayWidgets.add(box);
            taczfixes$overlayWidgets.add(saveBtn);
        } else if (mode == OVERLAY_SAVE_OVERWRITE) {
            Button saveBtn = new com.ssscript.taczfixes.client.util.TransparentButton(
                    Component.translatable("gui.taczfixes.refit_preset.save_confirm"),
                    b -> doSavePreset()).bounds(cx + 5, cy + 14, 100, 20);
            this.addRenderableWidget(saveBtn);
            taczfixes$overlayWidgets.add(saveBtn);
        } else if (mode == OVERLAY_IMPORT) {
            EditBox box = new EditBox(this.font, cx - 120, cy - 20, 240, 18, Component.literal(""));
            box.setMaxLength(4096);
            Button importBtn = new com.ssscript.taczfixes.client.util.TransparentButton(
                    Component.translatable("gui.taczfixes.refit_preset.import_confirm"),
                    b -> doImportPreset()).bounds(cx + 5, cy + 14, 110, 20);
            importBtn.active = false;
            box.setResponder(s -> importBtn.active = !s.trim().isEmpty());
            this.addRenderableWidget(box);
            this.addRenderableWidget(importBtn);
            this.setInitialFocus(box);
            taczfixes$overlayWidgets.add(box);
            taczfixes$overlayWidgets.add(importBtn);
        } else if (mode == OVERLAY_LIST) {
            List<String> names = RefitPresetStorage.getPresetNames(taczfixes$overlayGunId);
            int panelTop = cy - 180;
            int panelBottom = cy + 180;
            int rowH = 22;
            int y = panelTop + 24;
            for (String name : names) {
                if (y + 18 > panelBottom - 34) break;
                // 宽度上限 110: 避免标签右缘越过加载按钮左缘(cx-40)吞掉点击
                int nameW = Math.min(this.font.width(name), 110);
                net.minecraft.client.gui.components.StringWidget label =
                        new net.minecraft.client.gui.components.StringWidget(
                                Component.literal(name), this.font);
                label.alignLeft().setColor(0xFFFFFF);
                label.setX(cx - 160);
                label.setY(y + 5);
                label.setWidth(nameW);
                taczfixes$overlayWidgets.add(label);
                this.addRenderableWidget(label);
                Button loadBtn = new com.ssscript.taczfixes.client.util.TransparentButton(
                                Component.translatable("gui.taczfixes.refit_preset.load_preset"),
                                b -> doLoadPreset(name))
                        .bounds(cx + 20, y, 40, 16);
                taczfixes$overlayWidgets.add(loadBtn);
                this.addRenderableWidget(loadBtn);
                Button exportBtn = new com.ssscript.taczfixes.client.util.TransparentButton(
                                Component.translatable("gui.taczfixes.refit_preset.export"),
                                b -> doExportPreset(name))
                        .bounds(cx + 62, y, 40, 16);
                taczfixes$overlayWidgets.add(exportBtn);
                this.addRenderableWidget(exportBtn);
                Button deleteBtn = new com.ssscript.taczfixes.client.util.TransparentButton(
                                Component.translatable("gui.taczfixes.refit_preset.delete"),
                                b -> doDeletePreset(name))
                        .bounds(cx + 104, y, 40, 16);
                taczfixes$overlayWidgets.add(deleteBtn);
                this.addRenderableWidget(deleteBtn);
                y += rowH;
            }
            // 导入新方案按钮
            Button importNewBtn = new com.ssscript.taczfixes.client.util.TransparentButton(
                            Component.translatable("gui.taczfixes.refit_preset.import_new"),
                            b -> openPresetImport())
                    .bounds(cx - 100, panelBottom - 30, 200, 20);
            taczfixes$overlayWidgets.add(importNewBtn);
            this.addRenderableWidget(importNewBtn);
        }
        // 取消/关闭按钮
        if (mode == OVERLAY_SAVE_NAME || mode == OVERLAY_SAVE_OVERWRITE || mode == OVERLAY_IMPORT) {
            Button cancel = new com.ssscript.taczfixes.client.util.TransparentButton(
                    Component.translatable("gui.taczfixes.refit_preset.cancel"),
                    b -> closeOverlay()).bounds(cx - 105, cy + 14, 100, 20);
            this.addRenderableWidget(cancel);
            taczfixes$overlayWidgets.add(cancel);
        }
    }

    // ---------- 弹窗动作 ----------

    private void doSavePreset() {
        if (taczfixes$overlayGunId == null || taczfixes$overlayPreset == null) return;
        if (taczfixes$overlayMode == OVERLAY_SAVE_OVERWRITE) {
            if (taczfixes$overlayPendingName != null) {
                RefitPresetStorage.savePreset(taczfixes$overlayGunId,
                        taczfixes$overlayPendingName, taczfixes$overlayPreset);
                showToast(Component.translatable("gui.taczfixes.refit_preset.saved"));
                closeOverlay();
            }
            return;
        }
        // SAVE_NAME：从输入框取名字
        String name = null;
        for (Renderable r : taczfixes$overlayWidgets) {
            if (r instanceof EditBox box) {
                String v = box.getValue().trim();
                if (!v.isEmpty()) name = v;
                break;
            }
        }
        if (name == null) return;
        if (RefitPresetStorage.hasPreset(taczfixes$overlayGunId, name)) {
            taczfixes$overlayPendingName = name;
            taczfixes$overlayMode = OVERLAY_SAVE_OVERWRITE;
            taczfixes$rebuildOverlay();
            return;
        }
        RefitPresetStorage.savePreset(taczfixes$overlayGunId, name, taczfixes$overlayPreset);
        showToast(Component.translatable("gui.taczfixes.refit_preset.saved"));
        closeOverlay();
    }

    private void doLoadPreset(String name) {
        if (taczfixes$overlayGunId == null) return;
        Map<String, ResourceLocation> preset = RefitPresetStorage.getPreset(taczfixes$overlayGunId, name);
        if (preset == null || preset.isEmpty()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            ResourceLocation soundId = player.getRandom().nextBoolean()
                    ? ResourceLocation.tryParse("tacz:attachments/attachment_general_a")
                    : ResourceLocation.tryParse("tacz:attachments/attachment_general_b");
            playRefitSound(soundId);
        }
        NetworkHandler.CHANNEL.sendToServer(new ClientMessageLoadRefitPreset(preset));
        showToast(Component.translatable("gui.taczfixes.refit_preset.loaded"));
        closeOverlay();
    }

    private void doExportPreset(String name) {
        if (taczfixes$overlayGunId == null) return;
        Map<String, ResourceLocation> preset = RefitPresetStorage.getPreset(taczfixes$overlayGunId, name);
        if (preset == null || preset.isEmpty()) return;
        String code = RefitPresetStorage.exportCode(taczfixes$overlayGunId, name, preset);
        Minecraft.getInstance().keyboardHandler.setClipboard(code);
        showToast(Component.translatable("gui.taczfixes.refit_preset.exported"));
    }

    private void doDeletePreset(String name) {
        if (taczfixes$overlayGunId == null) return;
        RefitPresetStorage.removePreset(taczfixes$overlayGunId, name);
        showToast(Component.translatable("gui.taczfixes.refit_preset.deleted"));
        taczfixes$rebuildOverlay();
    }

    private void doImportPreset() {
        String code = null;
        for (Renderable r : taczfixes$overlayWidgets) {
            if (r instanceof EditBox box) {
                code = box.getValue().trim();
                break;
            }
        }
        if (code == null || code.isEmpty()) return;
        RefitPresetStorage.ImportedCode imported = RefitPresetStorage.importCode(code);
        if (imported == null) {
            taczfixes$overlayError = Component.translatable("gui.taczfixes.refit_preset.import_invalid");
            taczfixes$rebuildOverlay();
            return;
        }
        if (!imported.gunId().equals(taczfixes$overlayGunId)) {
            taczfixes$overlayError = Component.translatable("gui.taczfixes.refit_preset.import_wrong_gun");
            taczfixes$rebuildOverlay();
            return;
        }
        // gunId 匹配：进入保存流程（预填方案名）
        taczfixes$overlayPreset = imported.slots();
        taczfixes$overlayPendingName = imported.name();
        taczfixes$overlayMode = OVERLAY_SAVE_NAME;
        taczfixes$rebuildOverlay();
    }

    private static Map<String, ResourceLocation> collectCurrentPreset(ItemStack gunStack, ResourceLocation gunId, IGun gun) {
        Map<String, ResourceLocation> preset = new LinkedHashMap<>();
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            ItemStack item = gun.getAttachment(gunStack, type);
            IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
            if (attachment != null) {
                preset.put(type.name(), attachment.getAttachmentId(item));
            }
        }
        for (Map.Entry<String, CustomSlotDefinition> entry : CustomSlotManager.getSlots(gunId).entrySet()) {
            ResourceLocation attachmentId = CustomSlotStorage.getAttachmentId(gunStack, entry.getKey());
            if (attachmentId != null) {
                preset.put(entry.getKey(), attachmentId);
            }
        }
        return preset;
    }

    // ==================== 搜索框（既有功能） ====================

    @Inject(method = "addInventoryAttachmentButtons", at = @At("TAIL"), remap = false)
    private void taczfixes$onInventoryListRebuild(CallbackInfo ci) {
        if (taczfixes$searchBox != null && !taczfixes$searchBox.getValue().trim().isEmpty()) {
            taczfixes$rebuildSearchList();
            taczfixes$appliedSearch = taczfixes$searchBox.getValue() + "|" + RefitTransform.getCurrentTransformType();
        }
    }

    @Unique
    private EditBox taczfixes$searchBox;
    @Unique
    private String taczfixes$lastSearchText = "";
    @Unique
    private String taczfixes$appliedSearch = "";
    @Unique
    private long taczfixes$lastTickMs;
    @Unique
    private int taczfixes$searchPage;
    @Unique
    private boolean taczfixes$searchActive;
    @Unique
    private final List<Renderable> taczfixes$searchWidgets = new ArrayList<>();

    @Unique
    private void taczfixes$ensureSearchBox() {
        if (taczfixes$searchBox == null) {
            taczfixes$searchBox = new EditBox(this.font, 11, this.height - 28, 90, 16, Component.literal(""));
            taczfixes$searchBox.setMaxLength(64);
        } else {
            taczfixes$searchBox.setY(this.height - 28);
        }
        this.addRenderableWidget(taczfixes$searchBox);
    }

    @Unique
    private void taczfixes$updateSearch() {
        String query = taczfixes$searchBox.getValue();
        AttachmentType type = RefitTransform.getCurrentTransformType();
        String key = query + "|" + type;
        if (query.equals(taczfixes$lastSearchText) && key.equals(taczfixes$appliedSearch)) {
            if (taczfixes$searchActive && !query.trim().isEmpty() && type != AttachmentType.NONE) {
                boolean stale = taczfixes$searchWidgets.isEmpty();
                if (!stale) {
                    for (Renderable r : taczfixes$searchWidgets) {
                        if (!this.renderables.contains(r)) {
                            stale = true;
                            break;
                        }
                    }
                }
                if (stale) {
                    taczfixes$rebuildSearchList();
                }
            }
            return;
        }
        taczfixes$lastSearchText = query;
        if (query.trim().isEmpty()) {
            if (taczfixes$searchActive) {
                taczfixes$searchActive = false;
                taczfixes$searchPage = 0;
                this.init();
            }
        } else if (type != AttachmentType.NONE) {
            taczfixes$rebuildSearchList();
        }
        taczfixes$appliedSearch = key;
    }

    @Unique
    private void taczfixes$rebuildSearchList() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        AttachmentType currentType = RefitTransform.getCurrentTransformType();
        if (currentType == AttachmentType.NONE) return;
        String query = taczfixes$searchBox.getValue().trim();
        if (query.isEmpty()) return;
        List<GuiEventListener> toRemove = new ArrayList<>();
        for (Renderable renderable : this.renderables) {
            if (renderable instanceof InventoryAttachmentSlot || renderable instanceof RefitTurnPageButton) {
                toRemove.add((GuiEventListener) renderable);
            }
        }
        for (GuiEventListener renderable : toRemove) {
            this.removeWidget(renderable);
        }
        taczfixes$searchWidgets.clear();
        Inventory inv = player.getInventory();
        Inventory sourceInv = LiberateCompat.isLiberated(player) ? LiberateCompat.getVirtualInventory(inv) : inv;
        ItemStack gunStack = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(gunStack);
        List<Integer> matched = new ArrayList<>();
        String[] keywords = query.toLowerCase(Locale.ROOT).split("\\s+");
        for (int i = 0; i < sourceInv.getContainerSize(); i++) {
            ItemStack stack = sourceInv.getItem(i);
            IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
            if (attachment == null) continue;
            if (attachment.getType(stack) != currentType) continue;
            if (gun != null && !gun.allowAttachment(gunStack, stack)) continue;
            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            boolean allMatch = true;
            for (String keyword : keywords) {
                if (!name.contains(keyword)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                matched.add(i);
            }
        }
        int x = this.width - 30;
        int y = 50;
        int totalPages = Math.max(1, (matched.size() + 7) / 8);
        if (taczfixes$searchPage > totalPages - 1) taczfixes$searchPage = totalPages - 1;
        if (taczfixes$searchPage < 0) taczfixes$searchPage = 0;
        int start = taczfixes$searchPage * 8;
        int end = Math.min(start + 8, matched.size());
        for (int k = start; k < end; k++) {
            int index = matched.get(k);
            InventoryAttachmentSlot slot = new InventoryAttachmentSlot(x, y, index, sourceInv,
                    b -> taczfixes$installAttachment(sourceInv, b));
            this.addRenderableWidget(slot);
            taczfixes$searchWidgets.add(slot);
            y += 18;
        }
        taczfixes$searchActive = true;
        if (totalPages > 1) {
            if (taczfixes$searchPage > 0) {
                RefitTurnPageButton prev = new RefitTurnPageButton(x, 40, true,
                        b -> {
                            taczfixes$searchPage--;
                            taczfixes$rebuildSearchList();
                        });
                this.addRenderableWidget(prev);
                taczfixes$searchWidgets.add(prev);
            }
            if (taczfixes$searchPage < totalPages - 1) {
                RefitTurnPageButton next = new RefitTurnPageButton(x, 196, false,
                        b -> {
                            taczfixes$searchPage++;
                            taczfixes$rebuildSearchList();
                        });
                this.addRenderableWidget(next);
                taczfixes$searchWidgets.add(next);
            }
        }
    }

    @Unique
    private void taczfixes$installAttachment(Inventory inv, Button button) {
        int index = ((InventoryAttachmentSlot) button).getSlotIndex();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        int gunSlot = player.getInventory().selected;
        for (int i = 0; i < 9; i++) {
            if (IGun.getIGunOrNull(player.getInventory().getItem(i)) != null) {
                gunSlot = i;
                break;
            }
        }
        SoundPlayManager.playerRefitSound(inv.getItem(index), player, SoundManager.INSTALL_SOUND);
        com.tacz.guns.network.NetworkHandler.CHANNEL.sendToServer(new ClientMessageRefitGun(index, gunSlot, RefitTransform.getCurrentTransformType()));
    }

    // ==================== 渲染 / 提示 ====================

    @Unique
    private Component taczfixes$toastMessage = Component.literal("");
    @Unique
    private long taczfixes$toastUntil;

    @Unique
    private void showToast(Component message) {
        taczfixes$toastMessage = message;
        taczfixes$toastUntil = System.currentTimeMillis() + Config.REFIT_TOAST_DURATION_MS.get();
    }

    @Inject(method = "m_88315_", at = @At("HEAD"), remap = false)
    private void taczfixes$preRenderSearch(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (taczfixes$searchBox != null) {
            taczfixes$updateSearch();
        }
    }

    @Inject(method = "m_88315_", at = @At("TAIL"), remap = false)
    private void taczfixes$renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (taczfixes$searchBox != null) {
            long now = System.currentTimeMillis();
            if (now - taczfixes$lastTickMs >= 50) {
                taczfixes$searchBox.tick();
                taczfixes$lastTickMs = now;
            }
        }
        taczfixes$drawOverlay(graphics);
        taczfixes$drawToast(graphics);
    }

    /** 绘制弹窗标题（无面板背景、无全屏遮罩）。子组件按钮由 super.render 绘制在其上。 */
    @Unique
    private void taczfixes$drawOverlay(GuiGraphics graphics) {
        int mode = taczfixes$overlayMode;
        if (mode == OVERLAY_NONE) return;
        int cx = this.width / 2;
        int cy = this.height / 2;
        int panelH = mode == OVERLAY_LIST ? 360 : 110;
        int y0 = cy - panelH / 2;
        Component title;
        if (mode == OVERLAY_SAVE_NAME) {
            title = Component.translatable("gui.taczfixes.refit_preset.enter_name");
        } else if (mode == OVERLAY_SAVE_OVERWRITE) {
            title = Component.translatable("gui.taczfixes.refit_preset.overwrite_confirm");
        } else if (mode == OVERLAY_IMPORT) {
            title = Component.translatable("gui.taczfixes.refit_preset.enter_code");
        } else {
            title = Component.translatable("gui.taczfixes.refit_preset.list_title");
        }
        graphics.drawCenteredString(this.font, title, cx, y0 + 8, 0xFFFFFF);
        if (taczfixes$overlayError != null && mode == OVERLAY_IMPORT) {
            graphics.drawCenteredString(this.font, taczfixes$overlayError, cx, y0 + 22, 0xFF5555);
        }
        if (mode == OVERLAY_SAVE_OVERWRITE && taczfixes$overlayPendingName != null) {
            graphics.drawCenteredString(this.font,
                    Component.literal("\"" + taczfixes$overlayPendingName + "\""),
                    cx, y0 + 24, 0xFFAAAAAA);
        }
    }

    @Inject(method = "m_7379_", at = @At("TAIL"), remap = false)
    private void taczfixes$onCloseClearSearch(CallbackInfo ci) {
        if (taczfixes$searchBox != null) {
            taczfixes$searchBox.setValue("");
        }
        closeOverlay();
    }

    @Unique
    private void taczfixes$drawToast(GuiGraphics graphics) {
        long now = System.currentTimeMillis();
        if (now >= taczfixes$toastUntil) {
            return;
        }
        float remain = taczfixes$toastUntil - now;
        int fadeMs = Math.max(1, Config.REFIT_TOAST_FADE_MS.get());
        int alpha = (int) (255 * Math.min(1.0f, remain / fadeMs));
        int color = (alpha << 24) | 0xFFFFFF;
        Font font = Minecraft.getInstance().font;
        int x = (this.width - font.width(taczfixes$toastMessage)) / 2;
        int y = this.height / 2 - 4;
        graphics.drawString(font, taczfixes$toastMessage, x, y, color);
    }

    private void playRefitSound(ResourceLocation soundId) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        SoundPlayManager.playClientSound(player, soundId, 1.0f, 1.0f,
                GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
    }
}
