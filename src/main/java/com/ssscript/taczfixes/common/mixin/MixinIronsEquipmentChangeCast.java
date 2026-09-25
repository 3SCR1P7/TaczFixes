package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.util.ContinuousCastGuard;
import com.tacz.guns.api.item.IGun;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 开火会使枪械 NBT(弹药等)变化, Iron's 会将其视为装备变化并取消持续施法;
 * 仅当变化发生在施法手、仍是同一把枪、且未切换快捷栏时, 忽略这次装备变化;
 * 切枪/换手/切换物品栏照常取消。
 */
@Mixin(targets = "io.redspace.ironsspellbooks.player.ServerPlayerEvents", remap = false)
public class MixinIronsEquipmentChangeCast {

    @Inject(method = "onLivingEquipmentChangeEvent", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$keepGunCast(LivingEquipmentChangeEvent event, CallbackInfo ci) {
        if (!Config.GUN_SPELL_ENABLED.get()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        EquipmentSlot slot = event.getSlot();
        boolean offhand = slot == EquipmentSlot.OFFHAND;
        if (!offhand && slot != EquipmentSlot.MAINHAND) {
            return;
        }
        ContinuousCastGuard.Guard guard = ContinuousCastGuard.get(player);
        if (guard == null || guard.offhand() != offhand) {
            return;
        }
        ItemStack from = event.getFrom();
        ItemStack to = event.getTo();
        if (from.getItem() != to.getItem()) {
            return;
        }
        IGun gun = IGun.getIGunOrNull(to);
        if (gun == null || !guard.gunId().equals(gun.getGunId(to))) {
            return;
        }
        ci.cancel();
    }
}
