package com.ssscript.taczfixes.common.util;

import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/** 耐力消耗/判定工具(服务端状态)。 */
public final class StaminaHelper {

    private StaminaHelper() {
    }

    public static float max(Player player) {
        if (player == null) return 1f;
        return Math.max(1f, (float) player.getAttributeValue(
                com.ssscript.taczfixes.common.register.TaczFixesMod.STAMINA_ATTRIBUTE.get()));
    }

    public static float current(Player player) {
        return StaminaState.getStamina(player.getUUID(), max(player));
    }

    /** 消耗耐力(仅服务端), 并记录消耗时间用于恢复延迟。 */
    public static void consume(Player player, float amount) {
        if (player == null || player.level().isClientSide || amount <= 0f) return;
        UUID uuid = player.getUUID();
        float max = max(player);
        float value = Math.max(0f, StaminaState.getStamina(uuid, max) - amount);
        StaminaState.setStamina(uuid, value);
        StaminaState.setLastConsumeTick(uuid, player.level().getGameTime());
    }

    /** 按手持枪械/配件的消耗倍率消耗耐力(仅服务端)。 */
    public static void consumeWithGunMultiplier(Player player, float amount) {
        consume(player, amount * consumptionMultiplier(player));
    }

    /** 手持枪械/配件的耐力消耗倍率。 */
    public static float consumptionMultiplier(Player player) {
        if (player == null) return 1f;
        com.ssscript.taczfixes.common.data.GunTaczFixesData.StaminaConfig cfg =
                com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager.resolveStamina(player.getMainHandItem());
        return cfg == null || cfg.consumption_multiplier == null ? 1f : cfg.consumption_multiplier.floatValue();
    }

    /** 手持枪械/配件的耐力恢复倍率。 */
    public static float recoveryMultiplier(Player player) {
        if (player == null) return 1f;
        com.ssscript.taczfixes.common.data.GunTaczFixesData.StaminaConfig cfg =
                com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager.resolveStamina(player.getMainHandItem());
        return cfg == null || cfg.recovery_multiplier == null ? 1f : cfg.recovery_multiplier.floatValue();
    }

    public static boolean isInsufficient(Player player, float cost) {
        if (player == null || cost <= 0f) return false;
        if (!player.level().isClientSide) {
            return current(player) < cost;
        }
        return net.minecraftforge.fml.DistExecutor.unsafeRunForDist(
                () -> () -> com.ssscript.taczfixes.client.util.StaminaClientState.isInsufficient(cost),
                () -> () -> false);
    }

    /** 枪械重量(kg)。 */
    public static float gunWeight(Player player) {
        if (player == null) return 0f;
        net.minecraft.world.item.ItemStack stack = player.getMainHandItem();
        com.tacz.guns.api.item.IGun gun = com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
        if (gun == null) return 0f;
        net.minecraft.resources.ResourceLocation gunId = gun.getGunId(stack);
        if (gunId == null) return 0f;
        return com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> index.getGunData().getWeight()).orElse(0f);
    }
}
