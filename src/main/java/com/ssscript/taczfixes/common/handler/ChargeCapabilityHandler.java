package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.compat.ChargeCapabilityProvider;
import com.ssscript.taczfixes.common.util.ChargeStorage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 给带 charge 配置的枪械物品挂载 FE 能量能力。 */
public class ChargeCapabilityHandler {

    @SubscribeEvent
    public void onAttachItemStackCapabilities(AttachCapabilitiesEvent<ItemStack> event) {
        ItemStack stack = event.getObject();
        if (stack == null || stack.isEmpty() || !ChargeStorage.isChargeGun(stack)) {
            return;
        }
        event.addCapability(new ResourceLocation(TaczFixesMod.MOD_ID, "charge"), new ChargeCapabilityProvider(stack));
    }
}
