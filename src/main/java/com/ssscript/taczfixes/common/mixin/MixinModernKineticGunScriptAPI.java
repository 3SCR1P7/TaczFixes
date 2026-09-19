package com.ssscript.taczfixes.common.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ssscript.taczfixes.common.data.CustomFireModeManager;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.ssscript.taczfixes.common.util.DualReloadTimeController;
import com.ssscript.taczfixes.common.util.OffhandGunPropertyResolver;
import com.ssscript.taczfixes.common.util.OffhandShooterManager;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.CycleTaskHelper;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {ModernKineticGunScriptAPI.class}, remap = false)
public abstract class MixinModernKineticGunScriptAPI {

    @Shadow
    private ItemStack itemStack;

    @Shadow
    private LivingEntity shooter;

    @Shadow
    private ShooterDataHolder dataHolder;

    @Unique
    public String tfGetCustomAttachment(String slotId) {
        ResourceLocation id = CustomSlotStorage.getAttachmentId(itemStack, slotId);
        return id == null ? "tacz:empty" : id.toString();
    }

    /** lua: gun:getLevel() 获取枪械当前经验等级。 */
    @Unique
    public int getLevel() {
        if (itemStack == null || itemStack.isEmpty()) return 0;
        return itemStack.getOrCreateTag().getInt("GunLevel");
    }

    /** lua: gun:getExp() 获取枪械当前经验值。 */
    @Unique
    public int getExp() {
        if (itemStack == null || itemStack.isEmpty()) return 0;
        return itemStack.getOrCreateTag().getInt("GunLevelExp");
    }

    /** lua: gun:setExp(int) 设置枪械经验值并同步重算等级。 */
    @Unique
    public void setExp(int exp) {
        if (itemStack == null || itemStack.isEmpty()) return;
        if (exp < 0) exp = 0;
        CompoundTag tag = itemStack.getOrCreateTag();
        tag.putInt("GunLevelExp", exp);
        IGun gun = IGun.getIGunOrNull(itemStack);
        if (gun != null) {
            tag.putInt("GunLevel", gun.getLevel(exp));
        }
    }

    /** lua: api:runCommand("cmd ...") 以开火者身份在服务端执行命令, 命令整体为一个字符串, 参数以空格拼接。
     *  如 api:runCommand("kill @s") / api:runCommand("summon minecraft:lightning_bolt -34 68 -121")。
     *  仅服务端执行, 成功返回 true, 未执行或失败返回 false。 */
    @Unique
    public boolean runCommand(String command) {
        if (command == null) return false;
        String s = command.trim();
        if (s.isEmpty()) return false;
        if (shooter == null) return false;
        if (shooter.level() != null && shooter.level().isClientSide) return false;
        MinecraftServer server = shooter.getServer();
        if (server == null) return false;
        CommandSourceStack source = shooter.createCommandSourceStack().withSuppressedOutput().withPermission(4);
        try {
            return server.getCommands().performPrefixedCommand(source, s) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 射击流程内激活当前自定义模式, 使 GunData 的 adjust/burst 查询按该 id 精确取用。
     *  激活/清空由外层 LivingEntityShoot 统一管理; 此处仅为 Lua 直调 shootOnce 兜底。 */
    @Inject(method = "shootOnce(Z)V", at = @At("HEAD"), remap = false)
    private void taczfixes$activateCustomModeHead(boolean needConsumeAmmo, CallbackInfo ci) {
        CustomFireModeManager.activateFor(itemStack);
    }

    /** lua: api:getScoreboardValue("aaaa") 获取开火者玩家在名称 "aaaa"(或任意名字)的计分板上的值。
     *  计分板不存在或开火者无该值返回 0; 仅服务端生效。 */
    @Unique
    public int getScoreboardValue(String objectiveName) {
        if (objectiveName == null || objectiveName.isEmpty()) return 0;
        if (shooter == null) return 0;
        if (shooter.level() != null && shooter.level().isClientSide) return 0;
        MinecraftServer server = shooter.getServer();
        if (server == null) return 0;
        net.minecraft.world.scores.Objective objective =
                server.getScoreboard().getObjective(objectiveName);
        if (objective == null) return 0;
        net.minecraft.world.scores.Score score =
                server.getScoreboard().getOrCreatePlayerScore(shooter.getScoreboardName(), objective);
        return score == null ? 0 : score.getScore();
    }

    /** lua: api:getPlayerFacing() 开火者面向方向 {偏航, 俯仰} (MC 角度, 俯仰正值朝上)。 */
    @Unique
    public org.luaj.vm2.LuaValue getPlayerFacing() {
        return com.ssscript.taczfixes.common.util.ShooterLuaHelper.facing(shooter);
    }

    /** lua: api:getPlayerTarget() 开火者视线指向的方块坐标 {X, Y, Z}(未命中返回 {0,0,0})。 */
    @Unique
    public org.luaj.vm2.LuaValue getPlayerTarget() {
        return com.ssscript.taczfixes.common.util.ShooterLuaHelper.target(shooter);
    }

    /** lua: api:getPlayerPosition() 开火者当前坐标 {X, Y, Z}。 */
    @Unique
    public org.luaj.vm2.LuaValue getPlayerPosition() {
        return com.ssscript.taczfixes.common.util.ShooterLuaHelper.position(shooter);
    }

    /** lua: api:getInput() 开火者(本地玩家)当前按下的所有按键, 列表如 {"key.mouse.left","key.keyboard.1","key.keyboard.down"}。
     *  仅本地客户端玩家有效, 其他情况返回空表。*/
    @Unique
    public org.luaj.vm2.LuaValue getInput() {
        return com.ssscript.taczfixes.common.util.ShooterLuaHelper.input(shooter);
    }

    /** lua: api:getDualWieldState() 当前枪械在双持中的位置: "right"(主手), "left"(副手), "none"(未双持)。 */
    @Unique
    public org.luaj.vm2.LuaValue getDualWieldState() {
        if (shooter == null || !com.ssscript.taczfixes.common.util.DualWieldEligibility.isDualWielding(shooter)) {
            return org.luaj.vm2.LuaValue.valueOf("none");
        }
        if (com.ssscript.taczfixes.common.util.OffhandShooterManager.isOffhandData(this.dataHolder)) {
            return org.luaj.vm2.LuaValue.valueOf("left");
        }
        return org.luaj.vm2.LuaValue.valueOf("right");
    }

    /** lua: api:getChargePower() 当前电量(FE)。 */
    @Unique
    public int getChargePower() {
        return com.ssscript.taczfixes.common.util.ChargeStorage.get(this.itemStack);
    }

    /** lua: api:setChargePower(int) 设置当前电量(FE), 自动截断到 [0, 上限]。 */
    @Unique
    public void setChargePower(int value) {
        com.ssscript.taczfixes.common.util.ChargeStorage.set(this.itemStack, value);
    }

    /** lua: api:getChargePowerMax() 电量上限(FE)。 */
    @Unique
    public int getChargePowerMax() {
        return com.ssscript.taczfixes.common.util.ChargeStorage.getMax(this.itemStack);
    }

    /** lua: api:getEnchantments() 返回此枪械全部附魔及等级, 如 {{"taczfixes:abyssgazer",1},{"taczfixes:pandora_paradox",2}}。 */
    @Unique
    public org.luaj.vm2.LuaValue getEnchantments() {
        org.luaj.vm2.LuaTable result = new org.luaj.vm2.LuaTable();
        if (itemStack == null || itemStack.isEmpty()) {
            return result;
        }
        net.minecraft.nbt.ListTag list = itemStack.getEnchantmentTags();
        int index = 1;
        for (int i = 0; i < list.size(); i++) {
            net.minecraft.nbt.CompoundTag entry = list.getCompound(i);
            String id = entry.getString("id");
            if (id.isEmpty()) {
                continue;
            }
            org.luaj.vm2.LuaTable pair = new org.luaj.vm2.LuaTable();
            pair.set(1, org.luaj.vm2.LuaValue.valueOf(id));
            pair.set(2, org.luaj.vm2.LuaValue.valueOf(entry.getInt("lvl")));
            result.set(index++, pair);
        }
        return result;
    }

    /** lua: api:textShow("Ssscript","vvvv") 将语言文件中的 %vvvv% 占位符替换为 Ssscript。 */
    @Unique
    public void textShow(String value, String name) {
        String key = com.ssscript.taczfixes.common.util.TextShowStorage.normalizeName(name);
        if (key.isEmpty()) {
            return;
        }
        com.ssscript.taczfixes.common.util.TextShowStorage.set(this.itemStack, key, value);
        if (this.shooter != null && this.shooter.level() != null && this.shooter.level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.ssscript.taczfixes.client.util.TextShowPapiCompat.register(key));
        }
    }

    /** 开火前预检查电量; 电量不足且 blocking_fire 时取消整个连发并播放 dry_fire(实际消耗在每发子弹开火时)。 */
    @Inject(method = "shootOnce(Z)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$checkCharge(boolean needConsumeAmmo, CallbackInfo ci) {
        com.ssscript.taczfixes.common.data.GunTaczFixesData.ChargeConfig cfg =
                com.ssscript.taczfixes.common.util.ChargeStorage.config(this.itemStack);
        if (cfg == null || cfg.fire_consumption == null || cfg.fire_consumption.intValue() <= 0) {
            return;
        }
        if (com.ssscript.taczfixes.common.util.ChargeStorage.getMax(this.itemStack) <= 0) {
            return;
        }
        if (com.ssscript.taczfixes.common.util.ChargeStorage.get(this.itemStack) < cfg.fire_consumption.intValue()
                && Boolean.TRUE.equals(cfg.blocking_fire)) {
            taczfixes$playDryFire();
            ci.cancel();
        }
    }

    /** 每发实际开火(含 burst 连发的每一发)消耗一次电量; 中途电量不足且 blocking_fire 时终止本轮连发。 */
    @Inject(method = "lambda$shootOnce$2(ZLcom/tacz/guns/resource/modifier/AttachmentCacheProperty;ILcom/tacz/guns/resource/pojo/data/gun/GunData;Lcom/tacz/guns/resource/pojo/data/gun/BulletData;Lcom/tacz/guns/api/entity/IGunOperator;FFFIZ)Z",
            at = @At(value = "NEW", target = "com/tacz/guns/network/message/event/ServerMessageGunFire"),
            cancellable = true, remap = false)
    private void taczfixes$consumeChargePerShot(boolean needConsumeAmmo, AttachmentCacheProperty cache, int bulletAmount,
                                                GunData gunData, BulletData bulletData, IGunOperator operator,
                                                float damageMultiplier, float bulletSpeed, float inaccuracy,
                                                int soundDistance, boolean silenced, CallbackInfoReturnable<Boolean> cir) {
        com.ssscript.taczfixes.common.data.GunTaczFixesData.ChargeConfig cfg =
                com.ssscript.taczfixes.common.util.ChargeStorage.config(this.itemStack);
        if (cfg == null || cfg.fire_consumption == null || cfg.fire_consumption.intValue() <= 0) {
            return;
        }
        if (com.ssscript.taczfixes.common.util.ChargeStorage.getMax(this.itemStack) <= 0) {
            return;
        }
        if (!com.ssscript.taczfixes.common.util.ChargeStorage.consume(this.itemStack, cfg.fire_consumption.intValue())
                && Boolean.TRUE.equals(cfg.blocking_fire)) {
            taczfixes$playDryFire();
            cir.setReturnValue(false);
        }
    }

    @Unique
    private void taczfixes$playDryFire() {
        if (this.shooter == null || !this.shooter.level().isClientSide) {
            return;
        }
        if (!(this.shooter instanceof net.minecraft.client.player.LocalPlayer player)) {
            return;
        }
        com.tacz.guns.api.TimelessAPI.getGunDisplay(this.itemStack).ifPresent(display -> {
            net.minecraft.resources.ResourceLocation sound = display.getSounds(com.tacz.guns.sound.SoundManager.DRY_FIRE_SOUND);
            if (sound == null) {
                return;
            }
            com.tacz.guns.client.sound.SoundPlayManager.stopPlayGunSound();
            com.tacz.guns.client.sound.SoundPlayManager.playClientSound(player, sound, 1.0f, 1.0f,
                    ((Integer) com.tacz.guns.config.common.GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get()).intValue());
        });
    }

    /** lua: api:replaceData("tacz:ak47_data") 将此枪械的 data 替换为指定 id 的枪械数据, 成功返回 true。 */
    @Unique
    public boolean replaceData(String dataId) {
        return replaceGunId(dataId, false);
    }

    /** lua: api:replaceDisplay("tacz:minigun_display") 将此枪械的 display 替换为指定 id, 成功返回 true。 */
    @Unique
    public boolean replaceDisplay(String displayId) {
        return replaceGunId(displayId, true);
    }

    @Unique
    private boolean replaceGunId(String idStr, boolean display) {
        if (idStr == null || idStr.isEmpty()) return false;
        if (itemStack == null || itemStack.isEmpty()) return false;
        if (shooter == null) return false;
        if (shooter.level() != null && shooter.level().isClientSide) return false;
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(idStr.trim());
        if (id == null) return false;
        IGun gun = IGun.getIGunOrNull(itemStack);
        if (gun == null) return false;
        if (!display) {
            // gun data 为共享(服务端)资源, 校验存在性; display 是客户端资源, 服务端无索引, 不校验。
            com.tacz.guns.resource.ICommonResourceProvider provider = com.tacz.guns.resource.CommonAssetsManager.get();
            if (provider == null || provider.getGunIndex(id) == null) return false;
        }
        net.minecraft.resources.ResourceLocation current = display ? gun.getGunDisplayId(itemStack) : gun.getGunId(itemStack);
        boolean same = id.equals(current);
        if (display) {
            gun.setGunDisplayId(itemStack, id);
        } else {
            gun.setGunId(itemStack, id);
        }
        if (!same && shooter instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            // 直推客户端手持物品同步 NBT, 保证下一帧立即使用新的 data/display 渲染
            com.ssscript.taczfixes.common.network.NetworkHandler.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new com.ssscript.taczfixes.common.network.ClientMessageReplaceGun(id.toString(), display));
            serverPlayer.inventoryMenu.broadcastChanges();
        }
        return true;
    }

    @Inject(method = {"getReloadTime"}, at = {@At("RETURN")}, cancellable = true)
    private void dualWield$slowReloadClock(CallbackInfoReturnable<Long> callback) {
        callback.setReturnValue(Long.valueOf(DualReloadTimeController.toVirtualDuration(this.dataHolder, this.itemStack, callback.getReturnValue().longValue())));
    }

    @ModifyVariable(method = {"adjustReloadTime"}, at = @At("HEAD"), argsOnly = true, ordinal = ServerMessageOffhandActionResult.ACTION_SHOOT)
    private long dualWield$compensateReloadAdjustment(long alpha) {
        return DualReloadTimeController.toRealAdjustment(this.dataHolder, this.itemStack, alpha);
    }

    @Redirect(method = {"safeAsyncTask"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/util/CycleTaskHelper;addCycleTask(Ljava/util/function/BooleanSupplier;JJI)V"), require = 0)
    private void dualWield$guardSafeAsyncTask(BooleanSupplier task, long delayMillis, long periodMillis, int cycles) {
        CycleTaskHelper.addCycleTask(dualWield$wrapManualSafeAsyncTask(task, cycles), delayMillis, periodMillis, cycles);
    }

    @WrapOperation(method = {"shootOnce"}, at = {@At(value = "INVOKE", target = "Lcom/tacz/guns/util/CycleTaskHelper;addCycleTask(Ljava/util/function/BooleanSupplier;JI)V")}, require = 0)
    private void dualWield$guardShootOnceTask(BooleanSupplier task, long periodMillis, int cycles, Operation<Void> original) {
        original.call(new Object[]{dualWield$wrapManualGenerationTask(task), Long.valueOf(periodMillis), Integer.valueOf(cycles)});
    }

    @Unique
    private BooleanSupplier dualWield$wrapManualGenerationTask(BooleanSupplier task) {
        long generation = OffhandShooterManager.captureManualAsyncTaskGeneration(this.dataHolder, this.itemStack);
        if (generation == Long.MIN_VALUE) {
            return task;
        }
        return () -> {
            if (!OffhandShooterManager.isManualAsyncTaskGenerationCurrent(this.shooter, this.dataHolder, this.itemStack, generation)) {
                return false;
            }
            OffhandShooterManager.pushActiveData(this.dataHolder);
            try {
                boolean asBoolean = task.getAsBoolean();
                OffhandShooterManager.popActiveData();
                return asBoolean;
            } catch (Throwable th) {
                OffhandShooterManager.popActiveData();
                throw th;
            }
        };
    }

    @Unique
    private BooleanSupplier dualWield$wrapManualSafeAsyncTask(BooleanSupplier task, int cycles) {
        long generation = OffhandShooterManager.captureManualAsyncTaskGeneration(this.dataHolder, this.itemStack);
        if (generation == Long.MIN_VALUE) {
            return task;
        }
        OffhandShooterManager.recordManualSafeAsyncTask(this.dataHolder, this.itemStack, generation);
        AtomicInteger completedInvocations = new AtomicInteger();
        AtomicBoolean completionReported = new AtomicBoolean();
        return () -> {
            if (!OffhandShooterManager.isManualAsyncTaskGenerationCurrent(this.shooter, this.dataHolder, this.itemStack, generation)) {
                if (completionReported.compareAndSet(false, true)) {
                    OffhandShooterManager.finishManualSafeAsyncTask(this.dataHolder, this.itemStack, generation);
                    return false;
                }
                return false;
            }
            boolean completedNormally = false;
            OffhandShooterManager.pushActiveData(this.dataHolder);
            try {
                boolean continueTask = task.getAsBoolean();
                completedNormally = true;
                OffhandShooterManager.popActiveData();
                if (1 == 0 && completionReported.compareAndSet(false, true)) {
                    OffhandShooterManager.finishManualSafeAsyncTask(this.dataHolder, this.itemStack, generation);
                }
                int invocationCount = completedInvocations.incrementAndGet();
                boolean reachedCycleLimit = cycles > 0 && invocationCount >= cycles;
                if ((!continueTask || reachedCycleLimit) && completionReported.compareAndSet(false, true)) {
                    OffhandShooterManager.finishManualSafeAsyncTask(this.dataHolder, this.itemStack, generation);
                }
                return continueTask;
            } catch (Throwable th) {
                OffhandShooterManager.popActiveData();
                if (!completedNormally && completionReported.compareAndSet(false, true)) {
                    OffhandShooterManager.finishManualSafeAsyncTask(this.dataHolder, this.itemStack, generation);
                }
                throw th;
            }
        };
    }

    @Redirect(method = {"shootOnce", "getCachedProperty"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/entity/IGunOperator;getCacheProperty()Lcom/tacz/guns/resource/modifier/AttachmentCacheProperty;"))
    private AttachmentCacheProperty dualWield$resolveCache(IGunOperator operator) {
        if (OffhandShooterManager.isOffhandData(this.dataHolder) || OffhandGunPropertyResolver.isActive(this.dataHolder)) {
            return this.dataHolder.cacheProperty;
        }
        return operator.getCacheProperty();
    }

    @Inject(method = {"setAmmoInBarrel"}, at = {@At("HEAD")})
    private void dualWield$recordManualActionChamberRefill(boolean hasAmmo, CallbackInfo callback) {
        if (hasAmmo && OffhandShooterManager.isOffhandData(this.dataHolder)) {
            OffhandShooterManager.recordManualActionChamberRefill(this.dataHolder, this.itemStack);
        }
    }

    @Redirect(method = {"lambda$shootOnce$2(ZLcom/tacz/guns/resource/modifier/AttachmentCacheProperty;ILcom/tacz/guns/resource/pojo/data/gun/GunData;Lcom/tacz/guns/resource/pojo/data/gun/BulletData;Lcom/tacz/guns/api/entity/IGunOperator;FFFIZ)Z"}, at = @At(value = "INVOKE", target = "Ljava/lang/Object;equals(Ljava/lang/Object;)Z"), require = 0)
    private boolean dualWield$matchesBurstGun(Object resolvedMainGun, Object expectedGun) {
        ItemStack itemStack;
        if (!OffhandShooterManager.isOffhandData(this.dataHolder)) {
            return resolvedMainGun != null && resolvedMainGun.equals(expectedGun);
        }
        OffhandShooterManager.recordBulletAttempt(this.dataHolder, this.itemStack);
        if (!OffhandShooterManager.isCurrentOffhandContext(this.shooter, this.dataHolder)) {
            return false;
        }
        if (this.dataHolder.currentGunItem == null) {
            itemStack = ItemStack.EMPTY;
        } else {
            itemStack = this.dataHolder.currentGunItem.get();
        }
        ItemStack liveOffhand = itemStack;
        return liveOffhand == this.itemStack && !liveOffhand.isEmpty();
    }

    @Inject(method = {"lambda$shootOnce$2(ZLcom/tacz/guns/resource/modifier/AttachmentCacheProperty;ILcom/tacz/guns/resource/pojo/data/gun/GunData;Lcom/tacz/guns/resource/pojo/data/gun/BulletData;Lcom/tacz/guns/api/entity/IGunOperator;FFFIZ)Z"}, at = {@At("RETURN")}, require = 0)
    private void dualWield$finishBulletAttempt(boolean consumeAmmo, AttachmentCacheProperty cacheProperty, int bulletAmount, GunData gunData, BulletData bulletData, IGunOperator operator, float damageMultiplier, float projectileSpeed, float inaccuracy, int soundDistance, boolean silence, CallbackInfoReturnable<Boolean> callback) {
        if (OffhandShooterManager.isOffhandData(this.dataHolder)) {
            OffhandShooterManager.finishBulletAttempt(this.dataHolder, this.itemStack);
        }
    }

    @Redirect(method = {"lambda$shootOnce$2(ZLcom/tacz/guns/resource/modifier/AttachmentCacheProperty;ILcom/tacz/guns/resource/pojo/data/gun/GunData;Lcom/tacz/guns/resource/pojo/data/gun/BulletData;Lcom/tacz/guns/api/entity/IGunOperator;FFFIZ)Z"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/entity/IGunOperator;nextBulletIsTracer(I)Z"))
    private boolean dualWield$nextTracer(IGunOperator operator, int interval) {
        if (!OffhandShooterManager.isOffhandData(this.dataHolder)) {
            return operator.nextBulletIsTracer(interval);
        }
        this.dataHolder.shootCount++;
        return interval != -1 && this.dataHolder.shootCount % (interval + 1) == 0;
    }

    @WrapOperation(method = {"lambda$shootOnce$2(ZLcom/tacz/guns/resource/modifier/AttachmentCacheProperty;ILcom/tacz/guns/resource/pojo/data/gun/GunData;Lcom/tacz/guns/resource/pojo/data/gun/BulletData;Lcom/tacz/guns/api/entity/IGunOperator;FFFIZ)Z"}, at = {@At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", remap = true)}, require = 0)
    private boolean dualWield$recordAcceptedBullet(Level level, Entity entity, Operation<Boolean> original) {
        boolean added = original.call(new Object[]{level, entity}).booleanValue();
        if (added && OffhandShooterManager.isOffhandData(this.dataHolder)) {
            OffhandShooterManager.recordBulletSpawn(this.dataHolder, this.itemStack);
        }
        return added;
    }
}
