package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.util.ContinuousCastGuard;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import io.redspace.ironsspellbooks.api.events.SpellCooldownAddedEvent;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import io.redspace.ironsspellbooks.network.casting.CancelCastPacket;
import io.redspace.ironsspellbooks.network.casting.UpdateCastingStatePacket;
import io.redspace.ironsspellbooks.setup.PacketDistributor;
import io.redspace.ironsspellbooks.spells.blood.SacrificeSpell;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 枪械被注入 Iron's Spellbooks 法术后, 开火时随机发动一个满足条件的法术; 需要锁定目标的法术在子弹命中实体时发动。 */
public class GunSpellHandler {

    private static final long PENDING_TIMEOUT_MS = 5000L;
    private static final String MAIN_HAND = "mainhand";
    private static final String[] TARGET_MARKERS = {
            "preCastTargetHelper", "TargetEntityCastData", "SyncTargetingDataPacket"};

    private static final Map<UUID, PendingCast> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<ResourceLocation, Long>> LAST_TRIGGER = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> TARGETED_CACHE = new ConcurrentHashMap<>();

    private record PendingCast(ItemStack gun, ResourceLocation gunId, SpellData data, long expireAt) {
    }

    @SubscribeEvent
    public void onGunFire(GunFireEvent event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        if (!(event.getShooter() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack gun = event.getGunItemStack();
        if (gun == null || gun.isEmpty() || IGun.getIGunOrNull(gun) == null) {
            return;
        }
        GunTaczFixesData.ImbuementConfig settings = TaczFixesDataManager.resolveImbuement(gun);
        if (!Boolean.TRUE.equals(settings.enable)) {
            return;
        }
        ResourceLocation gunId = gunId(gun);
        if (onTriggerCooldown(player, gunId, settings.cooldown)) {
            return;
        }
        SpellData data = pickCastableSpell(player, gun);
        if (data == null) {
            return;
        }
        if (needsTarget(data.getSpell())) {
            PENDING.put(player.getUUID(), new PendingCast(gun.copy(), gunId, data,
                    System.currentTimeMillis() + PENDING_TIMEOUT_MS));
            return;
        }
        if (cast(player, gun, data, null)) {
            markTriggered(player, gunId);
        }
    }

    @SubscribeEvent
    public void onGunHurt(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        if (!(event.getAttacker() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getHurtEntity() instanceof LivingEntity target)) {
            return;
        }
        PendingCast pending = PENDING.get(player.getUUID());
        if (pending == null) {
            return;
        }
        if (System.currentTimeMillis() > pending.expireAt()) {
            PENDING.remove(player.getUUID());
            return;
        }
        ResourceLocation firedGunId = event.getGunId();
        if (firedGunId != null && pending.gunId() != null && !pending.gunId().equals(firedGunId)) {
            return;
        }
        PENDING.remove(player.getUUID());
        GunTaczFixesData.ImbuementConfig settings = TaczFixesDataManager.resolveImbuement(pending.gun());
        if (!Boolean.TRUE.equals(settings.enable) || onTriggerCooldown(player, pending.gunId(), settings.cooldown)) {
            return;
        }
        if (cast(player, pending.gun(), pending.data(), target)) {
            markTriggered(player, pending.gunId());
        }
    }

    /** 按倍率调整枪械法术的法力消耗(含持续施法每 10 tick 的结算)。 */
    @SubscribeEvent
    public void onSpellOnCast(SpellOnCastEvent event) {
        if (!Config.GUN_SPELL_ENABLED.get() || event.getCastSource() != CastSource.SWORD) {
            return;
        }
        Player player = event.getEntity();
        if (player == null) {
            return;
        }
        ItemStack gun = findImbuedGun(player);
        if (gun == null) {
            return;
        }
        Double multiplier = TaczFixesDataManager.resolveImbuement(gun).mana_consume_multiplier;
        if (multiplier == null || Math.abs(multiplier - 1.0) < 0.0001) {
            return;
        }
        event.setManaCost((int) Math.max(0L, Math.round(event.getManaCost() * multiplier)));
    }

    /** 按倍率调整枪械法术自身的冷却时间(不影响最小触发冷却)。 */
    @SubscribeEvent
    public void onSpellCooldownAdded(SpellCooldownAddedEvent.Pre event) {
        if (!Config.GUN_SPELL_ENABLED.get() || event.getCastSource() != CastSource.SWORD) {
            return;
        }
        Player player = event.getEntity();
        if (player == null) {
            return;
        }
        ItemStack gun = findImbuedGun(player);
        if (gun == null) {
            return;
        }
        Double multiplier = TaczFixesDataManager.resolveImbuement(gun).cooldown_multiplier;
        if (multiplier == null || Math.abs(multiplier - 1.0) < 0.0001) {
            return;
        }
        event.setEffectiveCooldown((int) Math.max(0L, Math.round(event.getEffectiveCooldown() * multiplier)));
    }

    private static ItemStack findImbuedGun(Player player) {
        ItemStack main = player.getMainHandItem();
        if (isImbuedGun(main)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        return isImbuedGun(off) ? off : null;
    }

    private static boolean isImbuedGun(ItemStack stack) {
        return stack != null && !stack.isEmpty() && IGun.getIGunOrNull(stack) != null
                && ISpellContainer.isSpellContainer(stack);
    }

    /** 持续施法期间切枪/换手/切换物品栏时终止施法(开火造成的自身变化不受影响)。 */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        ContinuousCastGuard.Guard guard = ContinuousCastGuard.get(player);
        if (guard == null) {
            return;
        }
        MagicData magicData = MagicData.getPlayerMagicData(player);
        if (!magicData.isCasting()) {
            ContinuousCastGuard.clear(player);
            return;
        }
        if (ContinuousCastGuard.matchesCurrent(player, guard)) {
            return;
        }
        ContinuousCastGuard.clear(player);
        CancelCastPacket.cancelCast(player, true);
    }

    private static boolean matchesGunId(ItemStack stack, ResourceLocation gunId) {
        IGun gun = stack == null || stack.isEmpty() ? null : IGun.getIGunOrNull(stack);
        return gun != null && gunId != null && gunId.equals(gun.getGunId(stack));
    }

    /** 该枪械是否仍在最小触发冷却内。 */
    private static boolean onTriggerCooldown(ServerPlayer player, ResourceLocation gunId, Double cooldownSeconds) {
        if (cooldownSeconds == null || cooldownSeconds <= 0.0 || gunId == null) {
            return false;
        }
        Map<ResourceLocation, Long> casts = LAST_TRIGGER.get(player.getUUID());
        Long last = casts == null ? null : casts.get(gunId);
        return last != null && System.currentTimeMillis() - last < (long) (cooldownSeconds * 1000.0);
    }

    private static void markTriggered(ServerPlayer player, ResourceLocation gunId) {
        if (gunId == null) {
            return;
        }
        LAST_TRIGGER.computeIfAbsent(player.getUUID(), key -> new ConcurrentHashMap<>()).put(gunId,
                System.currentTimeMillis());
    }

    /** 从枪械已注入的法术中随机选择一个满足触发条件的。 */
    private static SpellData pickCastableSpell(ServerPlayer player, ItemStack gun) {
        ISpellContainer container = ISpellContainer.get(gun);
        if (container == null || container.isEmpty()) {
            return null;
        }
        List<SpellSlot> slots = container.getActiveSpells();
        if (slots == null || slots.isEmpty()) {
            return null;
        }
        GunTaczFixesData.ImbuementConfig settings = TaczFixesDataManager.resolveImbuement(gun);
        double manaMultiplier = settings.mana_consume_multiplier == null ? 1.0 : settings.mana_consume_multiplier;
        List<SpellData> castable = new ArrayList<>();
        for (SpellSlot slot : slots) {
            if (slot == null) {
                continue;
            }
            SpellData data = slot.spellData();
            if (data != null && data.getSpell() != null && canCast(player, data, manaMultiplier)) {
                castable.add(data);
            }
        }
        if (castable.isEmpty()) {
            return null;
        }
        return castable.get(player.getRandom().nextInt(castable.size()));
    }

    /** 冷却完毕且有足够法力值(按倍率计算); 创造模式无需考虑; 施法期间不可再次触发。 */
    private static boolean canCast(ServerPlayer player, SpellData data, double manaMultiplier) {
        if (data == null || data.getSpell() == null) {
            return false;
        }
        MagicData magicData = MagicData.getPlayerMagicData(player);
        if (magicData.isCasting()) {
            return false;
        }
        if (player.isCreative()) {
            return true;
        }
        AbstractSpell spell = data.getSpell();
        if (magicData.getPlayerCooldowns().isOnCooldown(spell)) {
            return false;
        }
        int cost = (int) Math.max(0L, Math.round(spell.getManaCost(data.getLevel()) * manaMultiplier));
        return magicData.getMana() >= cost;
    }

    /**
     * 吟唱类法术直接施放(不进入吟唱状态, 无施法动画);
     * 持续施放类法术进入持续施法状态但不发送施法状态包(无施法动画), 由 Iron's 自行每 10 tick 触发一次效果并在结束时收尾,
     * 正在持续施法时不会再次触发以避免打断。
     */
    private static boolean cast(ServerPlayer player, ItemStack gun, SpellData data, LivingEntity target) {
        AbstractSpell spell = data.getSpell();
        if (target != null && !canCastOnTarget(player, spell, target)) {
            return false;
        }
        MagicData magicData = MagicData.getPlayerMagicData(player);
        if (spell.getCastType() == CastType.CONTINUOUS) {
            if (magicData.isCasting()) {
                return false;
            }
            if (target != null) {
                magicData.setAdditionalCastData(new TargetEntityCastData(target));
            }
            int duration = spell.getEffectiveCastTime(data.getLevel(), player);
            IGun castingGun = IGun.getIGunOrNull(gun);
            ResourceLocation castingGunId = castingGun == null ? null : castingGun.getGunId(gun);
            boolean offhand = !matchesGunId(player.getMainHandItem(), castingGunId);
            ItemStack held = offhand ? player.getOffhandItem() : player.getMainHandItem();
            if (IGun.getIGunOrNull(held) == null) {
                held = gun;
            }
            magicData.initiateCast(spell, data.getLevel(), duration, CastSource.SWORD, MAIN_HAND);
            magicData.setPlayerCastingItem(held);
            spell.onServerPreCast(player.level(), data.getLevel(), player, magicData);
            ContinuousCastGuard.track(player, offhand, castingGunId);
            // 只同步施法状态(不发 OnCastStartedPacket): 客户端据此渲染持续法术的光束等, 但不播放施法动画。
            PacketDistributor.sendToPlayer(player, new UpdateCastingStatePacket(
                    spell.getSpellId(), data.getLevel(), duration, CastSource.SWORD, MAIN_HAND));
            return true;
        }
        if (target != null) {
            magicData.setAdditionalCastData(new TargetEntityCastData(target));
        }
        try {
            spell.castSpell(player.level(), data.getLevel(), player, CastSource.SWORD, true);
            return true;
        } finally {
            if (target != null) {
                magicData.resetAdditionalCastData();
            }
        }
    }

    /** 目标限制类法术: 命中目标不合法时不施放, 避免白白消耗法力与冷却(如献祭只能作用于自己的召唤物)。 */
    private static boolean canCastOnTarget(ServerPlayer player, AbstractSpell spell, LivingEntity target) {
        if (spell instanceof SacrificeSpell) {
            if (!(target instanceof IMagicSummon summon)) {
                return false;
            }
            Entity summoner = summon.getSummoner();
            return summoner != null && summoner.getUUID().equals(player.getUUID());
        }
        return true;
    }

    private static ResourceLocation gunId(ItemStack gun) {
        IGun iGun = IGun.getIGunOrNull(gun);
        return iGun == null ? null : iGun.getGunId(gun);
    }

    /** 是否为需要锁定目标的法术(该法术类使用了 Iron's 的目标获取)。 */
    private static boolean needsTarget(AbstractSpell spell) {
        Class<?> clazz = spell.getClass();
        Boolean cached = TARGETED_CACHE.get(clazz);
        if (cached != null) {
            return cached;
        }
        boolean targeted = false;
        for (Class<?> type = clazz; type != null && type != AbstractSpell.class; type = type.getSuperclass()) {
            if (classUsesTarget(type)) {
                targeted = true;
                break;
            }
        }
        TARGETED_CACHE.put(clazz, targeted);
        return targeted;
    }

    private static boolean classUsesTarget(Class<?> type) {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getResourceAsStream(resource)) {
            if (in == null) {
                return false;
            }
            String body = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            for (String marker : TARGET_MARKERS) {
                if (body.contains(marker)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
