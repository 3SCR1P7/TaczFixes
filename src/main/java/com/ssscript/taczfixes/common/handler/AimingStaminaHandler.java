package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.network.PacketDistributor;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.common.network.ServerMessageAimingStamina;
import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.util.AimingStaminaState;
import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** 服务端: 上肢耐力消耗/恢复、归零强制收镜、同步到客户端。 */
public class AimingStaminaHandler {

    private static final ResourceLocation MAX_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("taczfixes", "max_modifier_id");
    private static final ResourceLocation CONSUMPTION_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("taczfixes", "consumption_modifier_id");
    private static final ResourceLocation RECOVERY_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("taczfixes", "recovery_modifier_id");

    @SubscribeEvent
    public void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (false) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!Config.AIMING_STAMINA_ENABLED.get()) return;

        UUID uuid = player.getUUID();
        float max = (float) player.getAttributeValue(TaczFixesMod.AIMING_STAMINA_ATTRIBUTE);
        if (max <= 0) max = 1;
        float consumption = Config.AIMING_STAMINA_CONSUMPTION.get().floatValue();
        float consumptionMultiplier = (float) player.getAttributeValue(TaczFixesMod.AIMING_STAMINA_CONSUMPTION_ATTRIBUTE);
        float recovery = (float) player.getAttributeValue(TaczFixesMod.AIMING_STAMINA_RECOVERY_ATTRIBUTE);

        IGunOperator operator = IGunOperator.fromLivingEntity(player);
        boolean aiming = operator != null && operator.getSynIsAiming();
        long gameTime = player.level().getGameTime();
        float stamina = AimingStaminaState.getStamina(uuid, max);
        if (stamina > max) stamina = max;

        com.ssscript.taczfixes.common.data.GunTaczFixesData.AimingStaminaConfig aimCfg =
                com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager.resolveAimingStamina(player.getMainHandItem());

        if (aiming) {
            float consumptionMul = aimCfg.consumption_multiplier.floatValue();
            float weightPerKg = aimCfg.weight_consumption.floatValue();
            float weightFactor = 1f + gunWeight(player) * weightPerKg;
            float holdMultiplier = AimingStaminaState.isHoldingBreath(uuid)
                    ? aimCfg.hold_breath_consumption_multiplier.floatValue() : 1f;
            stamina -= consumption * consumptionMultiplier * consumptionMul * weightFactor * holdMultiplier / 20f;
            AimingStaminaState.setLastAimTick(uuid, gameTime);
            if (stamina <= 0f) {
                stamina = 0f;
                if (operator != null) {
                    operator.aim(false);
                }
            }
        } else {
            long lastAim = AimingStaminaState.getLastAimTick(uuid);
            long delayTicks = Math.max(0, aimCfg.recovery_delay) / 50L;
            float recoveryMul = aimCfg.recovery_multiplier.floatValue();
            if (lastAim < 0 || gameTime - lastAim >= delayTicks) {
                stamina = Math.min(max, stamina + recovery * recoveryMul / 20f);
            }
        }

        AimingStaminaState.setStamina(uuid, stamina);

        float lastSynced = AimingStaminaState.getLastSynced(uuid);
        if (Float.isNaN(lastSynced) || Math.abs(lastSynced - stamina) > 0.005f) {
            AimingStaminaState.setLastSynced(uuid, stamina);
            sendToPlayer(player, stamina, max);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        applyConfigModifiers(player);
        float max = (float) player.getAttributeValue(TaczFixesMod.AIMING_STAMINA_ATTRIBUTE);
        AimingStaminaState.setStamina(player.getUUID(), max);
        sendToPlayer(player, max, max);
    }

    private static void sendToPlayer(ServerPlayer player, float stamina, float max) {
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ServerMessageAimingStamina(stamina, max));
    }

    /** 消耗上肢耐力(服务端)并立即同步。 */
    public static void consume(ServerPlayer player, float amount) {
        if (player == null || amount <= 0f) return;
        float max = (float) player.getAttributeValue(TaczFixesMod.AIMING_STAMINA_ATTRIBUTE);
        if (max <= 0f) max = 1f;
        float value = Math.max(0f, AimingStaminaState.getStamina(player.getUUID(), max) - amount);
        AimingStaminaState.setStamina(player.getUUID(), value);
        // 近战/开火等消耗后同样需要等待恢复延迟
        AimingStaminaState.setLastAimTick(player.getUUID(), player.level().getGameTime());
        sendToPlayer(player, value, max);
    }

    /** 上肢耐力是否不足以支付该消耗。 */
    public static boolean isInsufficient(ServerPlayer player, float cost) {
        if (player == null || cost <= 0f) return false;
        float max = (float) player.getAttributeValue(TaczFixesMod.AIMING_STAMINA_ATTRIBUTE);
        if (max <= 0f) max = 1f;
        return AimingStaminaState.getStamina(player.getUUID(), max) < cost;
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        applyConfigModifiers(player);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AimingStaminaState.remove(player.getUUID());
        }
    }

    /** 用 config 中的默认值修正 attribute 基础值(config 在注册表事件之后才加载, 无法直接作为 attribute 默认值)。 */
    private static void applyConfigModifiers(ServerPlayer player) {
        applyModifier(player, TaczFixesMod.AIMING_STAMINA_ATTRIBUTE, MAX_MODIFIER_ID,
                Config.AIMING_STAMINA_MAX.get());
        applyModifier(player, TaczFixesMod.AIMING_STAMINA_CONSUMPTION_ATTRIBUTE, CONSUMPTION_MODIFIER_ID,
                Config.AIMING_STAMINA_CONSUMPTION_MULTIPLIER.get());
        applyModifier(player, TaczFixesMod.AIMING_STAMINA_RECOVERY_ATTRIBUTE, RECOVERY_MODIFIER_ID,
                Config.AIMING_STAMINA_RECOVERY.get());
    }

    private static void applyModifier(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id, double configured) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        double delta = configured - instance.getBaseValue();
        AttributeModifier existing = instance.getModifier(id);
        if (existing != null) {
            if (Math.abs(existing.amount() - delta) < 1.0E-6) return;
            instance.removeModifier(existing);
        }
        if (Math.abs(delta) > 1.0E-6) {
            instance.addTransientModifier(new AttributeModifier(id, delta,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static float gunWeight(ServerPlayer player) {
        return com.ssscript.taczfixes.common.util.StaminaHelper.gunWeight(player);
    }
}
