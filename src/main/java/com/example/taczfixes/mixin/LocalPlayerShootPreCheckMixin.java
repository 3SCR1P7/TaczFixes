package com.example.taczfixes.mixin;

import com.example.taczfixes.util.BurstBlockHelper;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.gameplay.LocalPlayerShoot;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayerShoot.class)
public class LocalPlayerShootPreCheckMixin {
    @Shadow(remap = false)
    private LocalPlayer player;

    @Inject(method = "preCheck", at = @At("TAIL"), cancellable = true, remap = false, require = 0)
    private void onPreCheckTail(IGun iGun, IGunOperator gunOperator, ClientGunIndex gunIndex,
                                ItemStack mainHandItem, GunDisplayInstance display, GunData gunData,
                                boolean playDrySound, CallbackInfoReturnable<ShootResult> cir) {
        if (cir.getReturnValue() != null) return;
        if (iGun.getFireMode(mainHandItem) != FireMode.BURST) return;
        if (!BurstBlockHelper.hasRestrictedAttachment(iGun, mainHandItem)) return;
        if (playDrySound) {
            SoundPlayManager.playDryFireSound(player, display);
        }
        cir.setReturnValue(ShootResult.NO_AMMO);
    }
}
