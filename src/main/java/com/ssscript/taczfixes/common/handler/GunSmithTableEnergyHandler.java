package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.compat.GunSmithTableEnergyProvider;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class GunSmithTableEnergyHandler {

    @SubscribeEvent
    public void onAttachBlockEntityCapabilities(AttachCapabilitiesEvent<BlockEntity> event) {
        if (event.getObject() instanceof GunSmithTableBlockEntity table) {
            event.addCapability(new ResourceLocation(TaczFixesMod.MOD_ID, "gun_smith_table_energy"),
                    new GunSmithTableEnergyProvider(table));
        }
    }
}
