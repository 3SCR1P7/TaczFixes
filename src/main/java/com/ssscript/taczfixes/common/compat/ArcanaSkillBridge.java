package com.ssscript.taczfixes.common.compat;

import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.ssscript.taczfixes.common.util.DualWieldStackId;
import com.ssscript.taczfixes.common.util.OffhandShooterManager;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TaCZ: Arcana 技能桥接 (对应 Arcana 1.2.9 混淆名):
 * Arcana 的技能匹配、变量(getMainHandNBT 等)、条件与动作全部按 player.mainHandItem 解析枪械,
 * 双持时副手枪的技能会落到主手枪上。本桥接包含两部分:
 * 1. MixinLivingEntityOffhandMainHand: 副手处理上下文内把 getMainHandItem() 解析为副手枪械,
 *    使 Arcana 在副手开火/换弹/拉栓/近战/子弹事件中的技能求值作用于副手枪;
 * 2. 依据 Arcana 主手状态机语义为副手枪补发 ON_MAIN_HAND (装备/切换 forward, 卸下/切换 reverse)。
 */
public final class ArcanaSkillBridge {

    private static final String MANAGER_CLASS = "group.taczexpands.dist.mwicXaw4";
    private static final String TRIGGER_TYPE_CLASS = "group.taczexpands.dist.AyPUUNbN";
    private static final String CONTEXT_CLASS = "group.taczexpands.dist.mqyj6zVi";
    private static final String CHANGE_CONTEXT_CLASS = "group.taczexpands.dist.h2aWbnYJ";
    private static final String METHOD_TRIGGER = "flUVrHPK";
    private static final String METHOD_TRIGGER_REVERSE = "z9uRAw1a";
    private static final String TRIGGER_ON_MAIN_HAND = "ON_MAIN_HAND";
    private static final String TRIGGER_ON_PLAYER_TICK = "ON_PLAYER_TICK";
    private static final String TRIGGER_ON_CLICK = "ON_CLICK";
    private static final String TRIGGER_ON_AUTO_SHOOT = "ON_AUTO_SHOOT";
    private static final String TRIGGER_ON_CHANGE_ATTACHMENT = "ON_CHANGE_ATTACHMENT";

    private static volatile boolean resolveAttempted;
    private static volatile Object manager;
    private static volatile Method triggerMethod;
    private static volatile Method triggerReverseMethod;
    private static volatile Constructor<?> contextConstructor;
    private static volatile Constructor<?> changeContextConstructor;
    private static volatile Class<?> triggerTypeClass;

    private static final Map<UUID, String> OFFHAND_IDENTITY = new ConcurrentHashMap<>();
    private static final Map<UUID, ShooterDataHolder> OFFHAND_DATA = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> AUTO_SHOOT_ACTIVE = new ConcurrentHashMap<>();

    public ArcanaSkillBridge() {
    }

    private static boolean ensureResolved() {
        if (manager != null) {
            return true;
        }
        if (resolveAttempted) {
            return false;
        }
        synchronized (ArcanaSkillBridge.class) {
            if (manager != null) {
                return true;
            }
            if (resolveAttempted) {
                return false;
            }
            resolveAttempted = true;
            try {
                ClassLoader loader = ArcanaSkillBridge.class.getClassLoader();
                Class<?> managerClass = Class.forName(MANAGER_CLASS, false, loader);
                Class<?> typeClass = Class.forName(TRIGGER_TYPE_CLASS, false, loader);
                Class<?> contextClass = Class.forName(CONTEXT_CLASS, false, loader);
                Object instance = managerClass.getField("oxVomdAx").get(null);
                Method trigger = managerClass.getMethod(METHOD_TRIGGER, typeClass, contextClass, int.class);
                Method reverse = managerClass.getMethod(METHOD_TRIGGER_REVERSE, typeClass, contextClass, int.class);
                Constructor<?> constructor = contextClass.getConstructor(ServerPlayer.class);
                Constructor<?> changeConstructor = Class.forName(CHANGE_CONTEXT_CLASS, false, loader)
                        .getConstructor(ServerPlayer.class, String.class, String.class);
                triggerTypeClass = typeClass;
                triggerMethod = trigger;
                triggerReverseMethod = reverse;
                contextConstructor = constructor;
                changeContextConstructor = changeConstructor;
                manager = instance;
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    /** 配件安装/卸载: 触发 Arcana 的 ON_CHANGE_ATTACHMENT。previousId/newId 可为 null。 */
    public static void triggerChangeAttachment(ServerPlayer player, String previousId, String newId) {
        if (player == null || !ensureResolved() || changeContextConstructor == null) {
            return;
        }
        try {
            Object context = changeContextConstructor.newInstance(player, previousId, newId);
            Object triggerType = Enum.valueOf((Class) triggerTypeClass, TRIGGER_ON_CHANGE_ATTACHMENT);
            triggerMethod.invoke(manager, triggerType, context, -1);
        } catch (Throwable ignored) {
        }
    }

    /** 虚拟配件标记: 写入 Arcana 的 Extras.IsGenerated, 使 Arcana 卸载时不返还该配件。 */
    public static void markGenerated(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
        net.minecraft.nbt.CompoundTag extras = tag.contains("Extras", 10)
                ? tag.getCompound("Extras") : new net.minecraft.nbt.CompoundTag();
        extras.putBoolean("IsGenerated", true);
        tag.put("Extras", extras);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (!ensureResolved()) {
            return;
        }
        UUID uuid = player.getUUID();
        ItemStack offhand = player.getOffhandItem();
        IGun gun = IGun.getIGunOrNull(offhand);
        String identity = null;
        if (gun != null && DualWieldEligibility.isDualWielding(player)) {
            identity = gun.getGunId(offhand) + "@" + DualWieldStackId.getOrCreate(offhand);
        }
        String previous = OFFHAND_IDENTITY.get(uuid);
        ShooterDataHolder data = OffhandShooterManager.findOffhandData(player, offhand);
        if (Objects.equals(previous, identity)) {
            dispatchTickSkills(player, uuid, data);
            return;
        }
        if (previous != null) {
            dispatchOffhand(player, OFFHAND_DATA.get(uuid), TRIGGER_ON_MAIN_HAND, true);
            reverseAutoShoot(player, OFFHAND_DATA.get(uuid), uuid);
        }
        OFFHAND_IDENTITY.remove(uuid);
        OFFHAND_DATA.remove(uuid);
        if (identity == null || data == null) {
            AUTO_SHOOT_ACTIVE.remove(uuid);
            return;
        }
        if (dispatchOffhand(player, data, TRIGGER_ON_MAIN_HAND, false)) {
            OFFHAND_IDENTITY.put(uuid, identity);
            OFFHAND_DATA.put(uuid, data);
        }
        dispatchTickSkills(player, uuid, data);
    }

    private static void dispatchTickSkills(ServerPlayer player, UUID uuid, ShooterDataHolder data) {
        if (data == null) {
            return;
        }
        dispatchOffhand(player, data, TRIGGER_ON_PLAYER_TICK, false);
        if (Boolean.TRUE.equals(AUTO_SHOOT_ACTIVE.get(uuid)) && data.reloadStateType.isReloading()) {
            reverseAutoShoot(player, data, uuid);
        }
    }

    private static void reverseAutoShoot(ServerPlayer player, ShooterDataHolder data, UUID uuid) {
        if (!Boolean.TRUE.equals(AUTO_SHOOT_ACTIVE.get(uuid))) {
            return;
        }
        if (dispatchOffhand(player, data, TRIGGER_ON_AUTO_SHOOT, true)) {
            AUTO_SHOOT_ACTIVE.remove(uuid);
        }
    }

    /** 副手开火键按下/松开: 对应 Arcana 的 ON_CLICK 与 ON_AUTO_SHOOT (可逆)。 */
    public static void onOffhandShootState(ServerPlayer player, boolean down) {
        if (player == null || !ensureResolved()) {
            return;
        }
        UUID uuid = player.getUUID();
        ItemStack offhand = player.getOffhandItem();
        IGun gun = IGun.getIGunOrNull(offhand);
        ShooterDataHolder data = OffhandShooterManager.findOffhandData(player, offhand);
        if (down) {
            if (data == null || gun == null) {
                return;
            }
            dispatchOffhand(player, data, TRIGGER_ON_CLICK, false);
            if (gun.getFireMode(offhand) == FireMode.AUTO && !data.reloadStateType.isReloading()) {
                if (dispatchOffhand(player, data, TRIGGER_ON_AUTO_SHOOT, false)) {
                    AUTO_SHOOT_ACTIVE.put(uuid, Boolean.TRUE);
                }
            }
        } else {
            if (data != null) {
                dispatchOffhand(player, data, TRIGGER_ON_CLICK, true);
            }
            reverseAutoShoot(player, data, uuid);
        }
    }

    /** 副手动作键: 对应 Arcana 的 ON_ACTION_1..4。 */
    public static void triggerOffhandAction(ServerPlayer player, int action) {
        if (player == null || action < 1 || action > 4 || !ensureResolved()) {
            return;
        }
        ShooterDataHolder data = OffhandShooterManager.findOffhandData(player, player.getOffhandItem());
        if (data == null) {
            return;
        }
        dispatchOffhand(player, data, "ON_ACTION_" + action, false);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        OFFHAND_IDENTITY.remove(uuid);
        OFFHAND_DATA.remove(uuid);
        AUTO_SHOOT_ACTIVE.remove(uuid);
    }

    /** 在副手上下文中向 Arcana 派发技能触发。 */
    public static boolean dispatchOffhand(ServerPlayer player, ShooterDataHolder data, String trigger, boolean reverse) {
        if (player == null || !ensureResolved()) {
            return false;
        }
        if (data != null) {
            OffhandShooterManager.pushActiveData(data);
        }
        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object type = Enum.valueOf((Class) triggerTypeClass, trigger);
            Object context = contextConstructor.newInstance(player);
            (reverse ? triggerReverseMethod : triggerMethod).invoke(manager, type, context, Integer.valueOf(-1));
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (data != null) {
                OffhandShooterManager.popActiveData();
            }
        }
    }
}
