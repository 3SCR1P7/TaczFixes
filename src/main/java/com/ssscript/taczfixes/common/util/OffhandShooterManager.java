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
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.ssscript.taczfixes.common.util.DualWieldStackId;
import com.ssscript.taczfixes.common.util.OffhandGunPropertyResolver;
import com.ssscript.taczfixes.common.network.ServerMessageDualWieldEligibility;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandState;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.runtime.ObjectMethods;
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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.player.Player;

@Mod.EventBusSubscriber
public final class OffhandShooterManager {
    public static final long NO_MANUAL_ASYNC_TASK_GENERATION = Long.MIN_VALUE;
    private static final long MANUAL_ACTION_RETRY_TIMEOUT_MS = 5000;
    private static final long MAX_SERVER_BOLT_DURATION_MS = 10000;
    private static final long FIRE_SELECT_REQUEST_INTERVAL_MS = 150;
    private static final long CONTROL_REQUEST_INTERVAL_MS = 50;
    private static final UUID OFFHAND_WEIGHT_SPEED_MODIFIER_UUID = UUID.fromString("F861CC11-683E-4E22-B62D-D8BAAA2ECC57");
    private static final UUID OFFHAND_EXTRA_SPEED_MODIFIER_UUID = UUID.fromString("DA2A9E61-6B92-48B5-B30C-55ED296FDFA8");
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

    /** 副手近战冷却是否仍阻挡主手近战(冷却进行到配置比例后不再阻挡)。 */
    public static boolean isOffhandMeleeBlockingMainHand(ServerPlayer player) {
        OffhandShooter shooter = getValidated(player);
        return shooter != null && shooter.meleeBlocksMainHand();
    }

    private static boolean meleeStillBlocking(long remaining, long total) {
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

    /** 当前线程若正处于该实体的副手处理上下文, 返回其副手枪械; 否则返回空。 */
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

    public static boolean isCurrentOffhandContext(LivingEntity entity, ShooterDataHolder dataHolder) {
        if (entity == null || !isOffhandData(dataHolder) || entity.isSpectator() || entity.isDeadOrDying() || !DualWieldEligibility.isDualWielding(entity)) {
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
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        Player serverPlayer = event.player;
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
        if (DualWieldEligibility.isDualWielding(player)) {
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
            updateSpeedModifier(speedAttribute, OFFHAND_WEIGHT_SPEED_MODIFIER_UUID, "Offhand Gun Weight Speed Modifier", targetWeightSpeed, AttributeModifier.Operation.MULTIPLY_BASE);
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
        updateSpeedModifier(speedAttribute, OFFHAND_EXTRA_SPEED_MODIFIER_UUID, "Offhand Extra Gun Speed Modifier", targetExtraSpeed, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    private static void updateSpeedModifier(AttributeInstance speedAttribute, UUID modifierId, String name, double amount, AttributeModifier.Operation operation) {
        if (!Double.isFinite(amount)) {
            speedAttribute.removeModifier(modifierId);
            return;
        }
        AttributeModifier currentModifier = speedAttribute.getModifier(modifierId);
        if (currentModifier == null || currentModifier.getAmount() != amount || currentModifier.getOperation() != operation) {
            speedAttribute.removeModifier(modifierId);
            speedAttribute.addTransientModifier(new AttributeModifier(modifierId, name, amount, operation));
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

    private static void sendActionResult(ServerPlayer player, int actionType, UUID requestedStackId, int requestId, long shootTimestamp, boolean accepted, boolean stackMatched, int authoritativeFireMode, long authoritativeShootCoolDown) {
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
        if (player == null || player.isSpectator() || player.isDeadOrDying() || !DualWieldEligibility.isDualWielding(player)) {
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

    private record ShootOutcome(ShootResult result, boolean bulletAttemptedDuringSynchronousShoot, boolean bulletSpawnedDuringSynchronousShoot) {

        private ShootOutcome(ShootResult result, boolean bulletAttemptedDuringSynchronousShoot, boolean bulletSpawnedDuringSynchronousShoot) {
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

    private static final class OffhandShooter {
        private final ServerPlayer player;
        private final LivingEntityDrawGun draw;
        private final LivingEntityShoot shoot;
        private final LivingEntityReload reload;
        private final LivingEntityBolt bolt;
        private final LivingEntityMelee melee;
        private ResourceLocation boundGunId;
        private UUID boundStackId;
        private int reloadRequestId;
        private int boltRequestId;
        private boolean lastSentBolting;
        private boolean lastObservedManualActionBoltReady;
        private boolean manualActionBoltReadyInitialized;
        private boolean hasShootTimestamp;
        private boolean hasLastShootTimestamp;
        private boolean manualActionEpisodeActive;
        private boolean manualActionDelayedShotPending;
        private boolean manualActionDelayedBulletAttempted;
        private int manualActionDelayedAttemptsInProgress;
        private boolean manualActionDelayedAttemptCompleted;
        private boolean manualActionDelayedBulletSpawned;
        private boolean manualActionSafeAsyncTaskRegisteredDuringShoot;
        private int manualActionDelayedAsyncTasksOutstanding;
        private long manualActionAsyncGeneration;
        private boolean manualActionBoltRequestConsumed;
        private boolean manualActionBoltRequestInProgress;
        private boolean shootCaptureActive;
        private boolean bulletAttemptedDuringSynchronousShoot;
        private boolean bulletSpawnedDuringSynchronousShoot;
        private boolean manualActionShotCaptureActive;
        private boolean manualActionChamberRefilledDuringShot;
        private int manualActionAmmoCountBeforeShot;
        private boolean manualActionSyntheticCycle;
        private final ShooterDataHolder data = new ShooterDataHolder();
        private int lastSentReloadStateType = -1;
        private long lastStatePacketTimestamp = -1;
        private boolean stateDirty = true;
        private long manualActionDelayedShootTimestamp = Long.MIN_VALUE;
        private long manualActionFailureTimestamp = -1;
        private long serverBoltStartTimestamp = -1;
        private long lastReloadRequestTimestamp = -1;
        private long lastCancelReloadRequestTimestamp = -1;
        private long lastBoltRequestTimestamp = -1;
        private long lastFireSelectRequestTimestamp = -1;
        private final ItemStack[] boundAttachments = new ItemStack[AttachmentType.values().length];
        private ItemStack boundStack = ItemStack.EMPTY;

        private OffhandShooter(ServerPlayer player) {
            this.player = player;
            this.draw = new LivingEntityDrawGun(player, this.data);
            this.shoot = new LivingEntityShoot(player, this.data, this.draw);
            this.reload = new LivingEntityReload(player, this.data, this.draw, this.shoot);
            this.bolt = new LivingEntityBolt(this.data, player, this.draw, this.shoot);
            this.melee = new LivingEntityMelee(player, this.data, this.draw);
        }

        private void melee() {
            pushActiveData(this.data);
            try {
                this.melee.melee();
            } finally {
                popActiveData();
            }
        }

        private void bindIfChanged() {
            ItemStack mainStack = this.player.getMainHandItem();
            UUID mainStackId = null;
            if (IGun.getIGunOrNull(mainStack) != null) {
                mainStackId = DualWieldStackId.getOrCreate(mainStack);
            }
            ItemStack stack = this.player.getOffhandItem();
            IGun gun = IGun.getIGunOrNull(stack);
            ResourceLocation gunId = gun == null ? null : gun.getGunId(stack);
            UUID stackId = gunId == null ? null : DualWieldStackId.getOrCreate(stack);
            if (gunId == null || stackId == null || gun == null) {
                return;
            }
            boolean duplicatesMainId = (stack == mainStack || mainStackId == null || !mainStackId.equals(stackId)) ? false : true;
            boolean duplicatesInventoryId = hasDuplicateInventoryStackId(stack, stackId);
            if (duplicatesMainId || duplicatesInventoryId) {
                stackId = DualWieldStackId.regenerate(stack);
            }
            synchronizeShootBaseTimestamp();
            boolean identityChanged = (gunId.equals(this.boundGunId) && stackId.equals(this.boundStackId)) ? false : true;
            boolean attachmentChanged = updateAttachmentSignature(gun, stack);
            if (!identityChanged && !attachmentChanged) {
                this.boundStack = stack;
                return;
            }
            if (identityChanged) {
                interruptReloadBeforeRemoval();
                advanceManualActionAsyncGeneration();
                this.data.initialData();
                ShooterDataHolder shooterDataHolder = this.data;
                ServerPlayer serverPlayer = this.player;
                Objects.requireNonNull(serverPlayer);
                shooterDataHolder.currentGunItem = serverPlayer::getOffhandItem;
                long now = System.currentTimeMillis();
                this.data.drawTimestamp = now;
                this.data.lastShootTimestamp = -1L;
                this.data.heatTimestamp = now;
                this.data.currentPutAwayTimeS = 0.0f;
                this.data.baseTimestamp = IGunOperator.fromLivingEntity(this.player).getDataHolder().baseTimestamp;
                this.boundGunId = gunId;
                this.boundStackId = stackId;
                this.boundStack = stack;
                this.reloadRequestId = 0;
                this.boltRequestId = 0;
                this.serverBoltStartTimestamp = -1L;
                this.lastReloadRequestTimestamp = -1L;
                this.lastCancelReloadRequestTimestamp = -1L;
                this.lastBoltRequestTimestamp = -1L;
                this.lastFireSelectRequestTimestamp = -1L;
                this.hasShootTimestamp = false;
                this.hasLastShootTimestamp = false;
                this.lastObservedManualActionBoltReady = false;
                this.manualActionBoltReadyInitialized = false;
                resetManualActionEpisode();
                beginInitialManualActionEpisodeIfNeeded(stack, gun);
                this.stateDirty = true;
            } else {
                this.boundStack = stack;
            }
            rebuildCache();
            AttachmentPropertyManager.postChangeEvent(this.player, this.player.getMainHandItem());
        }

        private boolean hasDuplicateInventoryStackId(ItemStack currentStack, UUID currentStackId) {
            if (currentStackId == null) {
                return false;
            }
            int inventorySize = this.player.getInventory().getContainerSize();
            for (int slot = 0; slot < inventorySize; slot++) {
                ItemStack candidate = this.player.getInventory().getItem(slot);
                if (candidate != currentStack && !candidate.isEmpty()) {
                    UUID candidateId = DualWieldStackId.get(candidate);
                    if (currentStackId.equals(candidateId)) {
                        return true;
                    }
                }
            }
            ItemStack carried = this.player.containerMenu.getCarried();
            if (hasSameStackIdOnAnotherStack(currentStack, currentStackId, carried)) {
                return true;
            }
            Iterator it = this.player.containerMenu.slots.iterator();
            while (it.hasNext()) {
                Slot slot2 = (Slot) it.next();
                if (hasSameStackIdOnAnotherStack(currentStack, currentStackId, slot2.getItem())) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasSameStackIdOnAnotherStack(ItemStack currentStack, UUID currentStackId, ItemStack candidate) {
            return (candidate == currentStack || candidate == null || candidate.isEmpty() || !currentStackId.equals(DualWieldStackId.get(candidate))) ? false : true;
        }

        private boolean matchesBoundStack(UUID expectedStackId) {
            UUID liveStackId = DualWieldStackId.get(this.player.getOffhandItem());
            return expectedStackId != null && expectedStackId.equals(this.boundStackId) && expectedStackId.equals(liveStackId);
        }

        private void interruptReloadBeforeRemoval() {
            if (!this.data.reloadStateType.isReloading() || this.boundStack == null || this.boundStack.isEmpty()) {
                return;
            }
            ItemStack interruptedStack = this.boundStack;
            Supplier<ItemStack> currentSupplier = this.data.currentGunItem;
            this.data.currentGunItem = () -> {
                return interruptedStack;
            };
            OffhandShooterManager.pushActiveData(this.data);
            try {
                try {
                    this.reload.cancelReload();
                    this.reload.tickReloadState();
                    OffhandShooterManager.popActiveData();
                    this.data.currentGunItem = currentSupplier;
                } catch (RuntimeException exception) {
                    TaczFixesMod.LOGGER.error("Failed to interrupt offhand reload for {}", this.boundGunId, exception);
                    OffhandShooterManager.popActiveData();
                    this.data.currentGunItem = currentSupplier;
                }
            } catch (Throwable th) {
                OffhandShooterManager.popActiveData();
                this.data.currentGunItem = currentSupplier;
                throw th;
            }
        }

        private boolean updateAttachmentSignature(IGun gun, ItemStack stack) {
            boolean changed = false;
            for (AttachmentType type : AttachmentType.values()) {
                if (type != AttachmentType.NONE) {
                    ItemStack attachment = gun.getAttachment(stack, type);
                    int index = type.ordinal();
                    ItemStack boundAttachment = this.boundAttachments[index];
                    if (boundAttachment == null || !ItemStack.matches(attachment, boundAttachment)) {
                        this.boundAttachments[index] = attachment.copy();
                        changed = true;
                    }
                }
            }
            return changed;
        }

        private void tick() {
            bindIfChanged();
            if (this.data.currentGunItem == null) {
                return;
            }
            OffhandShooterManager.pushActiveData(this.data);
            try {
                ReloadState reloadState = this.reload.tickReloadState();
                boolean wasBolting = this.data.isBolting;
                this.bolt.tickBolt();
                this.melee.scheduleTickMelee();
                boolean boltTimedOut = enforceBoltTimeout(wasBolting);
                updateManualActionEpisodeAfterTick(wasBolting);
                if (boltTimedOut) {
                    this.manualActionBoltRequestConsumed = false;
                    this.manualActionFailureTimestamp = -1L;
                    this.stateDirty = true;
                }
                ItemStack stack = this.player.getOffhandItem();
                IGun gun = IGun.getIGunOrNull(stack);
                beginInitialManualActionEpisodeIfNeeded(stack, gun);
                updateManualActionBoltReadyTransition();
                Item abstractGunItemM_41720_ = stack.getItem();
                if (abstractGunItemM_41720_ instanceof AbstractGunItem) {
                    AbstractGunItem gunItem = (AbstractGunItem) abstractGunItemM_41720_;
                    gunItem.tickHeat(this.data, stack, this.player);
                }
                OffhandShooterManager.popActiveData();
                synchronizeState(reloadState, this.stateDirty);
                this.stateDirty = false;
                syncPlayerGlobalState();
            } catch (Throwable th) {
                OffhandShooterManager.popActiveData();
                throw th;
            }
        }

        private boolean enforceBoltTimeout(boolean wasBolting) {
            long now = System.currentTimeMillis();
            if (!this.data.isBolting) {
                this.serverBoltStartTimestamp = -1L;
                return false;
            }
            if (!wasBolting || this.serverBoltStartTimestamp < 0) {
                this.serverBoltStartTimestamp = now;
                return false;
            }
            if (now < this.serverBoltStartTimestamp) {
                this.serverBoltStartTimestamp = now;
                return false;
            }
            if (now - this.serverBoltStartTimestamp < OffhandShooterManager.MAX_SERVER_BOLT_DURATION_MS) {
                return false;
            }
            this.data.isBolting = false;
            this.serverBoltStartTimestamp = -1L;
            TaczFixesMod.LOGGER.warn("Forced timed-out offhand bolt to finish for {} ({})", this.player.getGameProfile().getName(), this.boundGunId);
            return true;
        }

        private void syncPlayerGlobalState() {
            this.data.sprintTimeS = IGunOperator.fromLivingEntity(this.player).getDataHolder().sprintTimeS;
        }

        private void updateManualActionBoltReadyTransition() {
            boolean ready = isManualActionBoltReady();
            if (!this.manualActionBoltReadyInitialized || ready != this.lastObservedManualActionBoltReady) {
                this.lastObservedManualActionBoltReady = ready;
                this.manualActionBoltReadyInitialized = true;
                this.stateDirty = true;
            }
        }

        private boolean isManualActionBoltReady() {
            return (!this.manualActionEpisodeActive || this.manualActionDelayedShotPending || this.manualActionBoltRequestConsumed || this.manualActionBoltRequestInProgress || this.data.isBolting || getShootCoolDown() != 0) ? false : true;
        }

        private void synchronizeShootBaseTimestamp() {
            long synchronizedBase = IGunOperator.fromLivingEntity(this.player).getDataHolder().baseTimestamp;
            long previousBase = this.data.baseTimestamp;
            if (previousBase == synchronizedBase) {
                return;
            }
            long relativeAdjustment = previousBase - synchronizedBase;
            if (this.hasShootTimestamp) {
                this.data.shootTimestamp += relativeAdjustment;
            }
            if (this.hasLastShootTimestamp) {
                this.data.lastShootTimestamp += relativeAdjustment;
            }
            this.data.baseTimestamp = synchronizedBase;
        }

        private ShootOutcome shoot(long timestamp, float chargeProgress) {
            synchronizeShootBaseTimestamp();
            if (this.manualActionEpisodeActive || this.data.isBolting) {
                synchronizeState(null, true, false, true, timestamp);
                return new ShootOutcome(ShootResult.IS_BOLTING, false, false);
            }
            ItemStack stack = this.player.getOffhandItem();
            IGun gun = IGun.getIGunOrNull(stack);
            boolean manualAction = isManualActionGun(stack, gun);
            if (manualAction) {
                advanceManualActionAsyncGeneration();
                this.manualActionSafeAsyncTaskRegisteredDuringShoot = false;
                this.manualActionDelayedAsyncTasksOutstanding = 0;
            }
            this.shootCaptureActive = true;
            this.bulletAttemptedDuringSynchronousShoot = false;
            this.bulletSpawnedDuringSynchronousShoot = false;
            this.manualActionShotCaptureActive = manualAction;
            this.manualActionChamberRefilledDuringShot = false;
            this.manualActionAmmoCountBeforeShot = gun == null ? 0 : gun.getCurrentAmmoCount(stack);
            OffhandShooterManager.pushActiveData(this.data);
            try {
                LivingEntityShoot livingEntityShoot = this.shoot;
                ServerPlayer serverPlayer = this.player;
                Objects.requireNonNull(serverPlayer);
                Supplier supplier = serverPlayer::getXRot;
                ServerPlayer serverPlayer2 = this.player;
                Objects.requireNonNull(serverPlayer2);
                ShootResult result = livingEntityShoot.shoot(supplier, serverPlayer2::getYRot, timestamp, chargeProgress);
                OffhandShooterManager.popActiveData();
                this.shootCaptureActive = false;
                this.manualActionShotCaptureActive = false;
                if (result == ShootResult.SUCCESS) {
                    this.hasLastShootTimestamp = this.hasShootTimestamp;
                    this.hasShootTimestamp = true;
                    if (manualAction && this.bulletAttemptedDuringSynchronousShoot && !this.bulletSpawnedDuringSynchronousShoot) {
                        resetManualActionEpisode();
                        advanceManualActionAsyncGeneration();
                    } else if (manualAction && this.manualActionSafeAsyncTaskRegisteredDuringShoot && this.manualActionDelayedAsyncTasksOutstanding > 0) {
                        beginManualActionDelayedShotReservation(timestamp, this.bulletAttemptedDuringSynchronousShoot, this.bulletSpawnedDuringSynchronousShoot);
                    } else if (manualAction && this.bulletSpawnedDuringSynchronousShoot) {
                        beginManualActionEpisodeAfterSuccessfulShot();
                    } else if (manualAction) {
                        resetManualActionEpisode();
                    }
                } else if (manualAction) {
                    resetManualActionEpisode();
                    advanceManualActionAsyncGeneration();
                }
                synchronizeState(null, true, false, true, timestamp);
                return new ShootOutcome(result, this.bulletAttemptedDuringSynchronousShoot, this.bulletSpawnedDuringSynchronousShoot);
            } catch (Throwable th) {
                OffhandShooterManager.popActiveData();
                this.shootCaptureActive = false;
                this.manualActionShotCaptureActive = false;
                throw th;
            }
        }

        private long getShootCoolDown() {
            return Math.max(this.shoot.getShootCoolDown(), 0L);
        }

        private long getMeleeCoolDown() {
            return Math.max(this.melee.getMeleeCoolDown(), 0L);
        }

        private boolean meleeBlocksMainHand() {
            long remaining = getMeleeCoolDown();
            if (remaining <= 0L) {
                return false;
            }
            long elapsed = Math.max(0L, System.currentTimeMillis() - this.data.meleeTimestamp);
            return OffhandShooterManager.meleeStillBlocking(remaining, remaining + elapsed);
        }

        private boolean reload(int requestId) {
            this.reloadRequestId = requestId;
            if (this.data.reloadStateType.isReloading()) {
                synchronizeState(null, true);
                return false;
            }
            long now = System.currentTimeMillis();
            if (this.lastReloadRequestTimestamp >= 0 && now >= this.lastReloadRequestTimestamp && now - this.lastReloadRequestTimestamp < OffhandShooterManager.CONTROL_REQUEST_INTERVAL_MS) {
                synchronizeState(null, true);
                return false;
            }
            this.lastReloadRequestTimestamp = now;
            ItemStack reloadStack = this.player.getOffhandItem();
            IGun reloadGun = IGun.getIGunOrNull(reloadStack);
            if (isManualActionGun(reloadStack, reloadGun)) {
                advanceManualActionAsyncGeneration();
                if (this.manualActionDelayedShotPending) {
                    resetManualActionEpisode();
                }
            }
            OffhandShooterManager.pushActiveData(this.data);
            try {
                this.reload.reload();
                OffhandShooterManager.popActiveData();
                if (this.data.reloadStateType.isReloading()) {
                    resetManualActionEpisode();
                }
                synchronizeState(null, true);
                return this.data.reloadStateType.isReloading();
            } catch (Throwable th) {
                OffhandShooterManager.popActiveData();
                throw th;
            }
        }

        private boolean cancelReload(int requestId) {
            long now = System.currentTimeMillis();
            if (this.lastCancelReloadRequestTimestamp >= 0 && now >= this.lastCancelReloadRequestTimestamp && now - this.lastCancelReloadRequestTimestamp < OffhandShooterManager.CONTROL_REQUEST_INTERVAL_MS) {
                return false;
            }
            this.lastCancelReloadRequestTimestamp = now;
            if (requestId != this.reloadRequestId || !this.data.reloadStateType.isReloading()) {
                synchronizeState(null, true);
                return false;
            }
            advanceManualActionAsyncGeneration();
            OffhandShooterManager.pushActiveData(this.data);
            try {
                this.reload.cancelReload();
                ReloadState reloadState = this.reload.tickReloadState();
                OffhandShooterManager.popActiveData();
                synchronizeState(reloadState, true);
                return true;
            } catch (Throwable th) {
                OffhandShooterManager.popActiveData();
                throw th;
            }
        }

        private boolean bolt(int requestId) {
            long now = System.currentTimeMillis();
            if (this.lastBoltRequestTimestamp >= 0 && now >= this.lastBoltRequestTimestamp && now - this.lastBoltRequestTimestamp < OffhandShooterManager.CONTROL_REQUEST_INTERVAL_MS) {
                return false;
            }
            this.lastBoltRequestTimestamp = now;
            if (!this.manualActionEpisodeActive || this.manualActionDelayedShotPending || this.manualActionBoltRequestConsumed || this.manualActionBoltRequestInProgress || getShootCoolDown() != 0) {
                synchronizeState(null, true, true, false, Long.MIN_VALUE);
                return false;
            }
            this.boltRequestId = requestId;
            this.manualActionBoltRequestInProgress = true;
            this.manualActionFailureTimestamp = -1L;
            ItemStack stack = this.player.getOffhandItem();
            IGun gun = IGun.getIGunOrNull(stack);
            boolean restoreSyntheticChamber = this.manualActionSyntheticCycle && gun != null && gun.hasBulletInBarrel(stack);
            if (restoreSyntheticChamber) {
                gun.setBulletInBarrel(stack, false);
            }
            long boltTimestampBefore = this.data.boltTimestamp;
            OffhandShooterManager.pushActiveData(this.data);
            try {
                this.bolt.bolt();
                OffhandShooterManager.popActiveData();
                if (restoreSyntheticChamber && gun != null && this.player.getOffhandItem() == stack) {
                    gun.setBulletInBarrel(stack, true);
                }
                this.manualActionBoltRequestInProgress = false;
                if (this.data.isBolting) {
                    this.manualActionBoltRequestConsumed = true;
                } else {
                    boolean boltStarted = this.data.boltTimestamp != boltTimestampBefore;
                    finishImmediateManualActionAttempt(boltStarted);
                }
                synchronizeState(null, true, true, false, Long.MIN_VALUE);
                return this.data.isBolting || this.data.boltTimestamp != boltTimestampBefore;
            } catch (Throwable th) {
                OffhandShooterManager.popActiveData();
                if (restoreSyntheticChamber && gun != null && this.player.getOffhandItem() == stack) {
                    gun.setBulletInBarrel(stack, true);
                }
                this.manualActionBoltRequestInProgress = false;
                throw th;
            }
        }

        private void beginManualActionDelayedShotReservation(long shootTimestamp, boolean synchronousAttempted, boolean synchronousSpawned) {
            ItemStack stack = this.player.getOffhandItem();
            IGun gun = IGun.getIGunOrNull(stack);
            if (!isManualActionGun(stack, gun)) {
                resetManualActionEpisode();
                return;
            }
            this.manualActionEpisodeActive = true;
            this.manualActionDelayedShotPending = true;
            this.manualActionDelayedShootTimestamp = shootTimestamp;
            this.manualActionDelayedBulletAttempted = synchronousAttempted;
            this.manualActionDelayedAttemptsInProgress = 0;
            this.manualActionDelayedAttemptCompleted = synchronousAttempted;
            this.manualActionDelayedBulletSpawned = synchronousSpawned;
            this.manualActionBoltRequestConsumed = false;
            this.manualActionBoltRequestInProgress = false;
            this.manualActionFailureTimestamp = -1L;
            this.manualActionSyntheticCycle = false;
            this.manualActionShotCaptureActive = true;
        }

        private void beginManualActionEpisodeAfterSuccessfulShot() {
            ItemStack stack = this.player.getOffhandItem();
            IGun gun = IGun.getIGunOrNull(stack);
            if (!isManualActionGun(stack, gun)) {
                resetManualActionEpisode();
                return;
            }
            boolean chamberLoaded = gun.hasBulletInBarrel(stack);
            boolean magazineAdvanced = gun.getCurrentAmmoCount(stack) < this.manualActionAmmoCountBeforeShot;
            if (chamberLoaded && (this.manualActionChamberRefilledDuringShot || magazineAdvanced)) {
                resetManualActionEpisode();
                return;
            }
            if (!chamberLoaded && !hasFeedAmmo(stack, gun)) {
                resetManualActionEpisode();
                return;
            }
            this.manualActionEpisodeActive = true;
            this.manualActionBoltRequestConsumed = false;
            this.manualActionFailureTimestamp = -1L;
            this.manualActionSyntheticCycle = chamberLoaded;
        }

        private void recordManualActionChamberRefill(ItemStack itemStack) {
            if (this.manualActionShotCaptureActive && itemStack == this.player.getOffhandItem()) {
                this.manualActionChamberRefilledDuringShot = true;
            }
        }

        private void recordBulletAttempt(ItemStack itemStack) {
            if (itemStack != this.player.getOffhandItem()) {
                return;
            }
            if (this.shootCaptureActive) {
                this.bulletAttemptedDuringSynchronousShoot = true;
            } else if (this.manualActionDelayedShotPending) {
                this.manualActionDelayedBulletAttempted = true;
                this.manualActionDelayedAttemptsInProgress++;
                this.manualActionDelayedAttemptCompleted = false;
            }
        }

        private void finishBulletAttempt(ItemStack itemStack) {
            if (!this.manualActionDelayedShotPending || itemStack != this.player.getOffhandItem() || this.manualActionDelayedAttemptsInProgress <= 0) {
                return;
            }
            this.manualActionDelayedAttemptsInProgress--;
            if (this.manualActionDelayedAttemptsInProgress == 0 && this.manualActionDelayedBulletAttempted) {
                this.manualActionDelayedAttemptCompleted = true;
                this.stateDirty = true;
            }
        }

        private void recordBulletSpawn(ItemStack itemStack) {
            if (itemStack != this.player.getOffhandItem()) {
                return;
            }
            if (this.shootCaptureActive) {
                this.bulletSpawnedDuringSynchronousShoot = true;
            } else if (this.manualActionDelayedShotPending && this.manualActionDelayedAttemptsInProgress > 0) {
                this.manualActionDelayedBulletSpawned = true;
            }
        }

        private void beginInitialManualActionEpisodeIfNeeded(ItemStack stack, IGun gun) {
            if (!this.manualActionEpisodeActive && !this.data.isBolting && !this.data.reloadStateType.isReloading() && isManualActionGun(stack, gun) && !gun.hasBulletInBarrel(stack) && hasFeedAmmo(stack, gun)) {
                this.manualActionEpisodeActive = true;
            }
        }

        private void updateManualActionEpisodeAfterTick(boolean wasBolting) {
            if (!this.manualActionEpisodeActive) {
                return;
            }
            ItemStack stack = this.player.getOffhandItem();
            IGun gun = IGun.getIGunOrNull(stack);
            if (!isManualActionGun(stack, gun) || this.data.reloadStateType.isReloading()) {
                resetManualActionEpisode();
                return;
            }
            if (this.manualActionDelayedShotPending) {
                finishManualActionDelayedShotIfReady();
                return;
            }
            if (this.data.isBolting) {
                return;
            }
            if (gun.hasBulletInBarrel(stack)) {
                if (this.manualActionSyntheticCycle && !wasBolting) {
                    return;
                }
                resetManualActionEpisode();
                return;
            }
            if (!hasFeedAmmo(stack, gun)) {
                resetManualActionEpisode();
                return;
            }
            if (wasBolting && this.manualActionFailureTimestamp < 0) {
                this.manualActionFailureTimestamp = System.currentTimeMillis();
            }
            if (this.manualActionBoltRequestConsumed && this.manualActionFailureTimestamp >= 0 && System.currentTimeMillis() - this.manualActionFailureTimestamp >= OffhandShooterManager.MANUAL_ACTION_RETRY_TIMEOUT_MS) {
                this.manualActionBoltRequestConsumed = false;
                this.manualActionFailureTimestamp = -1L;
                this.stateDirty = true;
            }
        }

        private void finishManualActionDelayedShotIfReady() {
            boolean attemptedShotFinished = this.manualActionDelayedBulletAttempted && this.manualActionDelayedAttemptCompleted && this.manualActionDelayedAttemptsInProgress == 0;
            boolean allAsyncTasksFinishedWithoutAttempt = !this.manualActionDelayedBulletAttempted && this.manualActionDelayedAsyncTasksOutstanding == 0;
            if (!this.manualActionDelayedShotPending || this.manualActionDelayedAsyncTasksOutstanding > 0) {
                return;
            }
            if (!attemptedShotFinished && !allAsyncTasksFinishedWithoutAttempt) {
                return;
            }
            long delayedShootTimestamp = this.manualActionDelayedShootTimestamp;
            boolean bulletSpawned = this.manualActionDelayedBulletSpawned;
            clearManualActionDelayedShotReservation();
            if (bulletSpawned) {
                beginManualActionEpisodeAfterSuccessfulShot();
                this.stateDirty = true;
                synchronizeState(null, true, false, true, delayedShootTimestamp);
                return;
            }
            UUID rejectedStackId = this.boundStackId;
            resetManualActionEpisode();
            advanceManualActionAsyncGeneration();
            this.stateDirty = true;
            if (rejectedStackId != null) {
                synchronizeState(null, true, false, true, delayedShootTimestamp);
                OffhandShooterManager.sendActionResult(this.player, 0, rejectedStackId, 0, delayedShootTimestamp, false, true, -1, 0L);
            }
        }

        private void finishImmediateManualActionAttempt(boolean boltStarted) {
            ItemStack stack = this.player.getOffhandItem();
            IGun gun = IGun.getIGunOrNull(stack);
            if (this.manualActionSyntheticCycle) {
                if (boltStarted) {
                    resetManualActionEpisode();
                    return;
                } else {
                    this.manualActionBoltRequestConsumed = false;
                    this.manualActionFailureTimestamp = -1L;
                    return;
                }
            }
            if (!isManualActionGun(stack, gun) || gun.hasBulletInBarrel(stack) || !hasFeedAmmo(stack, gun)) {
                resetManualActionEpisode();
            } else {
                this.manualActionBoltRequestConsumed = false;
                this.manualActionFailureTimestamp = -1L;
            }
        }

        private boolean isManualActionGun(ItemStack stack, IGun gun) {
            if (gun == null) {
                return false;
            }
            return ((Boolean) TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).map(index -> {
                return Boolean.valueOf(index.getGunData().getBolt() == Bolt.MANUAL_ACTION);
            }).orElse(false)).booleanValue();
        }

        private boolean hasFeedAmmo(ItemStack stack, IGun gun) {
            if (gun == null) {
                return false;
            }
            if (gun.useInventoryAmmo(stack)) {
                return gun.hasInventoryAmmo(this.player, stack, IGunOperator.fromLivingEntity(this.player).needCheckAmmo());
            }
            return gun.getCurrentAmmoCount(stack) > 0;
        }

        private long captureManualAsyncTaskGeneration(ItemStack itemStack) {
            IGun gun = IGun.getIGunOrNull(itemStack);
            if (itemStack != this.player.getOffhandItem() || !isManualActionGun(itemStack, gun)) {
                return Long.MIN_VALUE;
            }
            return this.manualActionAsyncGeneration;
        }

        private void recordManualSafeAsyncTask(ItemStack itemStack, long generation) {
            if (generation != this.manualActionAsyncGeneration || itemStack != this.player.getOffhandItem()) {
                return;
            }
            if (this.shootCaptureActive) {
                this.manualActionSafeAsyncTaskRegisteredDuringShoot = true;
                this.manualActionDelayedAsyncTasksOutstanding++;
            } else if (this.manualActionDelayedShotPending) {
                this.manualActionDelayedAsyncTasksOutstanding++;
            }
        }

        private void finishManualSafeAsyncTask(ItemStack itemStack, long generation) {
            if (generation != this.manualActionAsyncGeneration || this.manualActionDelayedAsyncTasksOutstanding <= 0) {
                return;
            }
            this.manualActionDelayedAsyncTasksOutstanding--;
            if (this.manualActionDelayedShotPending && this.manualActionDelayedAsyncTasksOutstanding == 0) {
                if (this.manualActionDelayedBulletAttempted && this.manualActionDelayedAttemptsInProgress > 0) {
                    this.manualActionDelayedAttemptsInProgress = 0;
                    this.manualActionDelayedAttemptCompleted = true;
                }
                this.stateDirty = true;
            }
        }

        private boolean isManualAsyncTaskGenerationCurrent(ItemStack itemStack, long generation) {
            IGun gun = IGun.getIGunOrNull(itemStack);
            UUID liveStackId = DualWieldStackId.get(itemStack);
            return generation == this.manualActionAsyncGeneration && itemStack == this.player.getOffhandItem() && this.data.currentGunItem != null && this.data.currentGunItem.get() == itemStack && this.boundStackId != null && this.boundStackId.equals(liveStackId) && this.boundGunId != null && gun != null && this.boundGunId.equals(gun.getGunId(itemStack)) && isManualActionGun(itemStack, gun);
        }

        private void advanceManualActionAsyncGeneration() {
            long j;
            if (this.manualActionAsyncGeneration == Long.MAX_VALUE) {
                j = 0;
            } else {
                j = this.manualActionAsyncGeneration + 1;
            }
            this.manualActionAsyncGeneration = j;
        }

        private void clearManualActionDelayedShotReservation() {
            this.manualActionDelayedShotPending = false;
            this.manualActionDelayedShootTimestamp = Long.MIN_VALUE;
            this.manualActionDelayedBulletAttempted = false;
            this.manualActionDelayedAttemptsInProgress = 0;
            this.manualActionDelayedAttemptCompleted = false;
            this.manualActionDelayedBulletSpawned = false;
            this.manualActionSafeAsyncTaskRegisteredDuringShoot = false;
            this.manualActionDelayedAsyncTasksOutstanding = 0;
            this.manualActionShotCaptureActive = false;
        }

        private void resetManualActionEpisode() {
            this.manualActionEpisodeActive = false;
            clearManualActionDelayedShotReservation();
            this.manualActionBoltRequestConsumed = false;
            this.manualActionBoltRequestInProgress = false;
            this.manualActionFailureTimestamp = -1L;
            this.manualActionSyntheticCycle = false;
            this.manualActionShotCaptureActive = false;
            this.manualActionChamberRefilledDuringShot = false;
            this.manualActionAmmoCountBeforeShot = 0;
        }

        private void synchronizeState(ReloadState reloadState, boolean force) {
            synchronizeState(reloadState, force, false, false, Long.MIN_VALUE);
        }

        private void synchronizeState(ReloadState reloadState, boolean force, boolean boltRequestAcknowledged, boolean shootRequestAcknowledged, long acknowledgedShootTimestamp) {
            long jMax;
            if (this.boundStackId == null || this.player.connection == null) {
                return;
            }
            int stateType = this.data.reloadStateType.ordinal();
            boolean bolting = this.data.isBolting;
            boolean manualActionBoltReady = isManualActionBoltReady();
            boolean active = this.data.reloadStateType.isReloading() || bolting;
            long now = System.currentTimeMillis();
            boolean heartbeatDue = this.lastStatePacketTimestamp < 0 || now - this.lastStatePacketTimestamp >= 1000;
            if (!force && !active && stateType == this.lastSentReloadStateType && bolting == this.lastSentBolting && !heartbeatDue) {
                return;
            }
            if (this.data.reloadTimestamp < 0) {
                jMax = -1;
            } else {
                jMax = Math.max(now - this.data.reloadTimestamp, 0L);
            }
            long elapsedMillis = jMax;
            long countDownMillis = reloadState == null ? -1L : reloadState.getCountDown();
            com.ssscript.taczfixes.common.network.NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> {
                return this.player;
            }), new ServerMessageOffhandState(this.boundStackId, this.reloadRequestId, stateType, elapsedMillis, countDownMillis, bolting, this.boltRequestId, boltRequestAcknowledged, this.manualActionEpisodeActive, manualActionBoltReady, shootRequestAcknowledged, acknowledgedShootTimestamp));
            this.lastObservedManualActionBoltReady = manualActionBoltReady;
            this.manualActionBoltReadyInitialized = true;
            this.lastSentReloadStateType = stateType;
            this.lastSentBolting = bolting;
            this.lastStatePacketTimestamp = now;
        }

        private boolean fireSelect() {
            long now = System.currentTimeMillis();
            if (this.lastFireSelectRequestTimestamp >= 0 && now >= this.lastFireSelectRequestTimestamp && now - this.lastFireSelectRequestTimestamp < OffhandShooterManager.FIRE_SELECT_REQUEST_INTERVAL_MS) {
                return false;
            }
            this.lastFireSelectRequestTimestamp = now;
            if (this.data.reloadStateType.isReloading() || this.data.isBolting || this.draw.getDrawCoolDown() > 0) {
                return false;
            }
            ItemStack stack = this.player.getOffhandItem();
            Item abstractGunItemM_41720_ = stack.getItem();
            if (!(abstractGunItemM_41720_ instanceof AbstractGunItem)) {
                return false;
            }
            AbstractGunItem gunItem = (AbstractGunItem) abstractGunItemM_41720_;
            OffhandShooterManager.pushActiveData(this.data);
            try {
                GunFireSelectEvent event = new GunFireSelectEvent(this.player, stack, LogicalSide.SERVER);
                if (!MinecraftForge.EVENT_BUS.post(event)) {
                    NetworkHandler.sendToTrackingEntity(new ServerMessageGunFireSelect(this.player.getId(), stack), this.player);
                    gunItem.fireSelect(this.data, stack);
                    rebuildCache();
                    OffhandShooterManager.popActiveData();
                    return true;
                }
                OffhandShooterManager.popActiveData();
                return false;
            } catch (Throwable th) {
                OffhandShooterManager.popActiveData();
                throw th;
            }
        }

        private void rebuildCache() {
            OffhandShooterManager.pushActiveData(this.data);
            try {
                ItemStack stack = this.player.getOffhandItem();
                IGun gun = IGun.getIGunOrNull(stack);
                if (gun == null) {
                    this.data.cacheProperty = null;
                } else if (TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).isEmpty()) {
                    this.data.cacheProperty = null;
                } else {
                    this.data.cacheProperty = OffhandGunPropertyResolver.rebuild(this.player, stack, gun, this.data);
                }
            } finally {
                OffhandShooterManager.popActiveData();
            }
        }
    }
}
