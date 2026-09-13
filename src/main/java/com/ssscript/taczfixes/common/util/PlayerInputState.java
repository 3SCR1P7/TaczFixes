package com.ssscript.taczfixes.common.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 客户端同步到服务端的玩家按键状态(供服务端 Lua getInput() 读取)。 */
public final class PlayerInputState {

    private static final Map<UUID, Set<String>> STATES = new HashMap<>();

    private PlayerInputState() {
    }

    /** 更新某玩家当前按下的按键集(空集表示清除/无按键)。 */
    public static synchronized void set(UUID uuid, Set<String> keys) {
        if (uuid == null) return;
        if (keys == null || keys.isEmpty()) {
            STATES.remove(uuid);
        } else {
            STATES.put(uuid, Collections.unmodifiableSet(new HashSet<>(keys)));
        }
    }

    /** 获取某玩家同步的按键集, 未同步过返回 null。 */
    public static synchronized Set<String> get(UUID uuid) {
        return uuid == null ? null : STATES.get(uuid);
    }

    public static synchronized void remove(UUID uuid) {
        if (uuid == null) return;
        STATES.remove(uuid);
    }
}
