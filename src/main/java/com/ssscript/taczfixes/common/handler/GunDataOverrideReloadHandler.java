package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.util.GunDataOverrideStorage;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 服务器进世界(重载 data)前应用 gun data 覆盖, 确保枪包读取后即带最新数据。 */
public class GunDataOverrideReloadHandler {
    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        GunDataOverrideStorage.applyAll();
    }
}
