package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.network.PacketDistributor;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.common.network.ServerMessageStamina;
import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.util.ParCoolStaminaHelper;
import com.ssscript.taczfixes.common.util.StaminaHelper;
import com.ssscript.taczfixes.common.util.StaminaState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** 服务端: 耐力消耗(原版疾跑/游泳/跳跃/ParCool)、恢复、同步。 */
public class StaminaHandler {

    private static final ResourceLocation MAX_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("taczfixes", "max_modifier_id");
    private static final ResourceLocation RECOVERY_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("taczfixes", "recovery_modifier_id");
    private static final ResourceLocation CONSUMPTION_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("taczfixes", "consumption_modifier_id");

    @SubscribeEvent
    public void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (false) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!Config.STAMINA_ENABLED.get()) return;

        UUID uuid = player.getUUID();
        float max = (float) player.getAttributeValue(TaczFixesMod.STAMINA_ATTRIBUTE);
        if (max <= 0f) max = 1f;
        float recovery = (float) player.getAttributeValue(TaczFixesMod.STAMINA_RECOVERY_ATTRIBUTE);
        long gameTime = player.level().getGameTime();
        float stamina = StaminaState.getStamina(uuid, max);
        if (stamina > max) stamina = max;
        boolean consumed = false;

        // ParCool 消耗通过 ClientMessageStaminaConsume 上报后直接扣除, 此处仅做镜像

        // 原版疾跑/游泳(服务端标记可能不同步, 以客户端上报为准); 潜行(按住shift)仅抵消疾跑, 游泳/下潜仍消耗
        boolean sprinting = (player.isSprinting() || StaminaState.isClientSprinting(uuid)) && !player.isShiftKeyDown();
        if (sprinting || player.isSwimming()) {
            float rate = Config.STAMINA_SPRINT_CONSUMPTION_PER_SECOND.get().floatValue();
            float weightFactor = 1f + gunWeight(player) * Config.STAMINA_WEIGHT_CONSUMPTION_PER_KG.get().floatValue();
            stamina -= rate * weightFactor * StaminaHelper.consumptionMultiplier(player) / 20f;
            consumed = true;
        }

        if (stamina < 0f) stamina = 0f;

        // 服务端玩家的 deltaMovement 不可靠, 用位置变化判断是否在行走
        double movedSqr = StaminaState.movedDistanceSqr(uuid, player.getX(), player.getZ());

        if (consumed) {
            StaminaState.setLastConsumeTick(uuid, gameTime);
        } else {
            long lastConsume = StaminaState.getLastConsumeTick(uuid);
            long delayTicks = Math.max(0, Config.STAMINA_RECOVERY_DELAY_MS.get()) / 50L;
            if (lastConsume < 0 || gameTime - lastConsume >= delayTicks) {
                boolean walking = movedSqr > 1.0E-6;
                float multiplier = walking ? Config.STAMINA_WALK_RECOVERY_MULTIPLIER.get().floatValue() : 1f;
                if (StaminaState.isExhausted(uuid)) {
                    multiplier *= Config.STAMINA_EXHAUSTION_RECOVERY_MULTIPLIER.get().floatValue();
                }
                stamina = Math.min(max,
                        stamina + recovery * StaminaHelper.recoveryMultiplier(player) * multiplier / 20f);
            }
        }

        // 力竭状态: 归零进入, 恢复到阈值后结束
        if (stamina <= 0f) {
            StaminaState.setExhausted(uuid, true);
        } else if (stamina >= Config.STAMINA_EXHAUSTION_END.get().floatValue()) {
            StaminaState.setExhausted(uuid, false);
        }

        StaminaState.setStamina(uuid, stamina);
        if (ParCoolStaminaHelper.isLoaded()) {
            ParCoolStaminaHelper.mirror(player, stamina, max, StaminaState.isExhausted(uuid));
        }

        float lastSynced = StaminaState.getLastSynced(uuid);
        if (Float.isNaN(lastSynced) || Math.abs(lastSynced - stamina) > 0.005f) {
            StaminaState.setLastSynced(uuid, stamina);
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new ServerMessageStamina(stamina, max, StaminaState.isExhausted(uuid)));
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        applyConfigModifiers(player);
        float max = (float) player.getAttributeValue(TaczFixesMod.STAMINA_ATTRIBUTE);
        StaminaState.setStamina(player.getUUID(), max);
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ServerMessageStamina(max, max, false));
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        applyConfigModifiers(player);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StaminaState.remove(player.getUUID());
        }
    }

    private static void applyConfigModifiers(ServerPlayer player) {
        applyModifier(player, TaczFixesMod.STAMINA_ATTRIBUTE, MAX_MODIFIER_ID, Config.STAMINA_MAX.get());
        applyModifier(player, TaczFixesMod.STAMINA_RECOVERY_ATTRIBUTE, RECOVERY_MODIFIER_ID,
                Config.STAMINA_RECOVERY.get());
        applyModifier(player, TaczFixesMod.STAMINA_CONSUMPTION_ATTRIBUTE, CONSUMPTION_MODIFIER_ID,
                Config.STAMINA_CONSUMPTION_MULTIPLIER.get());
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
        return StaminaHelper.gunWeight(player);
    }
}
