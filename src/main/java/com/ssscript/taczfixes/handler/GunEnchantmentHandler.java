package com.ssscript.taczfixes.handler;

import com.ssscript.taczfixes.Config;
import com.ssscript.taczfixes.util.GunEnchantmentHelper;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 枪械附魔的效果处理器：
 * - 子弹伤害倍率：力量（所有目标）、亡灵杀手（亡灵）、节肢杀手（节肢）、穿刺（水生）
 * - 引雷：每次命中（含击杀）按概率召唤闪电，带冷却
 * - 忠诚：手持忠诚枪械时，按附魔等级增加 GunsmithLib 的 aim_lock 属性（经属性修饰符）
 */
public class GunEnchantmentHandler {
    private static final UUID LOYALTY_RANGE_UUID = UUID.fromString("4a6e9c9f-8b3e-4f5e-9f0a-1b2c3d4e5f01");
    private static final UUID LOYALTY_ANGLE_UUID = UUID.fromString("4a6e9c9f-8b3e-4f5e-9f0a-1b2c3d4e5f02");

    private static final Map<UUID, Long> LAST_LIGHTNING = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onGunHurt(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null) {
            return;
        }
        if (!(event.getHurtEntity() instanceof LivingEntity target)) {
            return;
        }
        int power = GunEnchantmentHelper.getLevelFromShooter(shooter, Enchantments.POWER_ARROWS);
        int smite = GunEnchantmentHelper.getLevelFromShooter(shooter, Enchantments.SMITE);
        int bane = GunEnchantmentHelper.getLevelFromShooter(shooter, Enchantments.BANE_OF_ARTHROPODS);
        int impaling = GunEnchantmentHelper.getLevelFromShooter(shooter, Enchantments.IMPALING);

        float mult = 1.0F;
        if (power > 0) {
            mult += Config.ENCH_POWER_BULLET_MULT.get().floatValue() * power;
        }
        if (smite > 0 && GunEnchantmentHelper.isUndead(target.getMobType())) {
            mult += Config.ENCH_SMITE_BULLET_MULT.get().floatValue() * smite;
        }
        if (bane > 0 && GunEnchantmentHelper.isArthropod(target.getMobType())) {
            mult += Config.ENCH_BANE_BULLET_MULT.get().floatValue() * bane;
        }
        if (impaling > 0 && GunEnchantmentHelper.isAquatic(target)) {
            mult += Config.ENCH_IMPALING_BULLET_MULT.get().floatValue() * impaling;
        }
        if (mult > 1.0F) {
            event.setBaseAmount(event.getBaseAmount() * mult);
        }
    }

    @SubscribeEvent
    public void onGunHurtPost(EntityHurtByGunEvent.Post event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null) {
            return;
        }
        if (!(event.getHurtEntity() instanceof LivingEntity target)) {
            return;
        }
        if (GunEnchantmentHelper.getLevelFromShooter(shooter, Enchantments.CHANNELING) <= 0) {
            return;
        }
        if (target.isAlive() && rollChanneling(target)) {
            strikeLightning(shooter, target);
        }
    }

    @SubscribeEvent
    public void onGunKill(EntityKillByGunEvent event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null || event.getKilledEntity() == null) {
            return;
        }
        ItemStack gun = GunEnchantmentHelper.getGunStack(shooter);
        int channeling = GunEnchantmentHelper.getLevel(gun, Enchantments.CHANNELING);
        int mending = GunEnchantmentHelper.getLevel(gun, Enchantments.MENDING);
        if (channeling <= 0 && mending <= 0) {
            return;
        }
        if (channeling > 0 && rollChanneling(event.getKilledEntity())) {
            strikeLightning(shooter, event.getKilledEntity());
        }
        if (mending > 0) {
            refillAmmo(shooter, event, gun);
        }
    }

    /**
     * 经验修补：将当前弹种（以造成击杀的子弹为准，兼容 GunsmithLib 弹种切换与 TaCZ: Arcana 弹药扩展）
     * 补充 1 颗到弹匣；若弹药直读（背包直读）则直接放入背包；弹匣已满时溢出部分放入背包。
     */
    private void refillAmmo(LivingEntity shooter, EntityKillByGunEvent event, ItemStack gun) {
        if (gun.isEmpty() || !(gun.getItem() instanceof AbstractGunItem gunItem)) {
            return;
        }
        if (!(event.getBullet() instanceof EntityKineticBullet kineticBullet)) {
            return;
        }
        ResourceLocation ammoId = kineticBullet.getAmmoId();
        if (ammoId == null || DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
            return;
        }
        int count = Config.ENCH_MENDING_AMMO_PER_KILL.get();
        if (count <= 0) {
            return;
        }
        // 手持枪械与开火枪械不一致时无法安全补入弹匣，改为放入背包
        boolean sameGun = Objects.equals(kineticBullet.getGunId(), gunItem.getGunId(gun));
        if (!sameGun || gunItem.useInventoryAmmo(gun)) {
            giveAmmoItem(shooter, ammoId, count);
            return;
        }
        TimelessAPI.getCommonGunIndex(gunItem.getGunId(gun)).ifPresent(index -> {
            int max = AttachmentDataUtils.getAmmoCountWithAttachment(gun, index.getGunData());
            int current = gunItem.getCurrentAmmoCount(gun);
            int toMag = Math.min(count, Math.max(max - current, 0));
            if (toMag > 0) {
                gunItem.setCurrentAmmoCount(gun, current + toMag);
            }
            int overflow = count - toMag;
            if (overflow > 0) {
                giveAmmoItem(shooter, ammoId, overflow);
            }
        });
    }

    private void giveAmmoItem(LivingEntity shooter, ResourceLocation ammoId, int count) {
        if (count <= 0) {
            return;
        }
        ItemStack ammoStack = AmmoItemBuilder.create().setId(ammoId).setCount(count).build();
        ItemStack remaining = shooter.getCapability(ForgeCapabilities.ITEM_HANDLER, null)
                .map(handler -> ItemHandlerHelper.insertItemStacked(handler, ammoStack, false))
                .orElse(ammoStack);
        if (!remaining.isEmpty() && !shooter.level().isClientSide) {
            shooter.spawnAtLocation(remaining);
        }
    }

    private boolean rollChanneling(LivingEntity context) {
        double chance = Config.ENCH_CHANNELING_TRIGGER_CHANCE.get();
        if (chance >= 1.0) {
            return true;
        }
        if (chance <= 0.0) {
            return false;
        }
        return context.getRandom().nextDouble() < chance;
    }

    private void strikeLightning(LivingEntity shooter, LivingEntity target) {
        if (Config.ENCH_CHANNELING_ONLY_THUNDER.get() && !shooter.level().isThundering()) {
            return;
        }
        long now = System.currentTimeMillis();
        UUID key = shooter.getUUID();
        Long last = LAST_LIGHTNING.get(key);
        int cooldown = Config.ENCH_STEAL_LIGHTNING_COOLDOWN_MS.get();
        if (last != null && now - last < cooldown) {
            return;
        }
        LAST_LIGHTNING.put(key, now);
        if (shooter.level() instanceof ServerLevel serverLevel) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
            if (bolt != null) {
                bolt.moveTo(target.getX(), target.getY(), target.getZ());
                if (shooter instanceof ServerPlayer player) {
                    bolt.setCause(player);
                }
                serverLevel.addFreshEntity(bolt);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        int loyalty = GunEnchantmentHelper.getLevelFromShooter(player, Enchantments.LOYALTY);
        Attribute range = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.fromNamespaceAndPath("gunsmithlib", "aim_lock_range"));
        Attribute angle = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.fromNamespaceAndPath("gunsmithlib", "aim_lock_angle"));
        if (range == null || angle == null) {
            return;
        }
        applyLoyaltyModifier(player, range, LOYALTY_RANGE_UUID, loyalty * Config.ENCH_LOYALTY_RANGE_PER_LEVEL.get());
        applyLoyaltyModifier(player, angle, LOYALTY_ANGLE_UUID, loyalty * Config.ENCH_LOYALTY_ANGLE_PER_LEVEL.get());
    }

    private void applyLoyaltyModifier(Player player, Attribute attribute, UUID uuid, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(uuid);
        if (amount > 0) {
            instance.addTransientModifier(new AttributeModifier(uuid, "taczfixes:loyalty", amount, AttributeModifier.Operation.ADDITION));
        }
    }
}