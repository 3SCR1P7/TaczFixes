package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.client.util.CustomSlotButton;
import com.ssscript.taczfixes.client.util.CustomSlotEntry;
import com.ssscript.taczfixes.client.util.CustomSlotGuiState;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.components.refit.GunAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.RefitUnloadButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

@Mixin(com.tacz.guns.client.gui.GunRefitScreen.class)
public abstract class MixinGunRefitScreenCustomSlot extends Screen {

    protected MixinGunRefitScreenCustomSlot(LocalPlayer player) {
        super(Component.literal(""));
    }

    /** 自定义槽位/默认槽位可见性入口: 重排所有槽位列, 并插入自定义槽按钮。
     *  hidden_unavailable=true 时未启用的自定义槽隐藏, false 时以 tacz 不可用图标显示;
     *  hidden_unavailable_default=true 时未开放的默认槽隐藏, 其余槽位紧凑排列。 */
    @Inject(method = "addAttachmentTypeButtons", at = @At("TAIL"), remap = false)
    private void taczfixes$addCustomSlotButtons(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack gunStack = player.getMainHandItem();
        IGun igun = IGun.getIGunOrNull(gunStack);
        if (igun == null) return;
        ResourceLocation gunId = igun.getGunId(gunStack);
        Map<String, CustomSlotDefinition> slots = CustomSlotManager.getSlots(gunId);
        boolean hiddenDefault = CustomSlotManager.isHiddenUnavailableDefault(gunId);
        if (slots.isEmpty() && !hiddenDefault && !com.ssscript.taczfixes.client.util.RefitSlotLayout.scaled()) {
            // 无自定义槽、全局未开启隐藏默认槽且槽位尺寸为默认时保持 tacz 原生布局
            return;
        }
        if (CustomSlotGuiState.get() != null && !slots.containsKey(CustomSlotGuiState.get())) {
            CustomSlotGuiState.reset();
        }
        boolean hiddenUnavailable = CustomSlotManager.isHiddenUnavailable(gunId);

        java.util.List<AttachmentType> order = new java.util.ArrayList<>();
        for (AttachmentType t : AttachmentType.values()) {
            if (t != AttachmentType.NONE) {
                order.add(t);
            }
        }
        EnumMap<AttachmentType, java.util.List<CustomSlotEntry>> grouped = new EnumMap<>(AttachmentType.class);
        for (AttachmentType t : order) {
            grouped.put(t, new java.util.ArrayList<>());
        }
        java.util.List<CustomSlotEntry> customList = new java.util.ArrayList<>();
        for (Map.Entry<String, CustomSlotDefinition> entry : slots.entrySet()) {
            CustomSlotDefinition def = entry.getValue();
            if (def == null) continue;
            boolean available = CustomSlotManager.isDependenceMet(gunId, gunStack, def)
                    && !CustomSlotManager.isConflictOccupied(gunId, gunStack, def);
            if (!available) {
                if (entry.getKey().equals(CustomSlotGuiState.get())) {
                    CustomSlotGuiState.reset();
                }
                if (hiddenUnavailable) continue;
            }
            CustomSlotEntry se = new CustomSlotEntry(entry.getKey(), def, !available);
            if (def.isCustom()) {
                customList.add(se);
                continue;
            }
            AttachmentType t;
            try {
                t = AttachmentType.valueOf(def.type.toUpperCase(Locale.US));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (t == AttachmentType.NONE) {
                customList.add(se);
                continue;
            }
            grouped.get(t).add(se);
        }

        EnumMap<AttachmentType, GunAttachmentSlot> defaultSlots = new EnumMap<>(AttachmentType.class);
        for (Renderable r : new java.util.ArrayList<>(this.renderables)) {
            if (r instanceof GunAttachmentSlot slot && slot.getType() != AttachmentType.NONE) {
                defaultSlots.putIfAbsent(slot.getType(), slot);
            }
        }

        int slotSize = com.ssscript.taczfixes.client.util.RefitSlotLayout.size();
        int y = 10;
        int col = 0;
        int selectedX = 0;
        int selectedY = 0;
        int selectedDefaultX = Integer.MIN_VALUE;
        boolean hasSelected = false;
        for (AttachmentType t : order) {
            GunAttachmentSlot defSlot = defaultSlots.get(t);
            if (defSlot != null) {
                if (hiddenDefault && !defSlot.isAllow()) {
                    defSlot.visible = false;
                    defSlot.active = false;
                    this.renderables.remove(defSlot);
                    this.children().remove(defSlot);
                } else {
                    int x = com.ssscript.taczfixes.client.util.RefitSlotLayout.slotX(this.width, col);
                    defSlot.setX(x);
                    defSlot.setY(y);
                    defSlot.setWidth(slotSize);
                    defSlot.setHeight(slotSize);
                    if (RefitTransform.getCurrentTransformType() == t) {
                        selectedDefaultX = x;
                        for (Renderable r : new java.util.ArrayList<>(this.renderables)) {
                            if (r instanceof RefitUnloadButton unload && !ownUnloadButtons.contains(unload)) {
                                unload.setX(x + com.ssscript.taczfixes.client.util.RefitSlotLayout.scaled(5));
                                unload.setY(y + slotSize + 2);
                            }
                        }
                    }
                    col++;
                }
            }
            for (CustomSlotEntry se : grouped.get(t)) {
                int x = com.ssscript.taczfixes.client.util.RefitSlotLayout.slotX(this.width, col);
                CustomSlotButton button = createCustomSlotButton(x, y, se, gunStack);
                if (button != null) {
                    if (se.id.equals(CustomSlotGuiState.get())) {
                        selectedX = x;
                        selectedY = y;
                        hasSelected = true;
                    }
                    this.addRenderableWidget(button);
                }
                col++;
            }
        }
        for (CustomSlotEntry se : customList) {
            int x = com.ssscript.taczfixes.client.util.RefitSlotLayout.slotX(this.width, col);
            CustomSlotButton button = createCustomSlotButton(x, y, se, gunStack);
            if (button != null) {
                if (se.id.equals(CustomSlotGuiState.get())) {
                    selectedX = x;
                    selectedY = y;
                    hasSelected = true;
                }
                this.addRenderableWidget(button);
            }
            col++;
        }
        // 配件槽变大/变小时, 候选(variant/adapter)按钮列整体下移/上移, 避免与槽位重合
        if (com.ssscript.taczfixes.client.util.RefitSlotLayout.scaled()) {
            AttachmentType selectedType = RefitTransform.getCurrentTransformType();
            if (selectedType != AttachmentType.NONE) {
                int typeIndex = 0;
                for (AttachmentType t : order) {
                    if (t == selectedType) break;
                    typeIndex++;
                }
                int candidateBaseX = this.width - 30 - 18 * typeIndex - 60;
                int delta = slotSize - com.tacz.guns.client.gui.GunRefitScreen.SLOT_SIZE;
                for (Renderable r : new java.util.ArrayList<>(this.renderables)) {
                    if (r instanceof com.tacz.guns.client.gui.components.FlatColorButton button
                            && button.getX() == candidateBaseX) {
                        if (selectedDefaultX != Integer.MIN_VALUE) {
                            button.setX(selectedDefaultX + slotSize - 78);
                        }
                        button.setY(button.getY() + delta);
                    }
                }
            }
        }
        String selected = CustomSlotGuiState.get();
        // 候选/适配器按钮块位置已按槽位尺寸调整, 同步候选栏起始高度, 避免重合或距离过远
        int candidateBottom = 50;
        for (Renderable r : this.renderables) {
            if (r instanceof com.tacz.guns.client.gui.components.FlatColorButton button && button.visible) {
                candidateBottom = Math.max(candidateBottom, button.getY() + button.getHeight() + 4);
            }
        }
        this.inventoryAttachmentStartY = candidateBottom;
        if (hasSelected && selected != null && !CustomSlotStorage.get(gunStack, selected).isEmpty()) {
            String unloadSlot = selected;
            RefitUnloadButton unload = new RefitUnloadButton(
                    selectedX + com.ssscript.taczfixes.client.util.RefitSlotLayout.scaled(5),
                    selectedY + slotSize + 2,
                    btn -> com.ssscript.taczfixes.common.network.NetworkHandler.CHANNEL
                            .sendToServer(new com.ssscript.taczfixes.common.network.ClientMessageUnloadCustomSlot(unloadSlot)));
            ownUnloadButtons.add(unload);
            this.addRenderableWidget(unload);
        }
        if (CustomSlotGuiState.get() != null) {
            this.renderables.removeIf(r -> r instanceof RefitUnloadButton && !ownUnloadButtons.contains(r));
        }
        taczfixes$addCustomLaserSliders(gunStack, gunId);
    }

    /** 自定义 laser 槽位选中且装有可调色激光时, 补上 TACZ 只给默认 laser 槽位创建的调色滑条。 */
    @Unique
    private void taczfixes$addCustomLaserSliders(ItemStack gunStack, ResourceLocation gunId) {
        String slotId = CustomSlotGuiState.get();
        if (slotId == null) return;
        CustomSlotDefinition def = CustomSlotManager.getSlot(gunId, slotId);
        if (def == null || def.isCustom()) return;
        AttachmentType defType;
        try {
            defType = AttachmentType.valueOf(def.type.toUpperCase(Locale.US));
        } catch (IllegalArgumentException ex) {
            return;
        }
        if (defType != AttachmentType.LASER) return;
        for (net.minecraft.client.gui.components.events.GuiEventListener child : this.children()) {
            if (child instanceof com.tacz.guns.client.gui.components.refit.HSVSliderGroup.LaserColorSlider) return;
        }
        ItemStack item = CustomSlotStorage.get(gunStack, slotId);
        IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
        if (attachment == null) return;
        com.tacz.guns.client.resource.pojo.display.LaserConfig laserConfig = com.tacz.guns.api.TimelessAPI
                .getClientAttachmentIndex(attachment.getAttachmentId(item))
                .map(index -> index.getLaserConfig()).orElse(null);
        if (laserConfig == null || !laserConfig.canEdit()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        com.tacz.guns.client.gui.components.refit.HSVSliderGroup group =
                new com.tacz.guns.client.gui.components.refit.HSVSliderGroup(this.width - 140, this.height - 64, 120, 16, inventory, inventory.selected, AttachmentType.LASER);
        this.addRenderableWidget(group.getHueSlider());
        this.addRenderableWidget(group.getSaturationSlider());
    }

    @Unique
    private CustomSlotButton createCustomSlotButton(int x, int y, CustomSlotEntry entry, ItemStack gunStack) {
        CustomSlotDefinition def = entry.def;
        final String slotId = entry.id;
        final AttachmentType view;
        try {
            view = def.isCustom() ? AttachmentType.NONE : AttachmentType.valueOf(def.type.toUpperCase(Locale.US));
        } catch (IllegalArgumentException ex) {
            return null;
        }
        CustomSlotButton button = new CustomSlotButton(x, y, def, slotId, gunStack, entry.unavailable, btn -> {
            if (entry.unavailable) return;
            RefitTransform.changeRefitScreenView(view);
            CustomSlotGuiState.set(slotId);
            this.init();
        });
        int size = com.ssscript.taczfixes.client.util.RefitSlotLayout.size();
        button.setWidth(size);
        button.setHeight(size);
        if (entry.unavailable) {
            button.active = false;
        }
        return button;
    }

    @Inject(method = "m_7379_", at = @At("TAIL"), remap = false)
    private void taczfixes$onClose(CallbackInfo ci) {
        sendCustomSlotLaserColor();
        // 记录退出时选中的自定义槽: 收枪缓动期间仍需按该槽的 refit 路径(含 refit_view 回退)解析,
        // 否则会退回 tacz 原始路径(null 时定位矩阵为单位阵, 枪械从 0,0,0 缓动回)。
        CustomSlotGuiState.beginRefitViewTransition();
        CustomSlotGuiState.reset();
    }

    @Unique
    private void sendCustomSlotLaserColor() {
        String slotId = CustomSlotGuiState.get();
        if (slotId == null) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack gun = player.getMainHandItem();
        IGun igun = IGun.getIGunOrNull(gun);
        if (igun == null) return;
        ItemStack item = CustomSlotStorage.get(gun, slotId);
        IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
        if (attachment == null) return;
        if (!attachment.hasCustomLaserColor(item)) return;
        com.ssscript.taczfixes.common.network.NetworkHandler.CHANNEL.sendToServer(
                new com.ssscript.taczfixes.common.network.ClientMessageCustomSlotLaserColor(slotId,
                        attachment.getLaserColor(item)));
    }

    private final java.util.Set<RefitUnloadButton> ownUnloadButtons = new java.util.HashSet<>();

    @org.spongepowered.asm.mixin.Shadow(remap = false)
    private int inventoryAttachmentStartY;
}
