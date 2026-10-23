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
import com.tacz.guns.resource.pojo.data.gun.GunData;
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


final class OffhandShooter {
        final ServerPlayer player;
        final LivingEntityDrawGun draw;
        final LivingEntityShoot shoot;
        final LivingEntityReload reload;
        final LivingEntityBolt bolt;
        final LivingEntityMelee melee;
        ResourceLocation boundGunId;
        UUID boundStackId;
        int reloadRequestId;
        int boltRequestId;
        boolean lastSentBolting;
        boolean lastObservedManualActionBoltReady;
        boolean manualActionBoltReadyInitialized;
        boolean hasShootTimestamp;
        boolean hasLastShootTimestamp;
        boolean manualActionEpisodeActive;
        boolean manualActionDelayedShotPending;
        boolean manualActionDelayedBulletAttempted;
        int manualActionDelayedAttemptsInProgress;
        boolean manualActionDelayedAttemptCompleted;
        boolean manualActionDelayedBulletSpawned;
        boolean manualActionSafeAsyncTaskRegisteredDuringShoot;
        int manualActionDelayedAsyncTasksOutstanding;
        long manualActionAsyncGeneration;
        boolean manualActionBoltRequestConsumed;
        boolean manualActionBoltRequestInProgress;
        boolean shootCaptureActive;
        boolean bulletAttemptedDuringSynchronousShoot;
        boolean bulletSpawnedDuringSynchronousShoot;
        boolean manualActionShotCaptureActive;
        boolean manualActionChamberRefilledDuringShot;
        int manualActionAmmoCountBeforeShot;
        boolean manualActionSyntheticCycle;
        final ShooterDataHolder data = new ShooterDataHolder();
        int lastSentReloadStateType = -1;
        long lastStatePacketTimestamp = -1;
        boolean stateDirty = true;
        long manualActionDelayedShootTimestamp = Long.MIN_VALUE;
        long manualActionFailureTimestamp = -1;
        long serverBoltStartTimestamp = -1;
        long lastReloadRequestTimestamp = -1;
        long lastCancelReloadRequestTimestamp = -1;
        long lastBoltRequestTimestamp = -1;
        long lastFireSelectRequestTimestamp = -1;
        final ItemStack[] boundAttachments = new ItemStack[AttachmentType.values().length];
        ItemStack boundStack = ItemStack.EMPTY;

        OffhandShooter(ServerPlayer player) {
            this.player = player;
            this.draw = new LivingEntityDrawGun(player, this.data);
            this.shoot = new LivingEntityShoot(player, this.data, this.draw);
            this.reload = new LivingEntityReload(player, this.data, this.draw, this.shoot);
            this.bolt = new LivingEntityBolt(this.data, player, this.draw, this.shoot);
            this.melee = new LivingEntityMelee(player, this.data, this.draw);
        }

        void melee() {
            OffhandShooterManager.pushActiveData(this.data);
            try {
                this.melee.melee();
            } finally {
                OffhandShooterManager.popActiveData();
            }
        }

        void bindIfChanged() {
            ItemStack mainStack = this.player.getMainHandItem();
            UUID mainStackId = null;
            if (IGun.getIGunOrNull(mainStack) != null) {
                mainStackId = DualWieldStackId.getOrCreate(mainStack);
            }
            ItemStack stack = this.player.getOffhandItem();
            IGun gun = IGun.getIGunOrNull(stack);
            ResourceLocation gunId = gun == null ? null : gun.getGunId(stack);
            UUID existingId = gunId == null ? null : DualWieldStackId.get(stack);
            UUID stackId = existingId != null ? existingId
                    : (gunId == null ? null : DualWieldStackId.getOrCreate(stack));
            if (gunId == null || stackId == null || gun == null) {
                return;
            }
            // 只有新 ID 或绑定目标变化时才扫描背包查重复, 避免每 tick / 每次副手动作全背包扫描。
            boolean rebind = existingId == null || this.boundStackId == null
                    || !stackId.equals(this.boundStackId) || !gunId.equals(this.boundGunId);
            if (rebind) {
                boolean duplicatesMainId = (stack == mainStack || mainStackId == null || !mainStackId.equals(stackId)) ? false : true;
                boolean duplicatesInventoryId = hasDuplicateInventoryStackId(stack, stackId);
                if (duplicatesMainId || duplicatesInventoryId) {
                    stackId = DualWieldStackId.regenerate(stack);
                }
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
                shooterDataHolder.setCurrentGunItem(serverPlayer::getOffhandItem);
                long now = GunTime.nowMillis();
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

        boolean hasDuplicateInventoryStackId(ItemStack currentStack, UUID currentStackId) {
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

        boolean hasSameStackIdOnAnotherStack(ItemStack currentStack, UUID currentStackId, ItemStack candidate) {
            return (candidate == currentStack || candidate == null || candidate.isEmpty() || !currentStackId.equals(DualWieldStackId.get(candidate))) ? false : true;
        }

        boolean matchesBoundStack(UUID expectedStackId) {
            return expectedStackId != null && IGun.getIGunOrNull(this.player.getOffhandItem()) != null;
        }

        void interruptReloadBeforeRemoval() {
            if (!this.data.reloadStateType.isReloading() || this.boundStack == null || this.boundStack.isEmpty()) {
                return;
            }
            ItemStack interruptedStack = this.boundStack;
            Supplier<ItemStack> currentSupplier = this.data.currentGunItem;
            this.data.setCurrentGunItem(() -> {
                return interruptedStack;
            });
            OffhandShooterManager.pushActiveData(this.data);
            try {
                try {
                    this.reload.cancelReload();
                    this.reload.tickReloadState();
                    OffhandShooterManager.popActiveData();
                    this.data.setCurrentGunItem(currentSupplier);
                } catch (RuntimeException exception) {
                    TaczFixesMod.LOGGER.error("Failed to interrupt offhand reload for {}", this.boundGunId, exception);
                    OffhandShooterManager.popActiveData();
                    this.data.setCurrentGunItem(currentSupplier);
                }
            } catch (Throwable th) {
                OffhandShooterManager.popActiveData();
                this.data.setCurrentGunItem(currentSupplier);
                throw th;
            }
        }

        boolean updateAttachmentSignature(IGun gun, ItemStack stack) {
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

        void tick() {
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
                updateSprintState(gun, stack);
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

        boolean enforceBoltTimeout(boolean wasBolting) {
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

        void syncPlayerGlobalState() {
        }

        void updateSprintState(IGun gun, ItemStack stack) {
            GunData gunData = gun == null ? null
                    : TimelessAPI.getCommonGunIndex(gun.getGunId(stack))
                            .map(index -> index.getGunData()).orElse(null);
            long now = GunTime.nowMillis();
            if (gunData == null) {
                this.data.sprintTimeS = 0.0f;
                this.data.sprintTimestamp = now;
                return;
            }
            if (this.data.sprintTimestamp < 0L || now < this.data.sprintTimestamp) {
                this.data.sprintTimestamp = now;
            }
            float elapsed = (now - this.data.sprintTimestamp) / 1000.0f;
            this.data.sprintTimestamp = now;
            float maxSprintTime = Math.max(0.0f, gunData.getSprintTime());
            if (this.player.isSprinting() && !this.player.isVisuallySwimming()) {
                this.data.sprintTimeS = Math.min(maxSprintTime, this.data.sprintTimeS + elapsed);
            } else {
                this.data.sprintTimeS = Math.max(0.0f, this.data.sprintTimeS - elapsed);
            }
        }

        void updateManualActionBoltReadyTransition() {
            boolean ready = isManualActionBoltReady();
            if (!this.manualActionBoltReadyInitialized || ready != this.lastObservedManualActionBoltReady) {
                this.lastObservedManualActionBoltReady = ready;
                this.manualActionBoltReadyInitialized = true;
                this.stateDirty = true;
            }
        }

        boolean isManualActionBoltReady() {
            return (!this.manualActionEpisodeActive || this.manualActionDelayedShotPending || this.manualActionBoltRequestConsumed || this.manualActionBoltRequestInProgress || this.data.isBolting || getShootCoolDown() != 0) ? false : true;
        }

        void synchronizeShootBaseTimestamp() {
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

        OffhandShooterManager.ShootOutcome shoot(long timestamp, float chargeProgress) {
            synchronizeShootBaseTimestamp();
            if (this.manualActionEpisodeActive || this.data.isBolting) {
                synchronizeState(null, true, false, true, timestamp);
                return new OffhandShooterManager.ShootOutcome(ShootResult.IS_BOLTING, false, false);
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
                return new OffhandShooterManager.ShootOutcome(result, this.bulletAttemptedDuringSynchronousShoot, this.bulletSpawnedDuringSynchronousShoot);
            } catch (Throwable th) {
                OffhandShooterManager.popActiveData();
                this.shootCaptureActive = false;
                this.manualActionShotCaptureActive = false;
                throw th;
            }
        }

        long getShootCoolDown() {
            return Math.max(this.shoot.getShootCoolDown(), 0L);
        }

        long getMeleeCoolDown() {
            return Math.max(this.melee.getMeleeCoolDown(), 0L);
        }

        boolean meleeBlocksMainHand() {
            long remaining = getMeleeCoolDown();
            if (remaining <= 0L) {
                return false;
            }
            long elapsed = Math.max(0L, GunTime.nowMillis() - this.data.meleeTimestamp);
            return OffhandShooterManager.meleeStillBlocking(remaining, remaining + elapsed);
        }

        boolean reload(int requestId) {
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

        boolean cancelReload(int requestId) {
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

        boolean bolt(int requestId) {
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

        void beginManualActionDelayedShotReservation(long shootTimestamp, boolean synchronousAttempted, boolean synchronousSpawned) {
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

        void beginManualActionEpisodeAfterSuccessfulShot() {
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

        void recordManualActionChamberRefill(ItemStack itemStack) {
            if (this.manualActionShotCaptureActive && itemStack == this.player.getOffhandItem()) {
                this.manualActionChamberRefilledDuringShot = true;
            }
        }

        void recordBulletAttempt(ItemStack itemStack) {
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

        void finishBulletAttempt(ItemStack itemStack) {
            if (!this.manualActionDelayedShotPending || itemStack != this.player.getOffhandItem() || this.manualActionDelayedAttemptsInProgress <= 0) {
                return;
            }
            this.manualActionDelayedAttemptsInProgress--;
            if (this.manualActionDelayedAttemptsInProgress == 0 && this.manualActionDelayedBulletAttempted) {
                this.manualActionDelayedAttemptCompleted = true;
                this.stateDirty = true;
            }
        }

        void recordBulletSpawn(ItemStack itemStack) {
            if (itemStack != this.player.getOffhandItem()) {
                return;
            }
            if (this.shootCaptureActive) {
                this.bulletSpawnedDuringSynchronousShoot = true;
            } else if (this.manualActionDelayedShotPending && this.manualActionDelayedAttemptsInProgress > 0) {
                this.manualActionDelayedBulletSpawned = true;
            }
        }

        void beginInitialManualActionEpisodeIfNeeded(ItemStack stack, IGun gun) {
            if (!this.manualActionEpisodeActive && !this.data.isBolting && !this.data.reloadStateType.isReloading() && isManualActionGun(stack, gun) && !gun.hasBulletInBarrel(stack) && hasFeedAmmo(stack, gun)) {
                this.manualActionEpisodeActive = true;
            }
        }

        void updateManualActionEpisodeAfterTick(boolean wasBolting) {
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

        void finishManualActionDelayedShotIfReady() {
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

        void finishImmediateManualActionAttempt(boolean boltStarted) {
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

        boolean isManualActionGun(ItemStack stack, IGun gun) {
            if (gun == null) {
                return false;
            }
            return ((Boolean) TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).map(index -> {
                return Boolean.valueOf(index.getGunData().getBolt() == Bolt.MANUAL_ACTION);
            }).orElse(false)).booleanValue();
        }

        boolean hasFeedAmmo(ItemStack stack, IGun gun) {
            if (gun == null) {
                return false;
            }
            if (gun.useInventoryAmmo(stack)) {
                return gun.hasInventoryAmmo(this.player, stack, IGunOperator.fromLivingEntity(this.player).needCheckAmmo());
            }
            return gun.getCurrentAmmoCount(stack) > 0;
        }

        long captureManualAsyncTaskGeneration(ItemStack itemStack) {
            IGun gun = IGun.getIGunOrNull(itemStack);
            if (itemStack != this.player.getOffhandItem() || !isManualActionGun(itemStack, gun)) {
                return Long.MIN_VALUE;
            }
            return this.manualActionAsyncGeneration;
        }

        void recordManualSafeAsyncTask(ItemStack itemStack, long generation) {
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

        void finishManualSafeAsyncTask(ItemStack itemStack, long generation) {
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

        boolean isManualAsyncTaskGenerationCurrent(ItemStack itemStack, long generation) {
            IGun gun = IGun.getIGunOrNull(itemStack);
            UUID liveStackId = DualWieldStackId.get(itemStack);
            return generation == this.manualActionAsyncGeneration && itemStack == this.player.getOffhandItem() && this.data.currentGunItem != null && this.data.currentGunItem.get() == itemStack && this.boundStackId != null && this.boundStackId.equals(liveStackId) && this.boundGunId != null && gun != null && this.boundGunId.equals(gun.getGunId(itemStack)) && isManualActionGun(itemStack, gun);
        }

        void advanceManualActionAsyncGeneration() {
            long j;
            if (this.manualActionAsyncGeneration == Long.MAX_VALUE) {
                j = 0;
            } else {
                j = this.manualActionAsyncGeneration + 1;
            }
            this.manualActionAsyncGeneration = j;
        }

        void clearManualActionDelayedShotReservation() {
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

        void resetManualActionEpisode() {
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

        void synchronizeState(ReloadState reloadState, boolean force) {
            synchronizeState(reloadState, force, false, false, Long.MIN_VALUE);
        }

        void synchronizeState(ReloadState reloadState, boolean force, boolean boltRequestAcknowledged, boolean shootRequestAcknowledged, long acknowledgedShootTimestamp) {
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

        boolean fireSelect() {
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

        void rebuildCache() {
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
