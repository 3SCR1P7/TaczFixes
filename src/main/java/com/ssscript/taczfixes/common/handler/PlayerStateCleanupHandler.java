package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.util.DecapitationHelper;
import com.ssscript.taczfixes.common.util.GunBlocking;
import com.ssscript.taczfixes.common.util.GunShieldHelper;
import com.ssscript.taczfixes.common.util.PatienceHelper;
import com.ssscript.taczfixes.common.util.ReloadExtraTracker;
import com.ssscript.taczfixes.common.util.SpreadState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/** 玩家退出时清理各工具类的按玩家(UUID)状态, 避免长期运行的服务端内存泄漏。 */
public class PlayerStateCleanupHandler {
    /** 服务端每 tick 刷新 blocking 平滑因子(子弹使用, 与客户端模型同源)。 */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        GunBlocking.tick(event.player);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        SpreadState.clear(id);
        GunShieldHelper.clear(id);
        PatienceHelper.clear(id);
        ReloadExtraTracker.clear(id);
        DecapitationHelper.clear(id);
        GunBlocking.clearState(id);
        if (net.minecraftforge.fml.ModList.get().isLoaded("irons_spellbooks")) {
            try {
                Class.forName("com.ssscript.taczfixes.common.handler.GunSpellHandler")
                        .getMethod("clear", UUID.class)
                        .invoke(null, id);
            } catch (Throwable ignored) {
            }
        }
    }
}
