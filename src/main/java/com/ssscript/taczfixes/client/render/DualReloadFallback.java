package com.ssscript.taczfixes.client.render;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DualReloadFallback {

    private DualReloadFallback() {
    }

    public static boolean isReloadable(LocalPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof AbstractGunItem gun)) {
            return false;
        }
        if (gun.useInventoryAmmo(stack)) {
            return false;
        }
        ClientGunIndex index = TimelessAPI.getClientGunIndex(gun.getGunId(stack)).orElse(null);
        return index != null && index.getGunData() != null;
    }

    public static void reloadHand(LocalPlayer player, InteractionHand hand, boolean emptyOnly) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof AbstractGunItem gun)) {
            return;
        }
        if (gun.useInventoryAmmo(stack)) {
            return;
        }
        ClientGunIndex index = TimelessAPI.getClientGunIndex(gun.getGunId(stack)).orElse(null);
        GunData gunData = index == null ? null : index.getGunData();
        if (gunData == null) {
            return;
        }
        int magazineCapacity = Math.max(AttachmentDataUtils.getAmmoCountWithAttachment(stack, gunData), 0);
        if (magazineCapacity <= 0) {
            return;
        }
        int magazineAmmo = Math.max(gun.getCurrentAmmoCount(stack), 0);
        if (magazineAmmo >= magazineCapacity) {
            return;
        }
        if (emptyOnly) {
            boolean chamberSupported = gunData.getBolt() != Bolt.OPEN_BOLT;
            int chamberAmmo = chamberSupported && gun.hasBulletInBarrel(stack) ? 1 : 0;
            if (magazineAmmo + chamberAmmo > 0) {
                return;
            }
        }
        boolean hasReloadSupply = !IGunOperator.fromLivingEntity(player).needCheckAmmo() || gun.canReload(player, stack);
        if (!hasReloadSupply) {
            return;
        }
        if (hand == InteractionHand.OFF_HAND) {
            DualWieldClient.requestOffhandReload(player);
            return;
        }
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        LocalPlayerDataHolder data = operator.getDataHolder();
        if (isMainReloading(player) || data.clientStateLock || System.currentTimeMillis() - data.clientShootTimestamp < 100) {
            return;
        }
        operator.reload();
    }

    private static boolean isMainReloading(LocalPlayer player) {
        IGunOperator operator = IGunOperator.fromLivingEntity(player);
        if (operator.getSynReloadState().getStateType().isReloading()) {
            return true;
        }
        LocalPlayerDataHolder data = IClientPlayerGunOperator.fromLocalPlayer(player).getDataHolder();
        return data.clientStateLock && DualReloadAnimationManager.isHandActive(InteractionHand.MAIN_HAND);
    }
}
