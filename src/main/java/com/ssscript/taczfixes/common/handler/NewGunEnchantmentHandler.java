package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import com.ssscript.taczfixes.common.util.GunEnchantmentUtil;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 新增五个枪械附魔的实战效果:
 * <ul>
 *     <li>scarlet_death(猩红终焉): 命中标记目标, 持续冒出红色药水粒子; 射手死亡时被标记目标立刻死亡;</li>
 *     <li>tokonagi(常凪): 命中后目标被冻结 floor(伤害)×等级 tick, 期间无法移动/攻击;</li>
 *     <li>ember(余烬): 击杀时在原位置引发带火焰、破坏方块的爆炸, 威力 3+等级;</li>
 *     <li>poised(蓄势待发): 持枪蓄力时受到的伤害减少 等级×15%;</li>
 *     <li>seal_flare(封焰时刻): 伤害随过热进度提升, 过热>70% 时射速下降并点燃目标。</li>
 * </ul>
 */
public class NewGunEnchantmentHandler {
    private static final ResourceLocation CARDIAC_DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath(TaczFixesMod.MOD_ID, "cardiac_arrest");
    /** 凝渊者秒杀使用的伤害类型(死亡提示: 正在凝视深渊)。 */
    public static final ResourceLocation ABYSS_DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath(TaczFixesMod.MOD_ID, "abyss_gaze");

    /** 被猩红终焉标记: 目标 UUID -> 射手 UUID。 */
    private static final Map<UUID, UUID> CARDIAC_TARGETS = new ConcurrentHashMap<>();

    /** 被常凪冻结: 目标 UUID -> 冻结状态。 */
    private static final Map<UUID, Freeze> FROZEN = new ConcurrentHashMap<>();

    /** 封焰时刻: 过热超过该比例后开始降射速、点燃目标。 */
    private static final float SEAL_FLARE_HEAT_THRESHOLD = 0.70F;
    /** 封焰时刻: 过热 100% 时的射速倍率。 */
    private static final float SEAL_FLARE_MIN_FIRE_RATE = 0.775F;
    /** 封焰时刻: 点燃时长(tick)。 */
    private static final int SEAL_FLARE_IGNITE_TICKS = 80;

    private record Freeze(ResourceKey<Level> dimension, UUID target, double x, double y, double z,
                          long until, boolean hadNoAi, boolean wasNoAi) {
    }

    // ------------------------------------------------------------------ 命中/击杀事件

    @SubscribeEvent
    public void onGunHurtPost(EntityHurtByGunEvent.Post event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        if (!GunEnchantmentHelper.isEnabled()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null || !(event.getHurtEntity() instanceof LivingEntity target) || !target.isAlive()) {
            return;
        }
        ItemStack gun = GunEnchantmentHelper.getGunStack(event.getGunId(), shooter);
        if (gun.isEmpty()) {
            return;
        }
        if (GunEnchantmentUtil.level(gun, TaczFixesMod.SCARLET_DEATH_ENCHANTMENT) > 0) {
            CARDIAC_TARGETS.put(target.getUUID(), shooter.getUUID());
        }
        int tokonagi = GunEnchantmentUtil.level(gun, TaczFixesMod.TOKONAGI_ENCHANTMENT);
        if (tokonagi > 0) {
            int ticks = (int) (Math.floor(event.getAmount()) * tokonagi
                    * Config.ENCH_TOKONAGI_TICKS_PER_DAMAGE.get());
            applyFreeze(target, ticks);
        }
    }

    @SubscribeEvent
    public void onGunKill(EntityKillByGunEvent event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        if (!GunEnchantmentHelper.isEnabled()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        LivingEntity killed = event.getKilledEntity();
        if (shooter == null || killed == null) {
            return;
        }
        int level = GunEnchantmentUtil.level(GunEnchantmentHelper.getGunStack(event.getGunId(), shooter),
                TaczFixesMod.EMBER_ENCHANTMENT);
        if (level <= 0) {
            return;
        }
        Level levelObj = killed.level();
        if (levelObj.isClientSide) {
            return;
        }
        float power = (float) (Config.ENCH_EMBER_EXPLOSION_POWER_BASE.get()
                + level * Config.ENCH_EMBER_EXPLOSION_POWER_PER_LEVEL.get());
        levelObj.explode(null, killed.getX(), killed.getY(), killed.getZ(),
                power, true, Level.ExplosionInteraction.BLOCK);
    }

    @SubscribeEvent
    public void onGunHurtPre(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        if (!GunEnchantmentHelper.isEnabled()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null) {
            return;
        }
        ItemStack gun = GunEnchantmentHelper.getGunStack(event.getGunId(), shooter);
        int level = GunEnchantmentUtil.level(gun, TaczFixesMod.SEAL_FLARE_ENCHANTMENT);
        if (level <= 0) {
            return;
        }
        float heat = GunEnchantmentUtil.heatProgress(gun);
        if (heat <= 0.0F) {
            return;
        }
        float multiplier = 1.0F + heat * level
                * (float) (Config.ENCH_SEAL_FLARE_DAMAGE_PERCENT_PER_HEAT_PER_LEVEL.get() / 100.0D);
        event.setBaseAmount(event.getBaseAmount() * multiplier);
    }

    /** 蓄势待发: 持枪蓄力时受到的伤害减少 等级×15%。 */
    @SubscribeEvent
    public void onLivingHurtPoised(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!GunEnchantmentHelper.isEnabled()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        ItemStack gun = GunEnchantmentHelper.getGunStack(entity);
        if (gun.isEmpty()) {
            return;
        }
        int level = GunEnchantmentUtil.level(gun, TaczFixesMod.POISED_ENCHANTMENT);
        if (level <= 0 || !GunEnchantmentUtil.isCharging(entity)) {
            return;
        }
        float reduction = Math.min(1.0F, level
                * (float) (Config.ENCH_POISED_REDUCTION_PERCENT_PER_LEVEL.get() / 100.0D));
        event.setAmount(event.getAmount() * (1.0F - reduction));
    }

    // ------------------------------------------------------------------ 猩红终焉: 死亡触发

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        UUID dead = event.getEntity().getUUID();
        CARDIAC_TARGETS.remove(dead);
        List<UUID> owned = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : CARDIAC_TARGETS.entrySet()) {
            if (entry.getValue().equals(dead)) {
                owned.add(entry.getKey());
            }
        }
        if (owned.isEmpty() || event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity owner = event.getEntity();
        for (UUID targetId : owned) {
            CARDIAC_TARGETS.remove(targetId);
            Entity entity = findEntity(owner, targetId);
            if (entity instanceof LivingEntity target && target.isAlive()) {
                killByCardiacArrest(target, owner);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        CARDIAC_TARGETS.entrySet().removeIf(entry -> entry.getValue().equals(playerId));
        FROZEN.remove(playerId);
    }

    private static void killByCardiacArrest(LivingEntity target, LivingEntity owner) {
        instantKill(target, owner, CARDIAC_DAMAGE_ID);
    }

    /**
     * 瞬间击杀: 直接把血量置 0 再走正常死亡流程(绕过伤害减免/图腾), 保留击杀者与自定义死亡提示。
     * 猩红终焉与凝渊者共用。
     */
    public static void instantKill(LivingEntity target, LivingEntity attacker, ResourceLocation damageTypeId) {
        if (target == null || !target.isAlive()) {
            return;
        }
        DamageSource source;
        try {
            Holder<DamageType> holder = target.level().registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE, damageTypeId));
            source = new DamageSource(holder, attacker, attacker);
        } catch (Throwable throwable) {
            source = target.damageSources().magic();
        }
        target.setHealth(0.0F);
        if (!target.isRemoved()) {
            ((com.ssscript.taczfixes.common.mixin.LivingEntityDieInvoker) target).taczfixes$die(source);
        }
    }

    // ------------------------------------------------------------------ 常凪: 冻结

    private static void applyFreeze(LivingEntity target, int ticks) {
        if (ticks <= 0 || !(target.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        long now = serverLevel.getGameTime();
        long until = now + ticks;
        Freeze existing = FROZEN.get(target.getUUID());
        if (existing != null) {
            until = Math.max(existing.until(), until);
        }
        boolean mob = target instanceof Mob;
        boolean wasNoAi = mob && ((Mob) target).isNoAi();
        if (mob) {
            ((Mob) target).setNoAi(true);
        }
        FROZEN.put(target.getUUID(), new Freeze(serverLevel.dimension(), target.getUUID(),
                target.getX(), target.getY(), target.getZ(), until, true, wasNoAi));
    }

    public static boolean isFrozen(Entity entity) {
        return entity != null && FROZEN.containsKey(entity.getUUID());
    }

    // ------------------------------------------------------------------ 服务端 tick

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (!CARDIAC_TARGETS.isEmpty()) {
            tickCardiac(server);
        }
        if (!FROZEN.isEmpty()) {
            tickFreeze(server);
        }
    }

    private static void tickCardiac(MinecraftServer server) {
        long time = server.overworld() == null ? 0L : server.overworld().getGameTime();
        for (Map.Entry<UUID, UUID> entry : CARDIAC_TARGETS.entrySet()) {
            Entity target = findEntity(server, entry.getKey());
            if (!(target instanceof LivingEntity living) || !living.isAlive()) {
                CARDIAC_TARGETS.remove(entry.getKey());
                continue;
            }
            Entity owner = findEntity(server, entry.getValue());
            if (!(owner instanceof LivingEntity)) {
                CARDIAC_TARGETS.remove(entry.getKey());
                continue;
            }
            if (time % 5L != 0L || !(living.level() instanceof ServerLevel serverLevel)) {
                continue;
            }
            double x = living.getX() + (living.getRandom().nextDouble() - 0.5D) * living.getBbWidth();
            double y = living.getY() + living.getBbHeight() * (0.35D + living.getRandom().nextDouble() * 0.5D);
            double z = living.getZ() + (living.getRandom().nextDouble() - 0.5D) * living.getBbWidth();
            // ENTITY_EFFECT = 药水粒子; 颜色通过 xDist/yDist/zDist 传入(红色)。
            serverLevel.sendParticles(ParticleTypes.ENTITY_EFFECT, x, y, z, 0,
                    0.95D, 0.05D, 0.05D, 1.0D);
        }
    }

    private static void tickFreeze(MinecraftServer server) {
        for (Map.Entry<UUID, Freeze> entry : FROZEN.entrySet()) {
            Freeze freeze = entry.getValue();
            ServerLevel level = server.getLevel(freeze.dimension());
            if (level == null) {
                FROZEN.remove(entry.getKey());
                continue;
            }
            Entity entity = level.getEntity(freeze.target());
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                FROZEN.remove(entry.getKey());
                continue;
            }
            if (level.getGameTime() >= freeze.until()) {
                FROZEN.remove(entry.getKey());
                if (freeze.hadNoAi() && living instanceof Mob mob && !freeze.wasNoAi()) {
                    mob.setNoAi(false);
                }
                continue;
            }
            living.setDeltaMovement(0.0D, 0.0D, 0.0D);
            living.fallDistance = 0.0F;
            living.setPos(freeze.x(), freeze.y(), freeze.z());
            if (living instanceof Mob mob) {
                mob.getNavigation().stop();
                mob.setNoAi(true);
            }
            if (living instanceof ServerPlayer player) {
                player.connection.teleport(freeze.x(), freeze.y(), freeze.z(), player.getYRot(), player.getXRot());
            }
        }
    }

    private static Entity findEntity(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static Entity findEntity(LivingEntity context, UUID id) {
        if (context != null && context.level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(id);
            if (entity != null) {
                return entity;
            }
        }
        if (context != null && context.getServer() != null) {
            return findEntity(context.getServer(), id);
        }
        return null;
    }
}
