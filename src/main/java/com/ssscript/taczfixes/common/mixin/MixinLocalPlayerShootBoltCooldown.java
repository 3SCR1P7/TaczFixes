package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gameplay.LocalPlayerShoot;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 客户端侧: LocalPlayerShoot 用私有的 getCoolDown 计算射击间隔冷却,
 * 冷却 >= 50ms 时不允许开火尝试, 拉栓也因此被延迟。
 * 对 MANUAL_ACTION 枪械将该冷却按配件 manual_action_time 倍率同步缩减(与服务端 MixinLivingEntityShootBoltCooldown 一致),
 * 倍率 ≤ 0 时冷却直接归零。
 */
@Mixin(LocalPlayerShoot.class)
public class MixinLocalPlayerShootBoltCooldown {

    @Inject(method = "getCoolDown(Lcom/tacz/guns/api/item/IGun;Lnet/minecraft/world/item/ItemStack;Lcom/tacz/guns/resource/pojo/data/gun/GunData;)J",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void taczfixes$scaleBoltPreCooldown(IGun iGun, ItemStack mainHandItem, GunData gunData,
                                                CallbackInfoReturnable<Long> cir) {
        long coolDown = cir.getReturnValue();
        if (coolDown <= 0 || gunData == null) {
            return;
        }
        if (gunData.getBolt() != Bolt.MANUAL_ACTION) {
            return;
        }
        double factor = AttachmentTaczFixesManager.getManualActionTimeFactor(mainHandItem);
        if (factor <= 0.0d) {
            cir.setReturnValue(0L);
            return;
        }
        if (factor == 1.0d) {
            return;
        }
        cir.setReturnValue(Math.max(0L, (long) (coolDown / factor)));
    }
}
