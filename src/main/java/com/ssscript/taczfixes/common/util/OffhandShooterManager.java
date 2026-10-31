package com.ssscript.taczfixes.common.util;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.event.common.GunFireSelectEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.entity.shooter.LivingEntityBolt;
import com.tacz.guns.entity.shooter.LivingEntityDrawGun;
import com.tacz.guns.entity.shooter.LivingEntityMelee;
import com.tacz.guns.entity.shooter.LivingEntityReload;
import com.tacz.guns.entity.shooter.LivingEntityShoot;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.event.ServerMessageGunFireSelect;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.modifier.custom.ExtraMovementModifier;
import com.tacz.guns.resource.modifier.custom.WeightModifier;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.MoveSpeed;
import com.tacz.guns.util.time.GunTime;
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.ssscript.taczfixes.common.util.DualWieldStackId;
import com.ssscript.taczfixes.common.util.OffhandGunPropertyResolver;
import com.ssscript.taczfixes.common.network.ServerMessageDualWieldEligibility;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandState;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.Mod;
import com.ssscript.taczfixes.common.network.PacketDistributor;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.player.Player;

@net.neoforged.fml.common.EventBusSubscriber
public final class OffhandShooterManager {
    static final long MANUAL_ACTION_RETRY_TIMEOUT_MS = 5000;
    static final long MAX_SERVER_BOLT_DURATION_MS = 10000;
    static final long FIRE_SELECT_REQUEST_INTERVAL_MS = 150;
    static final long CONTROL_REQUEST_INTERVAL_MS = 50;
    private static final ResourceLocation OFFHAND_WEIGHT_SPEED_MODIFIER_UUID = ResourceLocation.fromNamespaceAndPath("taczfixes", "offhand_weight_speed");
    private static final ResourceLocation OFFHAND_EXTRA_SPEED_MODIFIER_UUID = ResourceLocation.fromNamespaceAndPath("taczfixes", "offhand_extra_speed");
    private static final Map<UUID, OffhandShooter> SHOOTERS = new ConcurrentHashMap();
    private static final Map<UUID, DualWieldEligibility.Rules> ELIGIBILITY_RULES = new ConcurrentHashMap();
    private static final Set<ShooterDataHolder> OFFHAND_DATA = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap()));
    private static final Map<LivingEntity, ShooterDataHolder> EXTERNAL_CONTEXTS = Collections.synchronizedMap(new WeakHashMap());
    private static final ThreadLocal<Deque<ShooterDataHolder>> ACTIVE_DATA = new ThreadLocal<>();

    private OffhandShooterManager() {
    }

    public static void melee(ServerPlayer player, UUID expectedStackId) {
        long mainRemaining = com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(player).getSynMeleeCoolDown();
        if (meleeStillBlocking(mainRemaining, MeleeCooldownHelper.totalCooldownMillis(player.getMainHandItem()))) {
            return;
        }
        OffhandShooter shooter = getValidated(player);
        if (shooter == null || !shooter.matchesBoundStack(expectedStackId)) {
            return;
        }
        shooter.melee();
    }

    /** 鍓墜杩戞垬鍐峰嵈鏄惁浠嶉樆鎸′富鎵嬭繎鎴?鍐峰嵈杩涜鍒伴厤缃瘮渚嬪悗涓嶅啀闃绘尅)銆?*/
    public static boolean isOffhandMeleeBlockingMainHand(ServerPlayer player) {
        OffhandShooter shooter = getValidated(player);
        return shooter != null && shooter.meleeBlocksMainHand();
    }

    static boolean meleeStillBlocking(long remaining, long total) {
        if (remaining <= 0L) {
            return false;
        }
        if (total <= 0L) {
            return true;
        }
        double switchPercent = com.ssscript.taczfixes.common.config.Config.DUAL_WIELD_MELEE_SWITCH_PERCENT.get();
        return remaining > total * (1.0d - switchPercent);
    }

    public static void shoot(ServerPlayer player, UUID expectedStackId, long timestamp, float chargeProgress) {
        ShootOutcome shootOutcome;
        long shootCoolDown;
        OffhandShooter shooter = getValidated(player);
        boolean stackMatched = shooter != null && shooter.matchesBoundStack(expectedStackId);
        boolean underwaterBlocked = stackMatched && com.ssscript.taczfixes.common.util.UnderwaterShooting.isBlocked(player, player.getOffhandItem());
        if (stackMatched && !underwaterBlocked) {
            shootOutcome = shooter.shoot(timestamp, chargeProgress);
        } else {
            shootOutcome = new ShootOutcome(ShootResult.UNKNOWN_FAIL, false, false);
        }
        ShootOutcome outcome = shootOutcome;
        boolean accepted = outcome.result() == ShootResult.SUCCESS && (!outcome.bulletAttemptedDuringSynchronousShoot() || outcome.bulletSpawnedDuringSynchronousShoot());
        if (stackMatched) {
            shootCoolDown = shooter.getShootCoolDown();
        } else {
            shootCoolDown = 0;
        }
        long authoritativeShootCoolDown = shootCoolDown;
        sendActionResult(player, 0, expectedStackId, 0, timestamp, accepted, stackMatched, -1, authoritativeShootCoolDown);
    }

    public static void reload(ServerPlayer player, UUID expectedStackId, int requestId) {
        OffhandShooter shooter = getValidated(player);
        boolean stackMatched = shooter != null && shooter.matchesBoundStack(expectedStackId);
        boolean accepted = stackMatched && requestId > 0 && shooter.reload(requestId);
        sendActionResult(player, 1, expectedStackId, requestId, Long.MIN_VALUE, accepted, stackMatched, -1, 0L);
    }

    public static void cancelReload(ServerPlayer player, UUID expectedStackId, int reloadRequestId) {
        OffhandShooter shooter = getValidated(player);
        boolean stackMatched = shooter != null && shooter.matchesBoundStack(expectedStackId);
        boolean accepted = stackMatched && reloadRequestId > 0 && shooter.cancelReload(reloadRequestId);
        sendActionResult(player, 4, expectedStackId, reloadRequestId, Long.MIN_VALUE, accepted, stackMatched, -1, 0L);
    }

    public static void bolt(ServerPlayer player, UUID expectedStackId, int requestId) {
        OffhandShooter shooter = getValidated(player);
        boolean stackMatched = shooter != null && shooter.matchesBoundStack(expectedStackId);
        boolean accepted = stackMatched && requestId > 0 && shooter.bolt(requestId);
        sendActionResult(player, 2, expectedStackId, requestId, Long.MIN_VALUE, accepted, stackMatched, -1, 0L);
    }

    public static void fireSelect(ServerPlayer player, UUID expectedStackId, int requestId) {
        OffhandShooter shooter = getValidated(player);
        boolean stackMatched = shooter != null && shooter.matchesBoundStack(expectedStackId);
        boolean accepted = stackMatched && requestId > 0 && shooter.fireSelect();
        sendActionResult(player, 3, expectedStackId, requestId, Long.MIN_VALUE, accepted, stackMatched, getFireModeOrdinal(player, expectedStackId), 0L);
    }

    public static boolean isOffhandData(ShooterDataHolder dataHolder) {
        return dataHolder != null && OFFHAND_DATA.contains(dataHolder);
    }

    /** 褰撳墠绾跨▼鑻ユ澶勪簬璇ュ疄浣撶殑鍓墜澶勭悊涓婁笅鏂? 杩斿洖鍏跺壇鎵嬫灙姊? 鍚﹀垯杩斿洖绌恒€?*/
    public static ItemStack getActiveOffhandGunStack(LivingEntity entity) {
        if (entity == null) {
            return ItemStack.EMPTY;
        }
        ShooterDataHolder active = getActiveData();
        if (active == null || !isCurrentOffhandContext(entity, active)) {
            return ItemStack.EMPTY;
        }
        return entity.getOffhandItem();
    }

    public static void registerExternalContext(LivingEntity entity, ShooterDataHolder dataHolder) {
        if (entity == null || dataHolder == null) {
            return;
        }
        EXTERNAL_CONTEXTS.put(entity, dataHolder);
        OFFHAND_DATA.add(dataHolder);
    }

    public static void unregisterExternalContext(LivingEntity entity, ShooterDataHolder dataHolder) {
        if (entity == null || dataHolder == null) {
            return;
        }
        synchronized (EXTERNAL_CONTEXTS) {
            if (EXTERNAL_CONTEXTS.get(entity) == dataHolder) {
                EXTERNAL_CONTEXTS.remove(entity);
            }
        }
    }

    public static ShooterDataHolder getActiveData() {
        Deque<ShooterDataHolder> stack = ACTIVE_DATA.get();
        if (stack == null) {
            return null;
        }
        return stack.peek();
    }

    public static void pushActiveData(ShooterDataHolder dataHolder) {
        if (dataHolder != null) {
            Deque<ShooterDataHolder> stack = ACTIVE_DATA.get();
            if (stack == null) {
                stack = new ArrayDeque();
                ACTIVE_DATA.set(stack);
            }
            stack.push(dataHolder);
        }
    }

    public static void popActiveData() {
        Deque<ShooterDataHolder> stack = ACTIVE_DATA.get();
        if (stack != null && !stack.isEmpty()) {
            stack.pop();
        }
        if (stack == null || stack.isEmpty()) {
            ACTIVE_DATA.remove();
        }
    }

    public static void recordManualActionChamberRefill(ShooterDataHolder dataHolder, ItemStack itemStack) {
        if (dataHolder == null || itemStack == null || itemStack.isEmpty()) {
            return;
        }
        for (OffhandShooter shooter : SHOOTERS.values()) {
            if (shooter.data == dataHolder) {
                shooter.recordManualActionChamberRefill(itemStack);
                return;
            }
        }
    }

    public static void recordBulletAttempt(ShooterDataHolder dataHolder, ItemStack itemStack) {
        if (dataHolder == null || itemStack == null || itemStack.isEmpty()) {
            return;
        }
        for (OffhandShooter shooter : SHOOTERS.values()) {
            if (shooter.data == dataHolder) {
                shooter.recordBulletAttempt(itemStack);
                return;
            }
        }
    }

    public static void finishBulletAttempt(ShooterDataHolder dataHolder, ItemStack itemStack) {
        if (dataHolder == null || itemStack == null || itemStack.isEmpty()) {
            return;
        }
        for (OffhandShooter shooter : SHOOTERS.values()) {
            if (shooter.data == dataHolder) {
                shooter.finishBulletAttempt(itemStack);
                return;
            }
        }
    }

    public static void recordBulletSpawn(ShooterDataHolder dataHolder, ItemStack itemStack) {
        if (dataHolder == null || itemStack == null || itemStack.isEmpty()) {
            return;
        }
        for (OffhandShooter shooter : SHOOTERS.values()) {
            if (shooter.data == dataHolder) {
                shooter.recordBulletSpawn(itemStack);
                return;
            }
        }
    }

    public static long captureManualAsyncTaskGeneration(ShooterDataHolder dataHolder, ItemStack itemStack) {
        if (dataHolder == null || itemStack == null || itemStack.isEmpty()) {
            return Long.MIN_VALUE;
        }
        for (OffhandShooter shooter : SHOOTERS.values()) {
            if (shooter.data == dataHolder) {
                return shooter.captureManualAsyncTaskGeneration(itemStack);
            }
        }
        return Long.MIN_VALUE;
    }

    public static void recordManualSafeAsyncTask(ShooterDataHolder dataHolder, ItemStack itemStack, long generation) {
        if (generation == Long.MIN_VALUE || dataHolder == null || itemStack == null || itemStack.isEmpty()) {
            return;
        }
        for (OffhandShooter shooter : SHOOTERS.values()) {
            if (shooter.data == dataHolder) {
                shooter.recordManualSafeAsyncTask(itemStack, generation);
                return;
            }
        }
    }

    public static void finishManualSafeAsyncTask(ShooterDataHolder dataHolder, ItemStack itemStack, long generation) {
        if (generation == Long.MIN_VALUE || dataHolder == null || itemStack == null) {
            return;
        }
        for (OffhandShooter shooter : SHOOTERS.values()) {
            if (shooter.data == dataHolder) {
                shooter.finishManualSafeAsyncTask(itemStack, generation);
                return;
            }
        }
    }

    public static boolean isManualAsyncTaskGenerationCurrent(LivingEntity entity, ShooterDataHolder dataHolder, ItemStack itemStack, long generation) {
        if (generation == Long.MIN_VALUE || entity == null || dataHolder == null || itemStack == null || itemStack.isEmpty() || !isCurrentOffhandContext(entity, dataHolder)) {
            return false;
        }
        for (OffhandShooter shooter : SHOOTERS.values()) {
            if (shooter.data == dataHolder) {
                return shooter.isManualAsyncTaskGenerationCurrent(itemStack, generation);
            }
        }
        return false;
    }

    private static boolean isOffhandGunContext(LivingEntity entity) {
        return entity != null && IGun.getIGunOrNull(entity.getOffhandItem()) != null;
    }

    public static boolean isCurrentOffhandContext(LivingEntity entity, ShooterDataHolder dataHolder) {
        if (entity == null || !isOffhandData(dataHolder) || entity.isSpectator() || entity.isDeadOrDying() || !isOffhandGunContext(entity)) {
            return false;
        }
        if (!(entity instanceof ServerPlayer)) {
            return EXTERNAL_CONTEXTS.get(entity) == dataHolder;
        }
        ServerPlayer player = (ServerPlayer) entity;
        OffhandShooter shooter = SHOOTERS.get(player.getUUID());
        return shooter != null && shooter.player == player && shooter.data == dataHolder;
    }

    public static ShooterDataHolder findOffhandData(LivingEntity entity, ItemStack stack) {
        ShooterDataHolder resolvedData;
        if (entity == null || stack == null) {
            return null;
        }
        ShooterDataHolder activeData = getActiveData();
        if (isCurrentOffhandContext(entity, activeData) && activeData.currentGunItem != null && activeData.currentGunItem.get() == stack) {
            return activeData;
        }
        if (entity instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) entity;
            OffhandShooter shooter = SHOOTERS.get(player.getUUID());
            resolvedData = shooter == null ? null : shooter.data;
        } else {
            resolvedData = EXTERNAL_CONTEXTS.get(entity);
        }
        if (!isCurrentOffhandContext(entity, resolvedData)) {
            return null;
        }
        ItemStack current = entity.getOffhandItem();
        if (current == stack) {
            return resolvedData;
        }
        return null;
    }

    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (false || event.getEntity().level().isClientSide()) {
            return;
        }
        Player serverPlayer = event.getEntity();
        if (!(serverPlayer instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer) serverPlayer;
        if (!ELIGIBILITY_RULES.containsKey(player.getUUID()) || player.tickCount % 20 == 0) {
            synchronizeEligibilityRules(player);
        }
        OffhandShooter shooter = SHOOTERS.get(player.getUUID());
        if (shooter != null && shooter.player != player) {
            remove(player.getUUID());
            shooter = null;
        }
        if (isOffhandGunContext(player)) {
            if (shooter == null) {
                shooter = create(player);
            }
            shooter.tick();
            updateWeightSpeedModifier(player, shooter);
            return;
        }
        if (shooter != null) {
            remove(player.getUUID());
            removeWeightSpeedModifier(player);
        } else {
            removeWeightSpeedModifier(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player entity = event.getEntity();
        if (entity instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) entity;
            synchronizeEligibilityRules(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player entity = event.getEntity();
        if (entity instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) entity;
            removeWeightSpeedModifier(player);
        }
        ELIGIBILITY_RULES.remove(event.getEntity().getUUID());
        remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        if (original instanceof ServerPlayer) {
            ServerPlayer originalPlayer = (ServerPlayer) original;
            removeWeightSpeedModifier(originalPlayer);
        }
        Player entity = event.getEntity();
        if (entity instanceof ServerPlayer) {
            ServerPlayer clonedPlayer = (ServerPlayer) entity;
            removeWeightSpeedModifier(clonedPlayer);
        }
        ELIGIBILITY_RULES.remove(event.getEntity().getUUID());
        remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player entity = event.getEntity();
        if (entity instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) entity;
            removeWeightSpeedModifier(player);
            ELIGIBILITY_RULES.remove(player.getUUID());
            remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) entity;
            removeWeightSpeedModifier(player);
            remove(player.getUUID());
        }
    }

    private static void updateWeightSpeedModifier(ServerPlayer player, OffhandShooter shooter) {
        double baseMultiplier;
        AttributeInstance speedAttribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        AttachmentCacheProperty cacheProperty = shooter.data.cacheProperty;
        if (speedAttribute == null || cacheProperty == null || !(player.getOffhandItem().getItem() instanceof AbstractGunItem)) {
            removeWeightSpeedModifier(player);
            return;
        }
        double weightFactor = ((Double) SyncConfig.WEIGHT_SPEED_MULTIPLIER.get()).doubleValue();
        Float cachedWeight = (Float) cacheProperty.getCache(WeightModifier.ID);
        if (weightFactor > 0.0d && cachedWeight != null && Float.isFinite(cachedWeight.floatValue())) {
            float targetWeightSpeed = cachedWeight.floatValue() * ((float) (-weightFactor));
            updateSpeedModifier(speedAttribute, OFFHAND_WEIGHT_SPEED_MODIFIER_UUID, "Offhand Gun Weight Speed Modifier", targetWeightSpeed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        } else {
            speedAttribute.removeModifier(OFFHAND_WEIGHT_SPEED_MODIFIER_UUID);
        }
        MoveSpeed cachedExtraMovement = (MoveSpeed) cacheProperty.getCache(ExtraMovementModifier.ID);
        if (cachedExtraMovement == null) {
            speedAttribute.removeModifier(OFFHAND_EXTRA_SPEED_MODIFIER_UUID);
            return;
        }
        if (shooter.data.reloadStateType.isReloading()) {
            baseMultiplier = cachedExtraMovement.getReloadMultiplier();
        } else {
            baseMultiplier = cachedExtraMovement.getBaseMultiplier();
        }
        double targetExtraSpeed = baseMultiplier;
        updateSpeedModifier(speedAttribute, OFFHAND_EXTRA_SPEED_MODIFIER_UUID, "Offhand Extra Gun Speed Modifier", targetExtraSpeed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void updateSpeedModifier(AttributeInstance speedAttribute, ResourceLocation modifierId, String name, double amount, AttributeModifier.Operation operation) {
        if (!Double.isFinite(amount)) {
            speedAttribute.removeModifier(modifierId);
            return;
        }
        AttributeModifier currentModifier = speedAttribute.getModifier(modifierId);
        if (currentModifier == null || currentModifier.amount() != amount || currentModifier.operation() != operation) {
            speedAttribute.removeModifier(modifierId);
            speedAttribute.addTransientModifier(new AttributeModifier(modifierId, amount, operation));
        }
    }

    private static void removeWeightSpeedModifier(ServerPlayer player) {
        AttributeInstance speedAttribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttribute != null) {
            speedAttribute.removeModifier(OFFHAND_WEIGHT_SPEED_MODIFIER_UUID);
            speedAttribute.removeModifier(OFFHAND_EXTRA_SPEED_MODIFIER_UUID);
        }
    }

    private static void synchronizeEligibilityRules(ServerPlayer player) {
        if (player == null || player.connection == null) {
            return;
        }
        DualWieldEligibility.Rules rules = DualWieldEligibility.getServerRules();
        DualWieldEligibility.Rules previous = ELIGIBILITY_RULES.put(player.getUUID(), rules);
        if (rules.equals(previous)) {
            return;
        }
        com.ssscript.taczfixes.common.network.NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> {
            return player;
        }), new ServerMessageDualWieldEligibility(rules));
    }

    static void sendActionResult(ServerPlayer player, int actionType, UUID requestedStackId, int requestId, long shootTimestamp, boolean accepted, boolean stackMatched, int authoritativeFireMode, long authoritativeShootCoolDown) {
        if (player == null || player.connection == null || requestedStackId == null) {
            return;
        }
        com.ssscript.taczfixes.common.network.NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> {
            return player;
        }), new ServerMessageOffhandActionResult(actionType, requestedStackId, requestId, shootTimestamp, accepted, stackMatched, authoritativeFireMode, authoritativeShootCoolDown));
    }

    private static int getFireModeOrdinal(ServerPlayer player, UUID requestedStackId) {
        if (player == null || requestedStackId == null || !requestedStackId.equals(DualWieldStackId.get(player.getOffhandItem()))) {
            return -1;
        }
        IGun gun = IGun.getIGunOrNull(player.getOffhandItem());
        FireMode fireMode = gun == null ? null : gun.getFireMode(player.getOffhandItem());
        if (fireMode == null) {
            return -1;
        }
        return fireMode.ordinal();
    }

    private static OffhandShooter getValidated(ServerPlayer player) {
        if (player == null || player.isSpectator() || player.isDeadOrDying() || !isOffhandGunContext(player)) {
            return null;
        }
        OffhandShooter shooter = SHOOTERS.get(player.getUUID());
        if (shooter != null && shooter.player != player) {
            remove(player.getUUID());
            shooter = null;
        }
        if (shooter == null) {
            shooter = create(player);
        }
        shooter.bindIfChanged();
        shooter.syncPlayerGlobalState();
        return shooter;
    }

    private static OffhandShooter create(ServerPlayer player) {
        OffhandShooter shooter = new OffhandShooter(player);
        SHOOTERS.put(player.getUUID(), shooter);
        OFFHAND_DATA.add(shooter.data);
        shooter.bindIfChanged();
        return shooter;
    }

    private static void remove(UUID playerId) {
        OffhandShooter shooter = SHOOTERS.remove(playerId);
        if (shooter != null) {
            shooter.interruptReloadBeforeRemoval();
        }
    }

    record ShootOutcome(ShootResult result, boolean bulletAttemptedDuringSynchronousShoot, boolean bulletSpawnedDuringSynchronousShoot) {

        ShootOutcome(ShootResult result, boolean bulletAttemptedDuringSynchronousShoot, boolean bulletSpawnedDuringSynchronousShoot) {
            this.result = result;
            this.bulletAttemptedDuringSynchronousShoot = bulletAttemptedDuringSynchronousShoot;
            this.bulletSpawnedDuringSynchronousShoot = bulletSpawnedDuringSynchronousShoot;
        }

        public ShootResult result() {
            return this.result;
        }

        public boolean bulletAttemptedDuringSynchronousShoot() {
            return this.bulletAttemptedDuringSynchronousShoot;
        }

        public boolean bulletSpawnedDuringSynchronousShoot() {
            return this.bulletSpawnedDuringSynchronousShoot;
        }
    }

}
