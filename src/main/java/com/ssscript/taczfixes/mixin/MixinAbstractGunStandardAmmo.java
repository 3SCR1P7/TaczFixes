package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.util.GunEnchantmentHelper;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractGunItem.class)
public class MixinAbstractGunStandardAmmo {
    private static final Logger LOGGER = LogManager.getLogger("taczfixes-standard-ammo");

    @Inject(method = "findAndExtractInventoryAmmo", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$extractOwnThenAnyAmmo(IItemHandler itemHandler, ItemStack gunItem, int needAmmoCount,
                                                 CallbackInfoReturnable<Integer> cir) {
        if (needAmmoCount <= 0) {
            return;
        }
        AbstractGunItem self = (AbstractGunItem) (Object) this;
        if (self.useInventoryAmmo(gunItem)) {
            return;
        }
        int level = GunEnchantmentHelper.getLevel(gunItem, TaczFixesMod.STANDARD_AMMO_ENCHANTMENT.get());
        if (level <= 0) {
            return;
        }
        try {
            int needLeft = needAmmoCount;
            int anySlot = -1;
            for (int i = 0; i < itemHandler.getSlots() && needLeft > 0; i++) {
                ItemStack stack = itemHandler.getStackInSlot(i);
                if (stack.isEmpty()) {
                    continue;
                }
                Item item = stack.getItem();
                boolean own;
                if (item instanceof IAmmo ammo) {
                    own = ammo.isAmmoOfGun(gunItem, stack);
                } else if (item instanceof IAmmoBox ammoBox) {
                    own = ammoBox.isAmmoBoxOfGun(gunItem, stack);
                    if (!own && anySlot < 0 && ammoBox.getAmmoCount(stack) > 0) {
                        anySlot = i;
                    }
                } else {
                    continue;
                }
                if (own) {
                    needLeft -= taczfixes$drainStack(itemHandler, i, needLeft);
                } else if (anySlot < 0) {
                    anySlot = i;
                }
            }
            if (needLeft > 0 && anySlot >= 0) {
                needLeft -= taczfixes$drainStack(itemHandler, anySlot, needLeft);
            }
            cir.setReturnValue(needAmmoCount - needLeft);
        } catch (Exception e) {
            LOGGER.error("[标准弹药] 抽取弹药时发生异常，已回退原版逻辑", e);
        }
    }

    private int taczfixes$drainStack(IItemHandler itemHandler, int slot, int need) {
        ItemStack stack = itemHandler.getStackInSlot(slot);
        if (stack.isEmpty()) {
            return 0;
        }
        if (stack.getItem() instanceof IAmmo) {
            return itemHandler.extractItem(slot, need, false).getCount();
        }
        if (stack.getItem() instanceof IAmmoBox ammoBox) {
            int count = ammoBox.getAmmoCount(stack);
            if (count <= 0) {
                return 0;
            }
            int take = Math.min(count, need);
            int left = count - take;
            ammoBox.setAmmoCount(stack, left);
            if (left <= 0) {
                ammoBox.setAmmoId(stack, DefaultAssets.EMPTY_AMMO_ID);
            }
            return take;
        }
        return 0;
    }

    @Inject(method = "canReload", at = @At("TAIL"), cancellable = true, remap = false)
    private void taczfixes$allowReloadWithAnyAmmo(LivingEntity shooter, ItemStack gunItem,
                                                  CallbackInfoReturnable<Boolean> cir) {
        try {
            if (cir.getReturnValue()) {
                return;
            }
            if (GunEnchantmentHelper.getLevel(gunItem, TaczFixesMod.STANDARD_AMMO_ENCHANTMENT.get()) <= 0) {
                return;
            }
            AbstractGunItem self = (AbstractGunItem) (Object) this;
            if (self.useInventoryAmmo(gunItem) || self.useDummyAmmo(gunItem)) {
                return;
            }
            ResourceLocation gunId = self.getGunId(gunItem);
            CommonGunIndex index = TimelessAPI.getCommonGunIndex(gunId).orElse(null);
            if (index == null) {
                return;
            }
            int maxAmmo = AttachmentDataUtils.getAmmoCountWithAttachment(gunItem, index.getGunData());
            int currentAmmo = self.getCurrentAmmoCount(gunItem);
            if (currentAmmo >= maxAmmo) {
                return;
            }
            if (taczfixes$hasAnyAmmo(shooter)) {
                cir.setReturnValue(Boolean.TRUE);
            }
        } catch (Exception e) {
            LOGGER.error("[标准弹药] canReload 判定异常", e);
        }
    }

    private boolean taczfixes$hasAnyAmmo(LivingEntity shooter) {
        return shooter.getCapability(ForgeCapabilities.ITEM_HANDLER, null)
                .map(handler -> {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack stack = handler.getStackInSlot(i);
                        if (stack.isEmpty()) {
                            continue;
                        }
                        if (stack.getItem() instanceof IAmmo) {
                            return true;
                        }
                        if (stack.getItem() instanceof IAmmoBox ammoBox && ammoBox.getAmmoCount(stack) > 0) {
                            return true;
                        }
                    }
                    return false;
                })
                .orElse(Boolean.FALSE);
    }
}