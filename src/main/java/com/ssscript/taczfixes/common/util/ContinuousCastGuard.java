package com.ssscript.taczfixes.common.util;

import com.tacz.guns.api.item.IGun;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** 记录枪械触发的持续施法: 用于区分"开火造成的枪械自身变化"与"切枪/换手", 后者应终止施法。 */
public final class ContinuousCastGuard {

    private static final Map<UUID, Guard> GUARDS = new ConcurrentHashMap<>();

    private ContinuousCastGuard() {
    }

    public record Guard(boolean offhand, int selectedSlot, ResourceLocation gunId) {
    }

    public static void track(ServerPlayer player, boolean offhand, ResourceLocation gunId) {
        if (player == null || gunId == null) {
            return;
        }
        GUARDS.put(player.getUUID(), new Guard(offhand, player.getInventory().selected, gunId));
    }

    public static Guard get(ServerPlayer player) {
        return player == null ? null : GUARDS.get(player.getUUID());
    }

    public static void clear(ServerPlayer player) {
        if (player != null) {
            GUARDS.remove(player.getUUID());
        }
    }

    /** 施法枪械是否仍处于施法时的手/快捷栏, 且仍是同一把枪。 */
    public static boolean matchesCurrent(ServerPlayer player, Guard guard) {
        if (player == null || guard == null) {
            return false;
        }
        if (player.getInventory().selected != guard.selectedSlot()) {
            return false;
        }
        ItemStack held = guard.offhand() ? player.getOffhandItem() : player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(held);
        return gun != null && guard.gunId().equals(gun.getGunId(held));
    }
}
