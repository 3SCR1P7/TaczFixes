package com.ssscript.taczfixes.client.util;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;


import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GunTransparency {
    private static final Map<ResourceLocation, Boolean> CACHE = new ConcurrentHashMap<>();

    private GunTransparency() {
    }

    /** 资源重载后清空缓存: 新贴图的 alpha 可能变化, 旧的 true/false(含异常时缓存的 false)必须失效。 */
    public static void clear() {
        CACHE.clear();
    }

    public static boolean needsTranslucent(ResourceLocation texture) {
        if (texture == null) {
            return false;
        }
        return CACHE.computeIfAbsent(texture, GunTransparency::scan);
    }

    private static boolean scan(ResourceLocation texture) {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
            if (resource.isEmpty()) {
                return false;
            }
            try (InputStream input = resource.get().open();
                 NativeImage image = NativeImage.read(input)) {
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int alpha = image.getPixelRGBA(x, y) >>> 24;
                        if (alpha != 0 && alpha != 255) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
