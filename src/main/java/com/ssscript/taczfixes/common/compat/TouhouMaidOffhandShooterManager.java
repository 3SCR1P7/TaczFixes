package com.ssscript.taczfixes.common.compat;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.util.DualReloadTimeController;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.ssscript.taczfixes.common.util.DualWieldStackId;
import com.ssscript.taczfixes.common.util.OffhandGunPropertyResolver;
import com.ssscript.taczfixes.common.util.OffhandShooterManager;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.shooter.LivingEntityBolt;
import com.tacz.guns.entity.shooter.LivingEntityDrawGun;
import com.tacz.guns.entity.shooter.LivingEntityReload;
import com.tacz.guns.entity.shooter.LivingEntityShoot;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.util.time.GunTime;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.items.IItemHandler;

@EventBusSubscriber(modid = TaczFixesMod.MOD_ID)
public final class TouhouMaidOffhandShooterManager {
    private static final long RELOAD_FAILURE_BACKOFF_TICKS = 100;
    private static final ResourceLocation MAID_ENTITY_ID = ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "maid");
    private static final Map<UUID, MaidOffhandShooter> SHOOTERS = new ConcurrentHashMap<>();
    private static final Set<String> WARNED_RELOAD_FAILURES = ConcurrentHashMap.newKeySet();

    private TouhouMaidOffhandShooterManager() {
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) throws IllegalAccessException, NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
        Entity eventEntity = event.getEntity();
        if (!(eventEntity instanceof LivingEntity entity)) {
            return;
        }
        if (entity.level().isClientSide() || !isTouhouMaid(entity)) {
            return;
        }
        UUID entityId = entity.getUUID();
        MaidOffhandShooter shooter = SHOOTERS.get(entityId);
        if (shooter != null && shooter.entity != entity) {
            remove(entityId);
            shooter = null;
        }
        if (!DualWieldEligibility.isDualWielding(entity) || !(entity instanceof Mob mob)) {
            if (shooter != null) {
                remove(entityId);
            }
        } else {
            if (shooter == null) {
                shooter = new MaidOffhandShooter(mob);
                SHOOTERS.put(entityId, shooter);
                OffhandShooterManager.registerExternalContext(entity, shooter.data);
            }
            shooter.tick();
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (isTouhouMaid(event.getEntity())) {
            remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity livingEntity && isTouhouMaid(livingEntity)) {
            remove(livingEntity.getUUID());
        }
    }

    private static boolean isTouhouMaid(LivingEntity entity) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return MAID_ENTITY_ID.equals(entityId);
    }

    private static void remove(UUID entityId) {
        MaidOffhandShooter removed = SHOOTERS.remove(entityId);
        if (removed != null) {
            OffhandShooterManager.unregisterExternalContext(removed.entity, removed.data);
        }
    }

    private static final class MaidOffhandShooter {
        private final Mob entity;
        private final LivingEntityDrawGun draw;
        private final LivingEntityShoot shoot;
        private final LivingEntityReload reload;
        private final LivingEntityBolt bolt;
        private ResourceLocation boundGunId;
        private UUID boundStackId;
        private long nextAttackGameTime;
        private final ShooterDataHolder data = new ShooterDataHolder();
        private final ItemStack[] boundAttachments = new ItemStack[AttachmentType.values().length];

        private MaidOffhandShooter(Mob entity) {
            this.entity = entity;
            this.draw = new LivingEntityDrawGun(entity, this.data);
            this.shoot = new LivingEntityShoot(entity, this.data, this.draw);
            this.reload = new LivingEntityReload(entity, this.data, this.draw, this.shoot);
            this.bolt = new LivingEntityBolt(this.data, entity, this.draw, this.shoot);
        }

        private void tick() throws IllegalAccessException, NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
            if (!bindIfChanged()) {
                return;
            }
            synchronizeBaseTimestamp();
            long gameTime = this.entity.level().getGameTime();
            OffhandShooterManager.pushActiveData(this.data);
            try {
                try {
                    this.reload.tickReloadState();
                } catch (RuntimeException exception) {
                    stopBrokenReload("tick_reload", exception, gameTime);
                }
                this.bolt.tickBolt();
                ItemStack stack = this.entity.getOffhandItem();
                if (stack.getItem() instanceof AbstractGunItem gunItem) {
                    gunItem.tickHeat(this.data, stack, this.entity);
                }
                OffhandShooterManager.popActiveData();
                ShooterDataHolder mainData = IGunOperator.fromLivingEntity(this.entity).getDataHolder();
                boolean mainhandFocusAiming = mainData.isAiming || mainData.aimingProgress > 0.0f;
                LivingEntity target = findAttackTarget();
                if (target == null || mainhandFocusAiming || gameTime < this.nextAttackGameTime || this.data.reloadStateType.isReloading() || this.data.isBolting) {
                    return;
                }
                fireAt(target, gameTime);
            } catch (Throwable th) {
                OffhandShooterManager.popActiveData();
                throw th;
            }
        }

        private boolean bindIfChanged() {
            ItemStack stack = this.entity.getOffhandItem();
            IGun gun = IGun.getIGunOrNull(stack);
            if (gun == null) {
                return false;
            }
            ResourceLocation gunId = gun.getGunId(stack);
            UUID stackId = DualWieldStackId.getOrCreate(stack);
            ItemStack mainStack = this.entity.getMainHandItem();
            UUID mainStackId = IGun.getIGunOrNull(mainStack) == null ? null : DualWieldStackId.getOrCreate(mainStack);
            if (stack != mainStack && stackId.equals(mainStackId)) {
                stackId = DualWieldStackId.regenerate(stack);
            }
            boolean identityChanged = !gunId.equals(this.boundGunId) || !stackId.equals(this.boundStackId);
            boolean attachmentChanged = updateAttachmentSignature(gun, stack);
            if (!identityChanged && !attachmentChanged) {
                return true;
            }
            if (identityChanged) {
                this.data.initialData();
                this.data.setCurrentGunItem(this.entity::getOffhandItem);
                long now = GunTime.nowMillis();
                this.data.drawTimestamp = now;
                this.data.lastShootTimestamp = -1L;
                this.data.heatTimestamp = now;
                this.data.currentPutAwayTimeS = 0.0f;
                this.data.baseTimestamp = IGunOperator.fromLivingEntity(this.entity).getDataHolder().baseTimestamp;
                this.boundGunId = gunId;
                this.boundStackId = stackId;
                this.nextAttackGameTime = this.entity.level().getGameTime();
            }
            OffhandGunPropertyResolver.rebuild(this.entity, stack, gun, this.data);
            return true;
        }

        private boolean updateAttachmentSignature(IGun gun, ItemStack stack) {
            boolean changed = false;
            for (AttachmentType type : AttachmentType.values()) {
                if (type != AttachmentType.NONE) {
                    ItemStack attachment = gun.getAttachment(this.entity.level().registryAccess(), stack, type);
                    int index = type.ordinal();
                    ItemStack previous = this.boundAttachments[index];
                    if (previous == null || !ItemStack.matches(attachment, previous)) {
                        this.boundAttachments[index] = attachment.copy();
                        changed = true;
                    }
                }
            }
            return changed;
        }

        private void synchronizeBaseTimestamp() {
            long synchronizedBase = IGunOperator.fromLivingEntity(this.entity).getDataHolder().baseTimestamp;
            long previousBase = this.data.baseTimestamp;
            if (previousBase == synchronizedBase) {
                return;
            }
            long relativeAdjustment = previousBase - synchronizedBase;
            if (this.data.shootTimestamp >= 0) {
                this.data.shootTimestamp += relativeAdjustment;
            }
            if (this.data.lastShootTimestamp >= 0) {
                this.data.lastShootTimestamp += relativeAdjustment;
            }
            this.data.baseTimestamp = synchronizedBase;
        }

        private LivingEntity findAttackTarget() {
            LivingEntity target = this.entity.getTarget();
            if (target == null) {
                target = this.entity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
            }
            if (target == null || !target.isAlive() || !this.entity.getSensing().hasLineOfSight(target)) {
                return null;
            }
            return target;
        }

        private void fireAt(LivingEntity target, long gameTime) throws IllegalAccessException, NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
            ItemStack stack = this.entity.getOffhandItem();
            IGun gun = IGun.getIGunOrNull(stack);
            CommonGunIndex gunIndex = gun == null ? null : TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).orElse(null);
            if (gun == null || gunIndex == null) {
                this.nextAttackGameTime = gameTime + RELOAD_FAILURE_BACKOFF_TICKS;
                return;
            }
            double targetX = target.getX() - this.entity.getX();
            double targetY = target.getEyeY() - this.entity.getEyeY();
            double targetZ = target.getZ() - this.entity.getZ();
            float yaw = (float) (-Math.toDegrees(Math.atan2(targetX, targetZ)));
            float pitch = (float) (-Math.toDegrees(Math.atan2(targetY, Math.sqrt((targetX * targetX) + (targetZ * targetZ)))));
            long timestamp = GunTime.nowMillis() - this.data.baseTimestamp;
            OffhandShooterManager.pushActiveData(this.data);
            try {
                ShootResult result = this.shoot.shoot(() -> Float.valueOf(pitch), () -> Float.valueOf(yaw), timestamp);
                OffhandShooterManager.popActiveData();
                handleShootResult(result, stack, gun, gunIndex, gameTime);
            } catch (Throwable th) {
                OffhandShooterManager.popActiveData();
                throw th;
            }
        }

        private void handleShootResult(ShootResult result, ItemStack stack, IGun gun, CommonGunIndex gunIndex, long gameTime) throws IllegalAccessException, NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
            if (result == ShootResult.SUCCESS) {
                this.entity.swing(InteractionHand.OFF_HAND, true);
                FireMode fireMode = gun.getFireMode(stack);
                long cooldown = fireMode == FireMode.SEMI || fireMode == FireMode.BURST
                        ? 10 + this.entity.getRandom().nextInt(5)
                        : 2;
                this.nextAttackGameTime = gameTime + cooldown;
                return;
            }
            if (result == ShootResult.NEED_BOLT) {
                OffhandShooterManager.pushActiveData(this.data);
                try {
                    this.bolt.bolt();
                    this.nextAttackGameTime = gameTime + Math.round(gunIndex.getGunData().getBoltActionTime() * 20.0f) + 2;
                    return;
                } finally {
                    OffhandShooterManager.popActiveData();
                }
            }
            if (result == ShootResult.NO_AMMO) {
                primeMaidAmmoRequest(stack);
                boolean reloadStarted = false;
                OffhandShooterManager.pushActiveData(this.data);
                try {
                    this.reload.reload();
                    reloadStarted = this.data.reloadStateType.isReloading();
                } catch (RuntimeException exception) {
                    stopBrokenReload("start_reload", exception, gameTime);
                } finally {
                    OffhandShooterManager.popActiveData();
                }
                if (!reloadStarted) {
                    this.nextAttackGameTime = Math.max(this.nextAttackGameTime, gameTime + RELOAD_FAILURE_BACKOFF_TICKS);
                    return;
                }
                float emptyReloadTime = gunIndex.getGunData().getReloadData().getCooldown().getEmptyTime();
                this.nextAttackGameTime = gameTime + Math.round(emptyReloadTime * 20.0f) + 2;
                return;
            }
            if (result == ShootResult.NOT_DRAW || result == ShootResult.IS_DRAWING) {
                this.nextAttackGameTime = gameTime + Math.round(gunIndex.getGunData().getDrawTime() * 20.0f) + 2;
            } else if (result == ShootResult.NOT_GUN || result == ShootResult.ID_NOT_EXIST) {
                this.nextAttackGameTime = gameTime + RELOAD_FAILURE_BACKOFF_TICKS;
            } else {
                this.nextAttackGameTime = gameTime + 1;
            }
        }

        private void stopBrokenReload(String phase, RuntimeException exception, long gameTime) {
            DualReloadTimeController.end(this.data, null);
            this.data.reloadStateType = ReloadState.StateType.NOT_RELOADING;
            this.data.reloadTimestamp = -1L;
            this.data.scriptData = null;
            this.nextAttackGameTime = Math.max(this.nextAttackGameTime, gameTime + RELOAD_FAILURE_BACKOFF_TICKS);
            String warningKey = String.valueOf(this.boundGunId) + ":" + phase;
            if (WARNED_RELOAD_FAILURES.add(warningKey)) {
                TaczFixesMod.LOGGER.error("Stopped broken maid offhand {} for {} on {} and applied a retry backoff", phase, this.boundGunId, this.entity.getUUID(), exception);
            }
        }

        private void primeMaidAmmoRequest(ItemStack gunStack) {
            try {
                Method getAvailableInventory = this.entity.getClass().getMethod("getAvailableInv", Boolean.TYPE);
                Object availableInventory = getAvailableInventory.invoke(this.entity, Boolean.TRUE);
                if (!(availableInventory instanceof IItemHandler itemHandler)) {
                    return;
                }
                for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
                    ItemStack candidate = itemHandler.getStackInSlot(slot);
                    IAmmo ammo = IAmmo.getIAmmoOrNull(candidate);
                    if (ammo != null && ammo.isAmmoOfGun(gunStack, candidate)) {
                        return;
                    }
                }
            } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            }
        }
    }
}
