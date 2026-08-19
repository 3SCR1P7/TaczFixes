package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.Config;
import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.client.util.CustomSlotGuiState;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.components.refit.RefitUnloadButton;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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

@Mixin(GunRefitScreen.class)
public abstract class MixinGunRefitScreenCustomSlot extends Screen {

    protected MixinGunRefitScreenCustomSlot(LocalPlayer player) {
        super(Component.literal(""));
    }

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
        if (slots.isEmpty()) {
            return;
        }
        if (CustomSlotGuiState.get() != null && !slots.containsKey(CustomSlotGuiState.get())) {
            CustomSlotGuiState.reset();
        }
        java.util.List<AttachmentType> order = new java.util.ArrayList<>();
        for (AttachmentType t : AttachmentType.values()) {
            if (t != AttachmentType.NONE) {
                order.add(t);
            }
        }
        EnumMap<AttachmentType, Integer> cnt = new EnumMap<>(AttachmentType.class);
        EnumMap<AttachmentType, java.util.List<Map.Entry<String, CustomSlotDefinition>>> grouped = new EnumMap<>(AttachmentType.class);
        for (AttachmentType t : order) {
            cnt.put(t, 0);
            grouped.put(t, new java.util.ArrayList<>());
        }
        java.util.List<Map.Entry<String, CustomSlotDefinition>> customList = new java.util.ArrayList<>();
        for (Map.Entry<String, CustomSlotDefinition> entry : slots.entrySet()) {
            CustomSlotDefinition def = entry.getValue();
            if (!CustomSlotManager.isDependenceMet(gunId, gunStack, def)) continue;
            if (CustomSlotManager.isConflictOccupied(gunId, gunStack, def)) continue;
            if (def.isCustom()) {
                customList.add(entry);
                continue;
            }
            AttachmentType t;
            try {
                t = AttachmentType.valueOf(def.type.toUpperCase(Locale.US));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (t == AttachmentType.NONE) {
                customList.add(entry);
                continue;
            }
            grouped.get(t).add(entry);
            cnt.put(t, cnt.get(t) + 1);
        }
        int totalCustom = 0;
        for (int c : cnt.values()) {
            totalCustom += c;
        }
        if (totalCustom == 0 && customList.isEmpty()) {
            return;
        }
        EnumMap<AttachmentType, Integer> leftCnt = new EnumMap<>(AttachmentType.class);
        int acc = 0;
        for (AttachmentType t : order) {
            leftCnt.put(t, acc);
            acc += cnt.get(t);
        }
        int startX = this.width - 30;
        for (net.minecraft.client.gui.components.Renderable r : new java.util.ArrayList<>(this.renderables)) {
            if (r instanceof com.tacz.guns.client.gui.components.refit.GunAttachmentSlot slot) {
                AttachmentType t = slot.getType();
                Integer lc = leftCnt.get(t);
                if (lc != null && lc > 0) {
                    slot.setX(slot.getX() - 18 * lc);
                }
            }
        }
        AttachmentType view = RefitTransform.getCurrentTransformType();
        Integer viewShift = leftCnt.get(view);
        if (view != AttachmentType.NONE && viewShift != null && viewShift > 0) {
            for (net.minecraft.client.gui.components.Renderable r : new java.util.ArrayList<>(this.renderables)) {
                if (r instanceof RefitUnloadButton unload && !ownUnloadButtons.contains(unload)) {
                    unload.setX(unload.getX() - 18 * viewShift);
                }
            }
        }
        int y = 10;
        int selectedX = 0;
        int selectedY = 0;
        boolean hasSelected = false;
        for (int i = 0; i < order.size(); i++) {
            AttachmentType t = order.get(i);
            int k = 0;
            for (Map.Entry<String, CustomSlotDefinition> entry : grouped.get(t)) {
                k++;
                int x = startX - 18 * (i + leftCnt.get(t)) - 18 * k;
                CustomSlotButton button = createCustomSlotButton(x, y, entry, gunStack);
                if (button == null) continue;
                if (entry.getKey().equals(CustomSlotGuiState.get())) {
                    selectedX = x;
                    selectedY = y;
                    hasSelected = true;
                }
                this.addRenderableWidget(button);
            }
        }
        int last = order.size() - 1;
        AttachmentType lastT = order.get(last);
        int k = 0;
        for (Map.Entry<String, CustomSlotDefinition> entry : customList) {
            k++;
            int x = startX - 18 * (last + leftCnt.get(lastT)) - 18 * cnt.get(lastT) - 18 * k;
            CustomSlotButton button = createCustomSlotButton(x, y, entry, gunStack);
            if (button == null) continue;
            if (entry.getKey().equals(CustomSlotGuiState.get())) {
                selectedX = x;
                selectedY = y;
                hasSelected = true;
            }
            this.addRenderableWidget(button);
        }
        String selected = CustomSlotGuiState.get();
        if (hasSelected && selected != null && !CustomSlotStorage.get(gunStack, selected).isEmpty()) {
            String unloadSlot = selected;
            RefitUnloadButton unload = new RefitUnloadButton(selectedX + 5, selectedY + 20,
                    btn -> com.ssscript.taczfixes.common.network.NetworkHandler.CHANNEL
                            .sendToServer(new com.ssscript.taczfixes.common.network.ClientMessageUnloadCustomSlot(unloadSlot)));
            ownUnloadButtons.add(unload);
            this.addRenderableWidget(unload);
        }
        if (CustomSlotGuiState.get() != null) {
            this.renderables.removeIf(r -> r instanceof RefitUnloadButton && !ownUnloadButtons.contains(r));
        }
    }

    @Unique
    private CustomSlotButton createCustomSlotButton(int x, int y, Map.Entry<String, CustomSlotDefinition> entry,
                                                    ItemStack gunStack) {
        CustomSlotDefinition def = entry.getValue();
        final String slotId = entry.getKey();
        final AttachmentType view;
        try {
            view = def.isCustom() ? AttachmentType.NONE : AttachmentType.valueOf(def.type.toUpperCase(Locale.US));
        } catch (IllegalArgumentException ex) {
            return null;
        }
        CustomSlotButton button = new CustomSlotButton(x, y, def, slotId, gunStack, btn -> {
            RefitTransform.changeRefitScreenView(view);
            CustomSlotGuiState.set(slotId);
            this.init();
        });
        return button;
    }

    @Inject(method = "onClose", at = @At("TAIL"))
    private void taczfixes$onClose(CallbackInfo ci) {
        sendCustomSlotLaserColor();
        CustomSlotGuiState.reset();
        CustomSlotGuiState.clearViewTransition();
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

    private static class CustomSlotButton extends Button implements com.tacz.guns.client.gui.components.refit.IStackTooltip {
        private final CustomSlotDefinition definition;
        private final String slotId;
        private final ItemStack gunStack;
        private final com.ssscript.taczfixes.client.util.GunPackIconLoader.LoadedIcon iconTexture;
        private boolean selected;

        CustomSlotButton(int x, int y, CustomSlotDefinition definition, String slotId, ItemStack gunStack, OnPress onPress) {
            super(x, y, 18, 18, Component.literal(""), onPress, DEFAULT_NARRATION);
            this.definition = definition;
            this.slotId = slotId;
            this.gunStack = gunStack;
            com.ssscript.taczfixes.client.util.GunPackIconLoader.LoadedIcon icon = null;
            if (definition.isCustom() && definition.slot != null) {
                ResourceLocation tex = ResourceLocation.tryParse(definition.slot);
                if (tex != null) {
                    icon = com.ssscript.taczfixes.client.util.GunPackIconLoader.load(tex);
                }
            }
            this.iconTexture = icon;
        }

        @Override
        public void renderTooltip(java.util.function.Consumer<ItemStack> consumer) {
            if (!isHoveredOrFocused()) return;
            ItemStack item = CustomSlotStorage.get(gunStack, slotId);
            if (!item.isEmpty()) {
                consumer.accept(item);
            }
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.selected = slotId.equals(CustomSlotGuiState.get());
            int x = getX();
            int y = getY();
            boolean hovered = isHoveredOrFocused();
            if (hovered) {
                Font font = Minecraft.getInstance().font;
                Component name = getSlotName();
                int nameX = x + (this.width - font.width(name)) / 2;
                int nameY = y + 20;
                if (this.selected && !CustomSlotStorage.get(gunStack, slotId).isEmpty()) {
                    nameY = y + 30;
                }
                graphics.drawString(font, name, nameX, nameY, 0xFFFFFF);
            }
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            if (this.selected || hovered) {
                graphics.blit(GunRefitScreen.SLOT_TEXTURE, x, y, 0, 0, this.width, this.height, 18, 18);
            } else {
                graphics.blit(GunRefitScreen.SLOT_TEXTURE, x + 1, y + 1, 1, 1, this.width - 2, this.height - 2, 18, 18);
            }
            ItemStack item = CustomSlotStorage.get(gunStack, slotId);
            if (definition.isCustom()) {
                if (item.isEmpty() && this.iconTexture != null) {
                    graphics.blit(this.iconTexture.texture(),
                            x + 2, y + 2, 14, 14,
                            0f, 0f,
                            this.iconTexture.width(), this.iconTexture.height(),
                            this.iconTexture.width(), this.iconTexture.height());
                }
            } else {
                try {
                    int offset = GunRefitScreen.getSlotTextureXOffset(gunStack,
                            AttachmentType.valueOf(definition.type.toUpperCase()));
                    if (item.isEmpty() && offset >= 0 && offset != 192) {
                        graphics.blit(GunRefitScreen.ICONS_TEXTURE, x + 2, y + 2,
                                this.width - 4, this.height - 4,
                                (float) offset, 0f, 32, 32,
                                GunRefitScreen.getSlotsTextureWidth(), 32);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (!item.isEmpty()) {
                graphics.renderItem(item, x + 1, y + 1);
            }
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }

        private Component getSlotName() {
            if (definition.name != null && !definition.name.isEmpty()) {
                return Component.translatable(definition.name);
            }
            if (!definition.isCustom()) {
                return Component.translatable("tooltip.tacz.attachment." + definition.type.toLowerCase(Locale.US));
            }
            return Component.literal(slotId);
        }
    }
}
