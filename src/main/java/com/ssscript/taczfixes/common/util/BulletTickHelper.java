package com.ssscript.taczfixes.common.util;

import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.resources.ResourceLocation;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 子弹逐 tick Lua 回调入口。 */
public final class BulletTickHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("taczfixes-bullet-tick");

    private BulletTickHelper() {
    }

    /** 执行子弹脚本的 tick_bullet(api 绑定该子弹)。客户端与服务端均执行(两端按同一脚本独立演化)。 */
    public static void runBulletTick(EntityKineticBullet bullet) {
        if (bullet == null) return;
        com.tacz.guns.resource.ICommonResourceProvider provider = CommonAssetsManager.get();
        if (provider == null) return;
        ResourceLocation gunId = bullet.getGunId();
        if (gunId == null) return;
        CommonGunIndex gunIndex = provider.getGunIndex(gunId);
        if (gunIndex == null) return;
        org.luaj.vm2.LuaTable script = gunIndex.getScript();
        if (script == null) return;
        LuaValue func = script.get("tick_bullet");
        if (func == null || func.isnil() || !func.isfunction()) return;
        try {
            func.call(CoerceJavaToLua.coerce(new BulletTickAPI(bullet)));
        } catch (Exception e) {
            LOGGER.warn("Failed to run tick_bullet for gun {}", gunId, e);
        }
    }
}
