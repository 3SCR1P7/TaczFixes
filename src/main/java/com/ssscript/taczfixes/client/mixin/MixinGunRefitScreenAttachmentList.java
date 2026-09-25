package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.network.ClientMessageInstallCustomSlot;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.client.util.CustomSlotGuiState;
import com.ssscript.taczfixes.client.util.TaczFixesClientState;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.ssscript.taczfixes.common.util.LiberateCompat;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.components.refit.InventoryAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.RefitTurnPageButton;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.sound.SoundManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(GunRefitScreen.class)
public abstract class MixinGunRefitScreenAttachmentList extends Screen {

    protected MixinGunRefitScreenAttachmentList(LocalPlayer player) {
        super(Component.literal(""));
    }

    @Shadow(remap = false) public int inventoryAttachmentStartY;

    @Inject(method = "addInventoryAttachmentButtons", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$customInventoryButtons(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack gunStack = player.getMainHandItem();
        IGun igun = IGun.getIGunOrNull(gunStack);
        if (igun == null) return;
        ResourceLocation gunId = igun.getGunId(gunStack);
        String selected = CustomSlotGuiState.get();
        boolean virtual = com.ssscript.taczfixes.common.util.VirtualAttachments.isActive(player);

        if (selected == null) {
            // 虚拟配件模式: 标准槽候选栏显示所有可用配件
            if (!virtual) return;
            com.tacz.guns.api.item.attachment.AttachmentType type =
                    com.tacz.guns.client.animation.screen.RefitTransform.getCurrentTransformType();
            if (type == com.tacz.guns.api.item.attachment.AttachmentType.NONE) return;
            taczfixes$renderVirtualEntries(ci, player, gunStack, null,
                    com.ssscript.taczfixes.common.util.VirtualAttachments.availableForStandard(gunStack, igun, type),
                    type.name());
            return;
        }

        CustomSlotDefinition def = CustomSlotManager.getSlot(gunId, selected);
        if (def == null) return;
        List<ResourceLocation> builtinIds = new ArrayList<>();
        // show_icon 为 true 时在改装界面显示原厂候选; 虚拟配件模式下始终显示
        if (def.builtin_attachments != null && (virtual || def.builtin_attachments.isShowIcon())) {
            for (String entry : def.getBuiltinAttachmentIds()) {
                ResourceLocation id = ResourceLocation.tryParse(entry);
                if (id != null) builtinIds.add(id);
            }
        }
        if (virtual) {
            // 虚拟配件模式: 原厂候选 + 所有可用配件, 安装不消耗、拆下不返还
            List<ResourceLocation> ids = new ArrayList<>(builtinIds);
            for (ResourceLocation id : com.ssscript.taczfixes.common.util.VirtualAttachments
                    .availableForCustom(gunStack, igun, selected, def)) {
                if (!ids.contains(id)) ids.add(id);
            }
            taczfixes$renderVirtualEntries(ci, player, gunStack, def, ids, selected);
            return;
        }
        Inventory inventory = LiberateCompat.getVirtualInventory(player.getInventory());
        int x = com.ssscript.taczfixes.client.util.RefitSlotLayout.firstX(this.width);
        int slotSize = com.ssscript.taczfixes.client.util.RefitSlotLayout.size();
        int y0 = this.inventoryAttachmentStartY;
        List<Integer> matched = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
            if (attachment == null) continue;
            if (!taczfixes$allowAttachmentForSlot(gunStack, selected, stack)) continue;
            if (!CustomSlotManager.matchesSlot(def, gunId, attachment.getAttachmentId(stack), attachment.getType(stack))) {
                continue;
            }
            matched.add(i);
        }
        int perPage = 8;
        int entryCount = builtinIds.size() + matched.size();
        int totalPages = Math.max(1, (entryCount + perPage - 1) / perPage);
        int page = Math.max(0, Math.min(CustomSlotGuiState.getPage(), totalPages - 1));
        CustomSlotGuiState.setPage(page);
        int start = page * perPage;
        int shown = 0;
        for (int k = start; k < entryCount && shown < perPage; k++) {
            int slotY = y0 + shown * slotSize;
            if (k < builtinIds.size()) {
                final ResourceLocation builtinId = builtinIds.get(k);
                final ItemStack builtinStack = CustomSlotManager.buildBuiltinItem(builtinId);
                if (builtinStack.isEmpty()) {
                    shown++;
                    continue;
                }
                com.ssscript.taczfixes.client.util.BuiltinAttachmentSlot builtinSlot =
                        new com.ssscript.taczfixes.client.util.BuiltinAttachmentSlot(
                                x, slotY, builtinStack, btn -> {
                            SoundPlayManager.playerRefitSound(builtinStack, player, SoundManager.INSTALL_SOUND);
                            NetworkHandler.CHANNEL.sendToServer(
                                    new com.ssscript.taczfixes.common.network.ClientMessageInstallCustomSlotBuiltin(
                                            selected, builtinId));
                        });
                builtinSlot.setWidth(slotSize);
                builtinSlot.setHeight(slotSize);
                this.addRenderableWidget(builtinSlot);
                shown++;
                continue;
            }
            final int index = matched.get(k - builtinIds.size());
            final ItemStack stack = inventory.getItem(index);
            InventoryAttachmentSlot slot = new InventoryAttachmentSlot(x, slotY, index, inventory,
                    btn -> {
                        ItemStack installed = CustomSlotStorage.getPhysical(gunStack, selected);
                        int oldConsume = installed.isEmpty() ? 0 : AttachmentTaczFixesManager.getRefitPointConsume(installed);
                        Integer total = TaczFixesDataManager.getGunRefitPoint(gunStack);
                        if (total != null) {
                            int used = AttachmentTaczFixesManager.getRefitPointUsed(gunStack);
                            int add = AttachmentTaczFixesManager.getRefitPointConsume(stack);
                            if (used + add > total + oldConsume) {
                                btn.setFocused(false);
                                TaczFixesClientState.markRejectFocusClear();
                                SoundPlayManager.playerRefitSound(stack, player, SoundManager.INSTALL_SOUND);
                                return;
                            }
                        }
                        SoundPlayManager.playerRefitSound(stack, player, SoundManager.INSTALL_SOUND);
                        NetworkHandler.CHANNEL.sendToServer(new ClientMessageInstallCustomSlot(index, selected));
                    });
            slot.setWidth(slotSize);
            slot.setHeight(slotSize);
            this.addRenderableWidget(slot);
            shown++;
        }
        if (entryCount > perPage) {
            if (page > 0) {
                RefitTurnPageButton prev = new RefitTurnPageButton(x, y0 - 10, true,
                        btn -> {
                            CustomSlotGuiState.setPage(page - 1);
                            this.init();
                        });
                prev.setWidth(slotSize);
                this.addRenderableWidget(prev);
            }
            if (entryCount > (page + 1) * perPage) {
                RefitTurnPageButton next = new RefitTurnPageButton(x, y0 + slotSize * 8 + 2, false,
                        btn -> {
                            CustomSlotGuiState.setPage(page + 1);
                            this.init();
                        });
                next.setWidth(slotSize);
                this.addRenderableWidget(next);
            }
        }
        ci.cancel();
    }

    /** 虚拟配件模式下渲染候选列表: 点击后免费安装(仍受改装点限制), 不消耗、不返还。 */
    @Unique
    private void taczfixes$renderVirtualEntries(CallbackInfo ci, LocalPlayer player, ItemStack gunStack,
                                                CustomSlotDefinition def, List<ResourceLocation> ids, String slotKey) {
        int x = com.ssscript.taczfixes.client.util.RefitSlotLayout.firstX(this.width);
        int slotSize = com.ssscript.taczfixes.client.util.RefitSlotLayout.size();
        int y0 = this.inventoryAttachmentStartY;
        int perPage = 8;
        int totalPages = Math.max(1, (ids.size() + perPage - 1) / perPage);
        int page = Math.max(0, Math.min(CustomSlotGuiState.getPage(), totalPages - 1));
        CustomSlotGuiState.setPage(page);
        int start = page * perPage;
        int shown = 0;
        for (int k = start; k < ids.size() && shown < perPage; k++) {
            ResourceLocation id = ids.get(k);
            ItemStack stack = CustomSlotManager.buildBuiltinItem(id);
            if (stack.isEmpty()) {
                shown++;
                continue;
            }
            int slotY = y0 + shown * slotSize;
            com.ssscript.taczfixes.client.util.BuiltinAttachmentSlot virtualSlot =
                    new com.ssscript.taczfixes.client.util.BuiltinAttachmentSlot(x, slotY, stack,
                    btn -> {
                        Integer total = TaczFixesDataManager.getGunRefitPoint(gunStack);
                        if (total != null) {
                            int used = AttachmentTaczFixesManager.getRefitPointUsed(gunStack);
                            int oldConsume = taczfixes$refitPointInSlot(gunStack, slotKey);
                            int add = def != null && CustomSlotManager.isBuiltinCandidate(def, id)
                                    ? 0 : AttachmentTaczFixesManager.getRefitPointConsume(stack);
                            if (used + add > total + oldConsume) {
                                btn.setFocused(false);
                                TaczFixesClientState.markRejectFocusClear();
                                SoundPlayManager.playerRefitSound(stack, player, SoundManager.INSTALL_SOUND);
                                return;
                            }
                        }
                        SoundPlayManager.playerRefitSound(stack, player, SoundManager.INSTALL_SOUND);
                        NetworkHandler.CHANNEL.sendToServer(
                                new com.ssscript.taczfixes.common.network.ClientMessageInstallVirtualAttachment(
                                        slotKey, id));
                    });
            virtualSlot.setWidth(slotSize);
            virtualSlot.setHeight(slotSize);
            this.addRenderableWidget(virtualSlot);
            shown++;
        }
        if (ids.size() > perPage) {
            if (page > 0) {
                RefitTurnPageButton prev = new RefitTurnPageButton(x, y0 - 10, true,
                        btn -> {
                            CustomSlotGuiState.setPage(page - 1);
                            this.init();
                        });
                prev.setWidth(slotSize);
                this.addRenderableWidget(prev);
            }
            if (ids.size() > (page + 1) * perPage) {
                RefitTurnPageButton next = new RefitTurnPageButton(x, y0 + slotSize * 8 + 2, false,
                        btn -> {
                            CustomSlotGuiState.setPage(page + 1);
                            this.init();
                        });
                next.setWidth(slotSize);
                this.addRenderableWidget(next);
            }
        }
        ci.cancel();
    }

    @Unique
    private static int taczfixes$refitPointInSlot(ItemStack gunStack, String slotKey) {
        ItemStack stack = CustomSlotStorage.getPhysical(gunStack, slotKey);
        if (stack.isEmpty()) {
            try {
                AttachmentType type = AttachmentType.valueOf(slotKey.toUpperCase(java.util.Locale.US));
                IGun gun = IGun.getIGunOrNull(gunStack);
                stack = gun == null ? ItemStack.EMPTY : gun.getAttachment(gunStack, type);
            } catch (IllegalArgumentException ex) {
                return 0;
            }
        }
        return AttachmentTaczFixesManager.getRefitPointConsume(stack);
    }

    /** 非自定义槽/非虚拟模式下, 按配置的槽位尺寸重排 tacz 原生候选列表。 */
    @Inject(method = "addInventoryAttachmentButtons", at = @At("TAIL"), remap = false)
    private void taczfixes$reflowVanillaCandidateList(CallbackInfo ci) {
        if (!com.ssscript.taczfixes.client.util.RefitSlotLayout.scaled()) return;
        if (CustomSlotGuiState.get() != null) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (com.ssscript.taczfixes.common.util.VirtualAttachments.isActive(player)) return;
        int x = com.ssscript.taczfixes.client.util.RefitSlotLayout.firstX(this.width);
        int size = com.ssscript.taczfixes.client.util.RefitSlotLayout.size();
        int y0 = this.inventoryAttachmentStartY;
        List<InventoryAttachmentSlot> slots = new ArrayList<>();
        for (net.minecraft.client.gui.components.Renderable renderable : this.renderables) {
            if (renderable instanceof InventoryAttachmentSlot slot) {
                slots.add(slot);
            }
        }
        slots.sort(java.util.Comparator.comparingInt(InventoryAttachmentSlot::getY));
        for (int i = 0; i < slots.size(); i++) {
            InventoryAttachmentSlot slot = slots.get(i);
            slot.setX(x);
            slot.setY(y0 + i * size);
            slot.setWidth(size);
            slot.setHeight(size);
        }
        for (net.minecraft.client.gui.components.Renderable renderable
                : new ArrayList<>(this.renderables)) {
            if (renderable instanceof RefitTurnPageButton page) {
                page.setX(x);
                page.setY(page.getY() < y0 ? y0 - 10 : y0 + size * 8 + 2);
                page.setWidth(size);
            }
        }
    }

    /**
     * 按槽位适配器过滤配件(镜像 SlotAdapterHelper.allowsAttachment 语义):
     * 无适配器 → 仅直连允许; 有适配器 → 适配器允许。
     * 原版 igun.allowAttachment 读共享 SlotAdapters NBT(标准槽位),
     * 自定义槽位需读取按槽位存储的适配器。
     */
    @Unique
    private static boolean taczfixes$allowAttachmentForSlot(ItemStack gunStack, String slotId, ItemStack stack) {
        IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
        if (attachment == null) return false;
        ResourceLocation attachmentId = attachment.getAttachmentId(stack);
        if (attachmentId == null) return false;
        ResourceLocation adapterId = CustomSlotStorage.getAdapter(gunStack, slotId);
        if (adapterId == null) {
            return com.tacz.guns.util.SlotAdapterHelper.allowsDirectAttachment(gunStack, attachmentId);
        }
        return com.tacz.guns.api.TimelessAPI.getCommonSlotAdapterIndex(adapterId)
                .map(index -> index.allowsAttachment(attachmentId))
                .orElse(false);
    }
}