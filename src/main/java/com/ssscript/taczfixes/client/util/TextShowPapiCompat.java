package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.common.util.TextShowStorage;
import com.tacz.guns.client.model.papi.PapiManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 把 textShow 设置的占位符注册到 TACZ 的 PapiManager。 */
@OnlyIn(Dist.CLIENT)
public final class TextShowPapiCompat {
    private static final Set<String> REGISTERED = ConcurrentHashMap.newKeySet();

    private TextShowPapiCompat() {
    }

    public static void register(String name) {
        if (name == null || name.isEmpty() || !REGISTERED.add(name)) {
            return;
        }
        try {
            PapiManager.addPapi(name, stack -> TextShowStorage.get(stack, name));
        } catch (Throwable ignored) {
        }
    }
}
