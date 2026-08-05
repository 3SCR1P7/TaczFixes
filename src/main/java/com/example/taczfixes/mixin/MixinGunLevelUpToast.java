package com.example.taczfixes.mixin;

import com.tacz.guns.client.gui.toast.GunLevelUpToast;
import com.tacz.guns.network.message.ServerMessageLevelUp;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.tacz.guns.network.message.ServerMessageLevelUp", remap = false)
public class MixinGunLevelUpToast {
    @Inject(method = "onLevelUp", at = @At("TAIL"), remap = false)
    private static void taczfixes$showToast(ServerMessageLevelUp msg, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack gun = msg.getGun();
        int level = msg.getLevel();
        mc.getToasts().addToast(new GunLevelUpToast(gun,
            Component.translatable("toast.tacz.level_up"),
            Component.translatable("toast.tacz.sub.level_up")));
    }
}
