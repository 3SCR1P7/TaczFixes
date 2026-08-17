package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.Config;
import com.ssscript.taczfixes.data.CustomSlotDefinition;
import com.ssscript.taczfixes.data.CustomSlotManager;
import com.ssscript.taczfixes.network.ClientMessageLoadRefitPreset;
import com.ssscript.taczfixes.network.NetworkHandler;
import com.ssscript.taczfixes.util.CustomSlotStorage;
import com.ssscript.taczfixes.util.LiberateCompat;
import com.ssscript.taczfixes.util.RefitPresetStorage;
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

@Mixin(GunRefitScreen.class)
public abstract class MixinGunRefitScreenPresetButtons extends Screen {

    protected MixinGunRefitScreenPresetButtons(LocalPlayer player) {
        super(Component.literal(""));
    }

    @Inject(method = "addAttachmentTypeButtons", at = @At("TAIL"), remap = false)
    private void taczfixes$addPresetButtons(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack gunStack = player.getMainHandItem();
        IGun igun = IGun.getIGunOrNull(gunStack);
        if (igun == null) return;
        ResourceLocation gunId = igun.getGunId(gunStack);
        Map<String, ResourceLocation> preset = RefitPresetStorage.get(gunId);
        boolean hasPreset = preset != null && !preset.isEmpty();
        int y = this.height - 28;
        int right = this.width - 20;
        Component saveLabel = Component.translatable("gui.taczfixes.refit_preset.save");
        int saveW = this.font.width(saveLabel) + 10;
        if (hasPreset) {
            Component loadLabel = Component.translatable("gui.taczfixes.refit_preset.load");
            int loadW = this.font.width(loadLabel) + 10;
            int loadX = right - saveW - loadW - 4;
            this.addRenderableWidget(Button.builder(loadLabel, b -> loadPreset(gunId))
                    .bounds(loadX, y, loadW, 18).build());
        }
        int saveX = right - saveW;
        this.addRenderableWidget(Button.builder(saveLabel, b -> savePreset(gunStack, gunId))
                .bounds(saveX, y, saveW, 18).build());
        taczfixes$ensureSearchBox();
    }

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
            taczfixes$searchBox = new EditBox(this.font, 11, this.height - 28, 110, 18, Component.literal(""));
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

    private void savePreset(ItemStack gunStack, ResourceLocation gunId) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return;
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
        RefitPresetStorage.save(gunId, preset);
        playRefitSound(ResourceLocation.tryParse("tacz:attachments/attachment_general_uninstall"));
        this.init();
        showToast(Component.translatable("gui.taczfixes.refit_preset.saved"));
    }

    private void loadPreset(ResourceLocation gunId) {
        Map<String, ResourceLocation> preset = RefitPresetStorage.get(gunId);
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
    }

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
    private void taczfixes$renderToast(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (taczfixes$searchBox != null) {
            long now = System.currentTimeMillis();
            if (now - taczfixes$lastTickMs >= 50) {
                taczfixes$searchBox.tick();
                taczfixes$lastTickMs = now;
            }
        }
        taczfixes$drawToast(graphics);
    }

    @Inject(method = "m_7379_", at = @At("TAIL"), remap = false)
    private void taczfixes$onCloseClearSearch(CallbackInfo ci) {
        if (taczfixes$searchBox != null) {
            taczfixes$searchBox.setValue("");
        }
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