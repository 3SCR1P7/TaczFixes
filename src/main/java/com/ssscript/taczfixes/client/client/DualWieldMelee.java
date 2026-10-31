package com.ssscript.taczfixes.client.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.compat.playeranimator.AnimationName;
import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunDefaultMeleeData;
import com.tacz.guns.resource.pojo.data.gun.GunMeleeData;
import com.ssscript.taczfixes.common.network.ClientMessageOffhandMelee;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.common.util.DualWieldStackId;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.LogicalSide;

/** 双持近战: 两把枪都支持时奇数次主手、偶数次副手; 冷却切换比例与主手共享判定。 */
public final class DualWieldMelee {
    private static boolean lastMeleeWasOffhand = true;
    private static long lastOffhandMeleeTimestamp;
    private static long offhandMeleeEndTimestamp;

    private DualWieldMelee() {
    }

    /** 退出双持/切换枪械时重置近战轮换与冷却。 */
    static void reset() {
        lastMeleeWasOffhand = true;
        lastOffhandMeleeTimestamp = 0L;
        offhandMeleeEndTimestamp = 0L;
    }
    /* ---------- 双持近战: 两把枪都支持时奇数次主手、偶数次副手 ---------- */

    private enum MeleeKind { MUZZLE, STOCK, PUSH }

    /** 双持近战键处理: 返回 true 表示由副手执行(需取消 TACZ 的主手近战)。 */
    public static boolean handleDualMeleeKey(LocalPlayer player) {
        if (player == null || !DualWieldClient.isDualMode(player)) {
            return false;
        }
        ItemStack mainStack = player.getMainHandItem();
        ItemStack offhandStack = player.getOffhandItem();
        boolean mainSupported = supportsMelee(mainStack);
        boolean offhandSupported = supportsMelee(offhandStack);
        if (!mainSupported && !offhandSupported) {
            return false;
        }
        boolean mainReady = mainSupported && isMainHandMeleeReady(player);
        boolean offhandReady = offhandSupported && isOffhandMeleeReady(player);
        if (!mainReady && !offhandReady) {
            return false;
        }
        // 上次是主手 → 优先副手(不能则主手); 上次是副手(或尚未近战) → 优先主手(不能则副手)。
        boolean useOffhand = lastMeleeWasOffhand ? !mainReady : offhandReady;
        if (!useOffhand) {
            if (mainReady) {
                lastMeleeWasOffhand = false;
            }
            return false;
        }
        if (meleeOffhand(player)) {
            lastMeleeWasOffhand = true;
        }
        return true;
    }

    /** 主手是否可以近战: 副手近战冷却(未到切换比例)或主手正在换弹/切枪/拉栓/开火/状态锁定时不可用。 */
    private static boolean isMainHandMeleeReady(LocalPlayer player) {
        if (offhandMeleeBlocksMainHand()) {
            return false;
        }
        IGunOperator operator = IGunOperator.fromLivingEntity(player);
        if (operator.getSynMeleeCoolDown() > 0
                || operator.getSynReloadState().getStateType().isReloading()
                || operator.getSynDrawCoolDown() != 0
                || operator.getSynIsBolting()
                || operator.getSynShootCoolDown() > 0) {
            return false;
        }
        return !IClientPlayerGunOperator.fromLocalPlayer(player).getDataHolder().clientStateLock;
    }

    /** 副手是否可以近战: 主手近战冷却(未到切换比例)、副手自身近战冷却中或副手正在切枪/换弹/拉栓/开火/连发视觉中不可用。 */
    private static boolean isOffhandMeleeReady(LocalPlayer player) {
        // 单手瞄准(主手)期间副手降下到低位, 不允许副手近战
        if (DualFocusAimState.isPoseVisible()) {
            return false;
        }
        if (mainHandMeleeBlocksOffhand(player) || isOffhandMeleeCoolingDown()) {
            return false;
        }
        ItemStack stack = player.getOffhandItem();
        ClientOffhandState state = OffhandDisplayManager.getClientState();
        if (state.isDrawing() || state.isReloading() || state.isBolting()
                || state.isManualShotAwaitingChamberSync() || state.isServerManualActionBoltReady()
                || DualWieldClient.isOffhandShotPending() || DualReloadAnimationManager.isActive()
                || state.getShootCoolDown(player, stack) > 0) {
            return false;
        }
        return true;
    }

    private static boolean meleeOffhand(LocalPlayer player) {
        ItemStack stack = player.getOffhandItem();
        IGun gun = IGun.getIGunOrNull(stack);
        GunDisplayInstance display = OffhandDisplayManager.getOrCreate(stack);
        ClientOffhandState state = OffhandDisplayManager.getClientState();
        if (gun == null || display == null || state.isDrawing() || state.isReloading() || state.isBolting()
                || DualWieldClient.isOffhandShotPending() || DualReloadAnimationManager.isActive()) {
            return false;
        }
        if (mainHandMeleeBlocksOffhand(player)) {
            return false;
        }
        com.ssscript.taczfixes.common.data.GunTaczFixesData.AimingStaminaConfig staminaCfg =
                com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager.resolveAimingStamina(stack);
        float meleeCost = staminaCfg.melee_cost.floatValue();
        if (meleeCost > 0.0f && com.ssscript.taczfixes.client.util.AimingStaminaClientState.isInsufficient(meleeCost)) {
            return false;
        }
        ClientGunIndex gunIndex = (ClientGunIndex) TimelessAPI.getClientGunIndex(gun.getGunId(stack)).orElse(null);
        GunData gunData = gunIndex == null ? null : gunIndex.getGunData();
        MeleeKind kind = resolveMeleeKind(stack, gun, gunData);
        if (kind == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        long cooldownMillis = meleeCooldownMillis(stack, gun, gunData, kind);
        if (now - lastOffhandMeleeTimestamp < cooldownMillis) {
            return false;
        }
        GunMeleeEvent event = new GunMeleeEvent(player, stack, LogicalSide.CLIENT);
        if (net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event).isCanceled()) {
            return false;
        }
        lastOffhandMeleeTimestamp = now;
        offhandMeleeEndTimestamp = now + cooldownMillis;
        NetworkHandler.CHANNEL.sendToServer(new ClientMessageOffhandMelee(DualWieldStackId.getOrCreate(stack)));
        switch (kind) {
            case MUZZLE -> {
                SoundPlayManager.playMeleeBayonetSound(player, display);
                OffhandDisplayManager.triggerNative("bayonet_muzzle");
            }
            case STOCK -> {
                SoundPlayManager.playMeleeStockSound(player, display);
                OffhandDisplayManager.triggerNative("bayonet_stock");
            }
            case PUSH -> {
                SoundPlayManager.playMeleePushSound(player, display);
                OffhandDisplayManager.triggerNative("bayonet_push");
            }
        }
        // TaCZ 的第三人称近战动画只由主手的 GunMeleeEvent 驱动, 副手补播一次
        String meleeAnimation = switch (player.getRandom().nextInt(3)) {
            case 0 -> AnimationName.MELEE_UPPER;
            case 1 -> AnimationName.MELEE_2_UPPER;
            default -> AnimationName.MELEE_3_UPPER;
        };
        DualWieldClient.playOffhandThirdPersonPlayerAnimation(player, stack, meleeAnimation, meleeAnimation);
        return true;
    }

    /** 副手近战冷却中(此时副手自身也不能再次近战)。 */
    public static boolean isOffhandMeleeCoolingDown() {
        return System.currentTimeMillis() < offhandMeleeEndTimestamp;
    }

    /** 主手近战冷却是否仍阻挡副手(冷却进行到配置比例后不再阻挡)。 */
    private static boolean mainHandMeleeBlocksOffhand(LocalPlayer player) {
        long remaining = IGunOperator.fromLivingEntity(player).getSynMeleeCoolDown();
        long total = com.ssscript.taczfixes.common.util.MeleeCooldownHelper
                .totalCooldownMillis(player.getMainHandItem());
        return meleeStillBlocking(remaining, total);
    }

    /** 副手近战冷却是否仍阻挡主手(冷却进行到配置比例后不再阻挡)。 */
    public static boolean offhandMeleeBlocksMainHand() {
        long now = System.currentTimeMillis();
        long remaining = offhandMeleeEndTimestamp - now;
        long total = offhandMeleeEndTimestamp - lastOffhandMeleeTimestamp;
        return meleeStillBlocking(remaining, total);
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

    /** 枪械是否支持近战: 枪口/枪托配件带近战数据, 或枪械 data 有近战默认数据。 */
    private static boolean supportsMelee(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return false;
        }
        if (attachmentMeleeData(gun.getAttachmentId(stack, AttachmentType.MUZZLE)) != null
                || attachmentMeleeData(gun.getAttachmentId(stack, AttachmentType.STOCK)) != null) {
            return true;
        }
        GunData data = clientGunData(gun, stack);
        return data != null && data.getMeleeData() != null && data.getMeleeData().getDefaultMeleeData() != null;
    }

    private static MeleeKind resolveMeleeKind(ItemStack stack, IGun gun, GunData gunData) {
        if (attachmentMeleeData(gun.getAttachmentId(stack, AttachmentType.MUZZLE)) != null) {
            return MeleeKind.MUZZLE;
        }
        if (attachmentMeleeData(gun.getAttachmentId(stack, AttachmentType.STOCK)) != null) {
            return MeleeKind.STOCK;
        }
        GunMeleeData meleeData = gunData == null ? null : gunData.getMeleeData();
        GunDefaultMeleeData defaultMelee = meleeData == null ? null : meleeData.getDefaultMeleeData();
        if (defaultMelee == null) {
            return null;
        }
        return "melee_stock".equals(defaultMelee.getAnimationType()) ? MeleeKind.STOCK : MeleeKind.PUSH;
    }

    /** 近战总冷却 = 枪械近战冷却 + 对应近战数据冷却。 */
    private static long meleeCooldownMillis(ItemStack stack, IGun gun, GunData gunData, MeleeKind kind) {
        GunMeleeData meleeData = gunData == null ? null : gunData.getMeleeData();
        float seconds = meleeData == null ? 0.0f : meleeData.getCooldown();
        if (kind == MeleeKind.MUZZLE) {
            seconds += attachmentMeleeCooldown(stack, gun, AttachmentType.MUZZLE);
        } else if (kind == MeleeKind.STOCK) {
            seconds += attachmentMeleeCooldown(stack, gun, AttachmentType.STOCK);
        } else if (meleeData != null && meleeData.getDefaultMeleeData() != null) {
            seconds += meleeData.getDefaultMeleeData().getCooldown();
        }
        return Math.max(0L, (long) (seconds * 1000.0f));
    }

    private static float attachmentMeleeCooldown(ItemStack stack, IGun gun, AttachmentType type) {
        com.tacz.guns.resource.pojo.data.attachment.MeleeData data =
                attachmentMeleeData(gun.getAttachmentId(stack, type));
        return data == null ? 0.0f : data.getCooldown();
    }

    private static com.tacz.guns.resource.pojo.data.attachment.MeleeData attachmentMeleeData(ResourceLocation attachmentId) {
        if (attachmentId == null || com.tacz.guns.api.DefaultAssets.isEmptyAttachmentId(attachmentId)) {
            return null;
        }
        return TimelessAPI.getClientAttachmentIndex(attachmentId)
                .map(index -> index.getData().getMeleeData())
                .orElse(null);
    }

    private static GunData clientGunData(IGun gun, ItemStack stack) {
        return (GunData) TimelessAPI.getClientGunIndex(gun.getGunId(stack))
                .map(index -> index.getGunData())
                .orElse(null);
    }
    static boolean isOffhandShotPending() {
        return DualWieldClient.isOffhandShotPending();
    }
}