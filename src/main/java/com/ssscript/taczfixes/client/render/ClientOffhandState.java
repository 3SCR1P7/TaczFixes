package com.ssscript.taczfixes.client.render;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.ChargeData;
import com.tacz.guns.resource.pojo.data.gun.ChargeType;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.ssscript.taczfixes.common.util.DualWieldBalance;
import com.ssscript.taczfixes.common.util.DualWieldStackId;
import com.ssscript.taczfixes.common.util.OffhandGunPropertyResolver;
import com.ssscript.taczfixes.common.util.PausableClock;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.runtime.ObjectMethods;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/render/ClientOffhandState.class */
public final class ClientOffhandState {
    private static final long RELOAD_REQUEST_TIMEOUT_MS = 5000;
    private static final long BOLT_REQUEST_TIMEOUT_MS = 5000;
    private static final long BOLT_ACTIVE_TIMEOUT_MS = 10000;
    private static final long BOLT_NBT_GRACE_MS = 500;
    private static final long MANUAL_SHOT_CHAMBER_SYNC_TIMEOUT_MS = 5000;
    private static final long FIRE_SELECT_REQUEST_TIMEOUT_MS = 5000;
    private static final long SHOOT_REQUEST_TIMEOUT_MS = 30000;
    private static final long SHOOT_TRANSITION_SERVER_WAIT_MS = 250;
    private static final int MAX_PENDING_SHOOT_REQUESTS = 256;
    private static final long MIN_MANUAL_ACTION_BOLT_LOCK_MS = 250;
    private boolean shootTransitionLocked;
    private UUID shootTransitionStackId;
    private boolean shootTransitionServerCoolDownObserved;
    private int reloadRequestId;
    private boolean reloadRequestPending;
    private boolean reloadCancelPending;
    private UUID reloadStackId;
    private boolean reloadAuthoritative;
    private boolean bolting;
    private boolean boltAwaitingServer;
    private int boltRequestId;
    private UUID boltStackId;
    private boolean boltCycleConsumed;
    private boolean serverBoltActiveSeen;
    private boolean serverManualActionBoltReady;
    private boolean manualShotAwaitingChamberSync;
    private boolean manualShotServerPendingConfirmed;
    private UUID manualShotStackId;
    private int fireSelectRequestId;
    private boolean fireSelectRequestPending;
    private UUID fireSelectStackId;
    private float chargeProgress;
    private boolean charging;
    private AttachmentCacheProperty attachmentCache;
    private ResourceLocation cachedGunId;
    private GunData cachedGunData;
    private FireMode cachedFireMode;
    private volatile long shootTimestamp = -1;
    private volatile long lastShootTimestamp = -1;
    private final LinkedHashMap<PendingShootRequestKey, Long> pendingShootRequests = new LinkedHashMap<>();
    private long shootTransitionTimestamp = Long.MIN_VALUE;
    private long shootTransitionStartTimestamp = -1;
    private long reloadStartTimestamp = -1;
    private long reloadEndTimestamp = -1;
    private long drawEndTimestamp = -1;
    private int reloadStateType = ReloadState.StateType.NOT_RELOADING.ordinal();
    private long boltStartTimestamp = -1;
    private long serverBoltCompletedTimestamp = -1;
    private long manualShotDispatchTimestamp = -1;
    private long manualShotExpectedServerTimestamp = Long.MIN_VALUE;
    private long fireSelectRequestTimestamp = -1;
    private final ShooterDataHolder propertyData = new ShooterDataHolder();
    private final ItemStack[] cachedAttachments = new ItemStack[AttachmentType.values().length];

    public ClientOffhandState() {
        this.propertyData.initialData();
    }

    public synchronized long recordShot() {
        this.lastShootTimestamp = this.shootTimestamp;
        this.shootTimestamp = System.currentTimeMillis();
        return this.shootTimestamp;
    }

    public synchronized void recordShootRequest(UUID stackId, long relativeShootTimestamp) {
        long now = System.currentTimeMillis();
        prunePendingShootRequests(now);
        PendingShootRequestKey key = new PendingShootRequestKey(stackId, relativeShootTimestamp);
        this.pendingShootRequests.remove(key);
        while (this.pendingShootRequests.size() >= MAX_PENDING_SHOOT_REQUESTS) {
            Iterator<Map.Entry<PendingShootRequestKey, Long>> iterator = this.pendingShootRequests.entrySet().iterator();
            if (!iterator.hasNext()) {
                break;
            }
            iterator.next();
            iterator.remove();
        }
        this.pendingShootRequests.put(key, Long.valueOf(now));
    }

    public synchronized void beginShootTransitionLock(UUID stackId, long relativeShootTimestamp) {
        this.shootTransitionLocked = true;
        this.shootTransitionStackId = stackId;
        this.shootTransitionTimestamp = relativeShootTimestamp;
        this.shootTransitionStartTimestamp = System.currentTimeMillis();
        this.shootTransitionServerCoolDownObserved = false;
    }

    public synchronized void discardShootRequest(UUID stackId, long relativeShootTimestamp) {
        this.pendingShootRequests.remove(new PendingShootRequestKey(stackId, relativeShootTimestamp));
        clearMatchingShootTransitionLock(stackId, relativeShootTimestamp);
    }

    public synchronized boolean isShootTransitionLocked(LocalPlayer player, ItemStack stack) {
        if (!this.shootTransitionLocked) {
            return false;
        }
        UUID liveStackId = DualWieldStackId.get(stack);
        if (this.shootTransitionStackId == null || !this.shootTransitionStackId.equals(liveStackId)) {
            clearShootTransitionLock();
            return false;
        }
        long now = System.currentTimeMillis();
        if (this.shootTransitionStartTimestamp < 0 || now < this.shootTransitionStartTimestamp) {
            this.shootTransitionStartTimestamp = now;
        }
        if (this.shootTransitionServerCoolDownObserved) {
            if (getShootCoolDown(player, stack) == 0) {
                clearShootTransitionLock();
                return false;
            }
            return true;
        }
        if (now - this.shootTransitionStartTimestamp >= 250) {
            clearShootTransitionLock();
            return false;
        }
        return true;
    }

    public long getLastShootTimestamp() {
        return this.lastShootTimestamp;
    }

    public synchronized void adjustShootTimestamp(long deltaMillis) {
        this.shootTimestamp += deltaMillis;
    }

    public long getShootInterval(LocalPlayer player, ItemStack stack) {
        GunData data;
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null || (data = (GunData) TimelessAPI.getClientGunIndex(gun.getGunId(stack)).map(index -> {
            return index.getGunData();
        }).orElse(null)) == null) {
            return 0L;
        }
        FireMode fireMode = gun.getFireMode(stack);
        AttachmentCacheProperty cache = getOrRebuildAttachmentCache(player, stack, gun, data, fireMode);
        long interval = OffhandGunPropertyResolver.getShootInterval(stack, gun, data, fireMode, cache);
        return Math.max(interval, 0L);
    }

    private AttachmentCacheProperty getOrRebuildAttachmentCache(LocalPlayer player, ItemStack stack, IGun gun, GunData data, FireMode fireMode) {
        ResourceLocation gunId = gun.getGunId(stack);
        boolean gunChanged = (gunId.equals(this.cachedGunId) && this.cachedGunData == data) ? false : true;
        if (!gunChanged && this.attachmentCache != null && fireMode == this.cachedFireMode && attachmentsMatch(stack, gun)) {
            return this.attachmentCache;
        }
        if (gunChanged) {
            this.propertyData.initialData();
        }
        this.propertyData.currentGunItem = () -> {
            return stack;
        };
        AttachmentCacheProperty cache = OffhandGunPropertyResolver.rebuild(player, stack, gun, this.propertyData);
        for (AttachmentType type : AttachmentType.values()) {
            if (type != AttachmentType.NONE) {
                this.cachedAttachments[type.ordinal()] = gun.getAttachment(stack, type).copy();
            }
        }
        this.cachedGunId = gunId;
        this.cachedGunData = data;
        this.cachedFireMode = fireMode;
        this.attachmentCache = cache;
        return cache;
    }

    AttachmentCacheProperty getAttachmentCacheForRecoil(LocalPlayer player, ItemStack stack, IGun gun, GunData data) {
        return getOrRebuildAttachmentCache(player, stack, gun, data, gun.getFireMode(stack));
    }

    private boolean attachmentsMatch(ItemStack stack, IGun gun) {
        ItemStack cachedAttachment;
        for (AttachmentType type : AttachmentType.values()) {
            if (type != AttachmentType.NONE && ((cachedAttachment = this.cachedAttachments[type.ordinal()]) == null || !ItemStack.matches(gun.getAttachment(stack, type), cachedAttachment))) {
                return false;
            }
        }
        return true;
    }

    public long getShootCoolDown(LocalPlayer player, ItemStack stack) {
        long interval = getShootInterval(player, stack);
        return Math.max(interval - (System.currentTimeMillis() - this.shootTimestamp), 0L);
    }

    public void beginReload(boolean isEmpty, float durationSeconds) {
        beginReload(ItemStack.EMPTY, null, isEmpty, durationSeconds);
    }

    public synchronized void beginReload(UUID stackId, boolean isEmpty, float durationSeconds) {
        beginReload(ItemStack.EMPTY, stackId, isEmpty, durationSeconds);
    }

    public synchronized void beginReload(ItemStack stack, UUID stackId, boolean isEmpty, float durationSeconds) {
        int iOrdinal;
        long j;
        resetCharge();
        clearManualShotAwaitingChamberSync();
        clearBolt(true);
        this.reloadRequestId = this.reloadRequestId == Integer.MAX_VALUE ? 1 : this.reloadRequestId + 1;
        this.reloadRequestPending = true;
        this.reloadCancelPending = false;
        this.reloadStackId = stackId;
        this.reloadAuthoritative = false;
        if (isEmpty) {
            iOrdinal = ReloadState.StateType.EMPTY_RELOAD_FEEDING.ordinal();
        } else {
            iOrdinal = ReloadState.StateType.TACTICAL_RELOAD_FEEDING.ordinal();
        }
        this.reloadStateType = iOrdinal;
        this.reloadStartTimestamp = PausableClock.millis();
        long durationMillis = getDualReloadDurationMillis(stack, durationSeconds);
        if (durationMillis > Long.MAX_VALUE - this.reloadStartTimestamp) {
            j = Long.MAX_VALUE;
        } else {
            j = this.reloadStartTimestamp + durationMillis;
        }
        this.reloadEndTimestamp = j;
    }

    private static long getDualReloadDurationMillis(ItemStack stack, float durationSeconds) {
        if (!Float.isFinite(durationSeconds) || durationSeconds <= 0.0f) {
            return 1L;
        }
        double timeScale = DualWieldBalance.getReloadTimeScale(stack);
        double durationMillis = durationSeconds * 1000.0d * timeScale;
        return (long) Math.min(Math.max(durationMillis, 1.0d), 9.223372036854776E18d);
    }

    public boolean isReloading() {
        long now = PausableClock.millis();
        return this.reloadRequestPending ? this.reloadStartTimestamp >= 0 && now - this.reloadStartTimestamp < 5000 : this.reloadAuthoritative ? this.reloadStateType >= 0 && this.reloadStateType < ReloadState.StateType.values().length && ReloadState.StateType.values()[this.reloadStateType].isReloading() : this.reloadEndTimestamp > now;
    }

    public int getReloadRequestId() {
        return this.reloadRequestId;
    }

    public synchronized int beginReloadCancel(UUID stackId) {
        if (isReloading() && !this.reloadCancelPending) {
            if (this.reloadStackId != null && !this.reloadStackId.equals(stackId)) {
                return 0;
            }
            this.reloadCancelPending = true;
            return this.reloadRequestId;
        }
        return 0;
    }

    public synchronized boolean isReloadCancelPending() {
        return this.reloadCancelPending;
    }

    public synchronized void applyServerState(int requestId, int serverReloadStateType, long reloadElapsedMillis, long reloadCountDownMillis, boolean serverBolting, int serverBoltRequestId, boolean boltRequestAcknowledged, boolean serverManualActionEpisodeActive, boolean serverManualActionBoltReady, boolean shootRequestAcknowledged, long acknowledgedShootTimestamp) {
        long now = System.currentTimeMillis();
        long reloadNow = PausableClock.millis();
        this.serverManualActionBoltReady = serverManualActionBoltReady;
        if (!this.reloadRequestPending || requestId == this.reloadRequestId) {
            this.reloadRequestId = requestId;
            this.reloadRequestPending = false;
            this.reloadCancelPending = false;
            this.reloadAuthoritative = true;
            if (serverReloadStateType >= 0 && serverReloadStateType < ReloadState.StateType.values().length) {
                this.reloadStateType = serverReloadStateType;
            } else {
                this.reloadStateType = ReloadState.StateType.NOT_RELOADING.ordinal();
            }
            if (ReloadState.StateType.values()[this.reloadStateType].isReloading()) {
                if (reloadElapsedMillis >= 0) {
                    this.reloadStartTimestamp = reloadNow - reloadElapsedMillis;
                }
                if (reloadCountDownMillis >= 0) {
                    this.reloadEndTimestamp = reloadNow + reloadCountDownMillis;
                }
            } else {
                this.reloadEndTimestamp = reloadNow;
            }
        }
        boolean matchingShootAcknowledgement = shootRequestAcknowledged && this.manualShotAwaitingChamberSync && acknowledgedShootTimestamp == this.manualShotExpectedServerTimestamp;
        if (matchingShootAcknowledgement) {
            if (serverManualActionEpisodeActive && !serverManualActionBoltReady) {
                this.manualShotServerPendingConfirmed = true;
            } else {
                clearManualShotAwaitingChamberSync();
            }
        } else if (this.manualShotAwaitingChamberSync && this.manualShotServerPendingConfirmed && (!serverManualActionEpisodeActive || serverManualActionBoltReady)) {
            clearManualShotAwaitingChamberSync();
        }
        boolean matchingBoltResponse = boltRequestAcknowledged && serverBoltRequestId == this.boltRequestId;
        if (this.boltAwaitingServer && serverBoltRequestId != this.boltRequestId) {
            if (now - this.boltStartTimestamp >= 5000) {
                clearBolt(true);
                return;
            }
            return;
        }
        if (serverBolting) {
            this.bolting = true;
            if (matchingBoltResponse || !this.boltAwaitingServer) {
                this.boltAwaitingServer = false;
            }
            this.boltCycleConsumed = true;
            this.serverBoltActiveSeen = true;
            this.serverBoltCompletedTimestamp = -1L;
            if (this.boltStartTimestamp < 0) {
                this.boltStartTimestamp = now;
                return;
            }
            return;
        }
        if (serverManualActionBoltReady && (this.boltAwaitingServer || this.serverBoltActiveSeen || this.boltCycleConsumed)) {
            clearBolt(true);
            return;
        }
        if (matchingBoltResponse && this.boltAwaitingServer) {
            if (serverManualActionEpisodeActive) {
                clearBolt(false);
                return;
            }
            this.bolting = true;
            this.boltAwaitingServer = false;
            this.boltCycleConsumed = true;
            this.serverBoltActiveSeen = false;
            this.serverBoltCompletedTimestamp = now;
            if (this.boltStartTimestamp < 0) {
                this.boltStartTimestamp = now;
                return;
            }
            return;
        }
        if (this.serverBoltActiveSeen) {
            if (this.serverBoltCompletedTimestamp < 0) {
                this.serverBoltCompletedTimestamp = now;
            }
        } else if (this.boltAwaitingServer && now - this.boltStartTimestamp >= 5000) {
            clearBolt(true);
        }
    }

    public synchronized void applyActionResult(int actionType, UUID requestedStackId, int requestId, long shootTimestamp, boolean accepted, boolean stackMatched, long authoritativeShootCoolDown) {
        if (actionType == 0) {
            prunePendingShootRequests(System.currentTimeMillis());
            PendingShootRequestKey shootRequestKey = new PendingShootRequestKey(requestedStackId, shootTimestamp);
            boolean matchingShootRequest = this.pendingShootRequests.containsKey(shootRequestKey);
            boolean matchingShot = this.manualShotAwaitingChamberSync && shootTimestamp == this.manualShotExpectedServerTimestamp && this.manualShotStackId != null && this.manualShotStackId.equals(requestedStackId);
            if (matchingShootRequest) {
                this.pendingShootRequests.remove(shootRequestKey);
            }
            boolean matchingTransitionLock = this.shootTransitionLocked && this.shootTransitionTimestamp == shootTimestamp && this.shootTransitionStackId != null && this.shootTransitionStackId.equals(requestedStackId);
            if (matchingTransitionLock) {
                if (!accepted || !stackMatched) {
                    clearShootTransitionLock();
                } else if (authoritativeShootCoolDown > 0) {
                    this.shootTransitionServerCoolDownObserved = true;
                }
            }
            if (matchingShot) {
                if (!accepted || !stackMatched) {
                    clearManualShotAwaitingChamberSync();
                    return;
                }
                return;
            }
            return;
        }
        if (actionType == 1) {
            if (requestId == this.reloadRequestId && matchesPendingStack(this.reloadStackId, requestedStackId) && !stackMatched) {
                clearReloadPrediction();
                return;
            }
            return;
        }
        if (actionType == 2) {
            if (requestId == this.boltRequestId && matchesPendingStack(this.boltStackId, requestedStackId)) {
                if (!accepted || !stackMatched) {
                    clearBolt(true);
                    return;
                }
                return;
            }
            return;
        }
        if (actionType == 3) {
            if (requestId == this.fireSelectRequestId && matchesPendingStack(this.fireSelectStackId, requestedStackId)) {
                this.fireSelectRequestPending = false;
                this.fireSelectRequestTimestamp = -1L;
                return;
            }
            return;
        }
        if (actionType == 4 && requestId == this.reloadRequestId && matchesPendingStack(this.reloadStackId, requestedStackId)) {
            if (!stackMatched) {
                clearReloadPrediction();
            } else if (!accepted) {
                this.reloadCancelPending = false;
            }
        }
    }

    private boolean matchesPendingStack(UUID pendingStackId, UUID requestedStackId) {
        return pendingStackId != null && pendingStackId.equals(requestedStackId);
    }

    private void prunePendingShootRequests(long now) {
        Iterator<Map.Entry<PendingShootRequestKey, Long>> iterator = this.pendingShootRequests.entrySet().iterator();
        while (iterator.hasNext()) {
            long createdTimestamp = iterator.next().getValue().longValue();
            if (createdTimestamp < 0 || now < createdTimestamp || now - createdTimestamp >= SHOOT_REQUEST_TIMEOUT_MS) {
                iterator.remove();
            }
        }
    }

    private void clearReloadPrediction() {
        this.reloadRequestPending = false;
        this.reloadCancelPending = false;
        this.reloadAuthoritative = true;
        this.reloadStateType = ReloadState.StateType.NOT_RELOADING.ordinal();
        this.reloadEndTimestamp = PausableClock.millis();
        this.reloadStackId = null;
    }

    private void clearMatchingShootTransitionLock(UUID stackId, long relativeShootTimestamp) {
        if (this.shootTransitionLocked && this.shootTransitionTimestamp == relativeShootTimestamp && this.shootTransitionStackId != null && this.shootTransitionStackId.equals(stackId)) {
            clearShootTransitionLock();
        }
    }

    private void clearShootTransitionLock() {
        this.shootTransitionLocked = false;
        this.shootTransitionStackId = null;
        this.shootTransitionTimestamp = Long.MIN_VALUE;
        this.shootTransitionStartTimestamp = -1L;
        this.shootTransitionServerCoolDownObserved = false;
    }

    public void beginBolt() {
        beginBolt(null);
    }

    public synchronized void beginBolt(UUID stackId) {
        resetCharge();
        clearManualShotAwaitingChamberSync();
        this.boltRequestId = this.boltRequestId == Integer.MAX_VALUE ? 1 : this.boltRequestId + 1;
        this.boltStackId = stackId;
        this.serverManualActionBoltReady = false;
        this.bolting = true;
        this.boltAwaitingServer = true;
        this.boltCycleConsumed = true;
        this.serverBoltActiveSeen = false;
        this.boltStartTimestamp = System.currentTimeMillis();
        this.serverBoltCompletedTimestamp = -1L;
    }

    public synchronized int getBoltRequestId() {
        return this.boltRequestId;
    }

    public synchronized int beginFireSelect(UUID stackId) {
        this.fireSelectRequestId = this.fireSelectRequestId == Integer.MAX_VALUE ? 1 : this.fireSelectRequestId + 1;
        this.fireSelectStackId = stackId;
        this.fireSelectRequestPending = true;
        this.fireSelectRequestTimestamp = System.currentTimeMillis();
        return this.fireSelectRequestId;
    }

    public synchronized int getFireSelectRequestId() {
        return this.fireSelectRequestId;
    }

    public synchronized boolean isFireSelectRequestPending() {
        long now = System.currentTimeMillis();
        if (this.fireSelectRequestPending && (this.fireSelectRequestTimestamp < 0 || now < this.fireSelectRequestTimestamp || now - this.fireSelectRequestTimestamp >= 5000)) {
            this.fireSelectRequestPending = false;
            this.fireSelectRequestTimestamp = -1L;
        }
        return this.fireSelectRequestPending;
    }

    public synchronized boolean isMatchingFireSelectResult(UUID stackId, int requestId) {
        isFireSelectRequestPending();
        return requestId == this.fireSelectRequestId && matchesPendingStack(this.fireSelectStackId, stackId);
    }

    public boolean isBolting() {
        return this.bolting;
    }

    public boolean canBeginBoltCycle() {
        return !this.boltCycleConsumed;
    }

    public boolean isServerManualActionBoltReady() {
        return this.serverManualActionBoltReady;
    }

    public synchronized void beginManualShotAwaitingChamberSync(UUID stackId, long relativeShootTimestamp) {
        this.manualShotAwaitingChamberSync = true;
        this.manualShotServerPendingConfirmed = false;
        this.manualShotDispatchTimestamp = System.currentTimeMillis();
        this.manualShotStackId = stackId;
        this.manualShotExpectedServerTimestamp = relativeShootTimestamp;
    }

    public synchronized void discardManualShotAwaitingChamberSync(UUID stackId, long relativeShootTimestamp) {
        if (this.manualShotAwaitingChamberSync && this.manualShotStackId != null && this.manualShotStackId.equals(stackId) && this.manualShotExpectedServerTimestamp == relativeShootTimestamp) {
            clearManualShotAwaitingChamberSync();
        }
    }

    public synchronized boolean isManualShotAwaitingChamberSync() {
        if (this.manualShotAwaitingChamberSync && !this.manualShotServerPendingConfirmed && isManualShotChamberSyncTimedOut()) {
            clearManualShotAwaitingChamberSync();
        }
        return this.manualShotAwaitingChamberSync;
    }

    public synchronized void tickManualShotChamberSync(ItemStack stack) {
        if (!this.manualShotAwaitingChamberSync) {
            return;
        }
        UUID liveStackId = DualWieldStackId.get(stack);
        IGun gun = IGun.getIGunOrNull(stack);
        boolean sameStack = this.manualShotStackId != null && this.manualShotStackId.equals(liveStackId);
        if (!sameStack || gun == null) {
            clearManualShotAwaitingChamberSync();
        } else {
            if (this.manualShotServerPendingConfirmed) {
                return;
            }
            if (!gun.hasBulletInBarrel(stack) || isManualShotChamberSyncTimedOut()) {
                clearManualShotAwaitingChamberSync();
            }
        }
    }

    private boolean isManualShotChamberSyncTimedOut() {
        return this.manualShotDispatchTimestamp < 0 || System.currentTimeMillis() - this.manualShotDispatchTimestamp >= 5000;
    }

    private synchronized void clearManualShotAwaitingChamberSync() {
        this.manualShotAwaitingChamberSync = false;
        this.manualShotServerPendingConfirmed = false;
        this.manualShotDispatchTimestamp = -1L;
        this.manualShotStackId = null;
        this.manualShotExpectedServerTimestamp = Long.MIN_VALUE;
    }

    public void tickBoltCompletion(LocalPlayer player, ItemStack stack) {
        boolean z;
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            clearBolt(true);
            return;
        }
        if (gun.hasBulletInBarrel(stack)) {
            boolean waitingForServerStart = this.boltAwaitingServer;
            boolean serverStillBolting = this.serverBoltActiveSeen && this.serverBoltCompletedTimestamp < 0;
            long now = System.currentTimeMillis();
            if (this.boltStartTimestamp > now) {
                this.boltStartTimestamp = now;
            }
            boolean minimumActionLockActive = this.boltStartTimestamp >= 0 && now - this.boltStartTimestamp < 250;
            if (waitingForServerStart || serverStillBolting || minimumActionLockActive) {
                return;
            }
            clearBolt(true);
            return;
        }
        boolean inventoryAmmo = gun.hasInventoryAmmo(player, stack, IGunOperator.fromLivingEntity(player).needCheckAmmo());
        if (gun.useInventoryAmmo(stack)) {
            z = !inventoryAmmo;
        } else {
            z = gun.getCurrentAmmoCount(stack) < 1;
        }
        boolean noAmmo = z;
        if (noAmmo) {
            clearBolt(true);
            return;
        }
        if (!this.bolting) {
            return;
        }
        long now2 = System.currentTimeMillis();
        if (this.boltAwaitingServer && now2 - this.boltStartTimestamp >= 5000) {
            clearBolt(true);
            return;
        }
        if (this.serverBoltCompletedTimestamp >= 0 && now2 - this.serverBoltCompletedTimestamp >= BOLT_NBT_GRACE_MS && this.boltStartTimestamp >= 0 && now2 - this.boltStartTimestamp >= BOLT_ACTIVE_TIMEOUT_MS) {
            clearBolt(true);
        } else if (this.serverBoltActiveSeen && this.boltStartTimestamp >= 0 && now2 - this.boltStartTimestamp >= BOLT_ACTIVE_TIMEOUT_MS) {
            clearBolt(true);
        }
    }

    private void clearBolt(boolean allowNextCycle) {
        this.bolting = false;
        this.boltAwaitingServer = false;
        this.serverBoltActiveSeen = false;
        this.boltStackId = null;
        this.boltStartTimestamp = -1L;
        this.serverBoltCompletedTimestamp = -1L;
        if (allowNextCycle) {
            this.boltCycleConsumed = false;
        }
    }

    public void beginDraw(float durationSeconds) {
        resetCharge();
        this.drawEndTimestamp = PausableClock.millis() + Math.max((long) (durationSeconds * 1000.0f), 0L);
    }

    public boolean isDrawing() {
        return this.drawEndTimestamp > PausableClock.millis();
    }

    public int getReloadStateType() {
        if (this.reloadAuthoritative) {
            return this.reloadStateType;
        }
        if (!isReloading()) {
            return ReloadState.StateType.NOT_RELOADING.ordinal();
        }
        return this.reloadStateType;
    }

    public float getReloadProgress() {
        if (this.reloadStartTimestamp < 0 || this.reloadEndTimestamp <= this.reloadStartTimestamp) {
            return -1.0f;
        }
        return Mth.clamp((PausableClock.millis() - this.reloadStartTimestamp) / (this.reloadEndTimestamp - this.reloadStartTimestamp), 0.0f, 1.0f);
    }

    public boolean chargeShoot(LocalPlayer player, ItemStack stack, boolean isChargingInput) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            resetCharge();
            return false;
        }
        GunData gunData = (GunData) TimelessAPI.getClientGunIndex(gun.getGunId(stack)).map(index -> {
            return index.getGunData();
        }).orElse(null);
        if (gunData == null) {
            resetCharge();
            return false;
        }
        FireMode fireMode = gun.getFireMode(stack);
        ChargeData chargeData = gunData.getChargeData(fireMode);
        if (chargeData == null) {
            this.charging = false;
            this.chargeProgress = 0.0f;
            return isChargingInput;
        }
        boolean canChargeDuringCooldown = chargeData.isChargeDuringCooldown() || getShootCoolDown(player, stack) < 50;
        boolean canCharge = canChargeDuringCooldown && !isDrawing() && !isReloading() && !isBolting() && !isManualShotAwaitingChamberSync() && System.currentTimeMillis() - LocalPlayerDataHolder.clientClickButtonTimestamp >= 50 && canFeedShot(player, stack, gun, gunData);
        float previousProgress = this.chargeProgress;
        ChargeType type = chargeData.getChargeType();
        if (type == ChargeType.AUTO) {
            if (isChargingInput && canCharge) {
                this.charging = true;
                this.chargeProgress = Math.min(previousProgress + chargeData.getIncreasePerTick(), chargeData.getMaxCharge());
                return this.chargeProgress >= chargeData.getMaxCharge();
            }
            this.charging = false;
            this.chargeProgress = Math.max(previousProgress - chargeData.getDecreasePerTick(), 0.0f);
            return false;
        }
        if (type == ChargeType.HOLD) {
            if (isChargingInput && canCharge) {
                this.charging = true;
                this.chargeProgress = Math.min(previousProgress + chargeData.getIncreasePerTick(), chargeData.getMaxCharge());
                return false;
            }
            if (canChargeDuringCooldown && previousProgress >= chargeData.getFireThreshold()) {
                return true;
            }
            this.charging = false;
            this.chargeProgress = Math.max(previousProgress - chargeData.getDecreasePerTick(), 0.0f);
            return false;
        }
        if (type == ChargeType.DELAY) {
            if ((isChargingInput || previousProgress > 0.0f) && canCharge) {
                this.charging = true;
                this.chargeProgress = Math.min(previousProgress + chargeData.getIncreasePerTick(), chargeData.getMaxCharge());
                return this.chargeProgress >= chargeData.getMaxCharge();
            }
            this.charging = false;
            this.chargeProgress = Math.max(previousProgress - chargeData.getDecreasePerTick(), 0.0f);
            return false;
        }
        return false;
    }

    private boolean canFeedShot(LocalPlayer player, ItemStack stack, IGun gun, GunData gunData) {
        if (com.ssscript.taczfixes.common.util.UnderwaterShooting.isBlocked(player, stack)) {
            return false;
        }
        boolean z;
        Bolt bolt = gunData.getBolt();
        boolean inBarrel = gun.hasBulletInBarrel(stack) && bolt != Bolt.OPEN_BOLT;
        boolean inventoryAmmo = gun.hasInventoryAmmo(player, stack, IGunOperator.fromLivingEntity(player).needCheckAmmo());
        int ammoCount = gun.getCurrentAmmoCount(stack) + (inBarrel ? 1 : 0);
        if (gun.useInventoryAmmo(stack)) {
            z = (inventoryAmmo || inBarrel) ? false : true;
        } else {
            z = ammoCount < 1;
        }
        boolean noAmmo = z;
        if (noAmmo) {
            return false;
        }
        if (bolt != Bolt.MANUAL_ACTION || inBarrel) {
            return (gunData.hasHeatData() && gun.isOverheatLocked(stack)) ? false : true;
        }
        return false;
    }

    public float getChargeProgress() {
        return this.chargeProgress;
    }

    public boolean isCharging() {
        return this.charging;
    }

    public void consumeChargeAfterShot(ItemStack stack, GunData gunData) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            resetCharge();
            return;
        }
        ChargeData chargeData = gunData.getChargeData(gun.getFireMode(stack));
        if (chargeData == null) {
            this.chargeProgress = 0.0f;
        } else if (chargeData.getChargeType() == ChargeType.DELAY) {
            this.chargeProgress = 0.0f;
        } else {
            this.chargeProgress = Math.max(0.0f, this.chargeProgress - chargeData.getDecreaseOnFire());
        }
    }

    public void resetCharge() {
        this.chargeProgress = 0.0f;
        this.charging = false;
    }

    public synchronized void reset() {
        this.shootTimestamp = -1L;
        this.lastShootTimestamp = -1L;
        this.pendingShootRequests.clear();
        clearShootTransitionLock();
        this.reloadStartTimestamp = -1L;
        this.reloadEndTimestamp = -1L;
        this.drawEndTimestamp = -1L;
        this.reloadStateType = ReloadState.StateType.NOT_RELOADING.ordinal();
        this.reloadRequestId = 0;
        this.reloadRequestPending = false;
        this.reloadCancelPending = false;
        this.reloadStackId = null;
        this.reloadAuthoritative = false;
        clearManualShotAwaitingChamberSync();
        clearBolt(true);
        this.boltRequestId = 0;
        this.serverManualActionBoltReady = false;
        this.fireSelectRequestId = 0;
        this.fireSelectRequestPending = false;
        this.fireSelectStackId = null;
        this.fireSelectRequestTimestamp = -1L;
        this.attachmentCache = null;
        this.cachedGunId = null;
        this.cachedGunData = null;
        this.cachedFireMode = null;
        this.propertyData.initialData();
        for (int index = 0; index < this.cachedAttachments.length; index++) {
            this.cachedAttachments[index] = null;
        }
        resetCharge();
    }

    /* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/render/ClientOffhandState$PendingShootRequestKey.class */
    private record PendingShootRequestKey(UUID stackId, long shootTimestamp) {

        private PendingShootRequestKey(UUID stackId, long shootTimestamp) {
            this.stackId = stackId;
            this.shootTimestamp = shootTimestamp;
        }

        public UUID stackId() {
            return this.stackId;
        }

        public long shootTimestamp() {
            return this.shootTimestamp;
        }
    }
}
