package com.ssscript.taczfixes.client.hud;

import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 自定义 HUD 解析。custom_hud 只接受 ApricityUI HTML 页面:
 * {@code assets/<ns>/hud/<path>.html}。
 *
 * <p>ApricityUI 未安装时该字段完全无效, 直接使用原版 HUD。旧 JSON 格式已移除。</p>
 */
public final class CustomHudManager {

    public enum Kind {
        NONE,
        APRICITY
    }

    /** 当前主手枪与自定义 HUD 的解析结果。 */
    public static final class ActiveGun {
        public final LocalPlayer player;
        public final ItemStack stack;
        public final IGun gun;
        public final ResourceLocation gunId;
        public final ResourceLocation hudId;
        public final String config;

        private ActiveGun(LocalPlayer player, ItemStack stack, IGun gun,
                          ResourceLocation gunId, ResourceLocation hudId, String config) {
            this.player = player;
            this.stack = stack;
            this.gun = gun;
            this.gunId = gunId;
            this.hudId = hudId;
            this.config = config;
        }

        public String key() {
            return gunId + "|" + config;
        }
    }

    private static final Map<ResourceLocation, Boolean> HTML_EXISTS = new HashMap<>();

    private static volatile boolean apricityRendering;
    private static volatile boolean apricityFailed;

    private CustomHudManager() {
    }

    /** 当前主手配置了自定义 HUD 的枪械, 未配置/旧格式/AUI 未安装返回 null。 */
    public static ActiveGun activeGun() {
        if (!ApricityHudSupport.isAvailable()) return null;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;
        ItemStack stack = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return null;
        ResourceLocation gunId = gun.getGunId(stack);
        if (gunId == null) return null;
        ResourceLocation dataId = TaczFixesDataManager.resolveDataId(gunId);
        GunTaczFixesData data = dataId == null ? null : TaczFixesDataManager.get(dataId);
        if (data == null || data.custom_hud == null || data.custom_hud.isBlank()) return null;
        String config = data.custom_hud.trim();
        ResourceLocation hudId = parseHudId(config);
        if (hudId == null) return null;
        return new ActiveGun(player, stack, gun, gunId, hudId, config);
    }

    /** 只有 ApricityUI 已安装且对应 HTML 存在时才启用 AUI HUD。 */
    public static Kind kindFor(ActiveGun active) {
        if (active == null) return Kind.NONE;
        if (!ApricityHudSupport.isAvailable()) return Kind.NONE;
        return hasHtml(active.hudId) ? Kind.APRICITY : Kind.NONE;
    }

    public static boolean isApricityRendering() {
        return apricityRendering;
    }

    public static void setApricityRendering(boolean rendering) {
        apricityRendering = rendering;
    }

    public static boolean isApricityFailed() {
        return apricityFailed;
    }

    public static void setApricityFailed(boolean failed) {
        apricityFailed = failed;
    }

    public static void clear() {
        HTML_EXISTS.clear();
        apricityRendering = false;
        apricityFailed = false;
    }

    private static boolean hasHtml(ResourceLocation id) {
        return HTML_EXISTS.computeIfAbsent(id, key -> GunPackFiles.exists(relativeHtml(key)));
    }

    static String relativeHtml(ResourceLocation id) {
        return "assets/" + id.getNamespace() + "/hud/" + id.getPath() + ".html";
    }

    /**
     * 解析 custom_hud:
     * <ul>
     *     <li>{@code namespace:name} → {@code assets/namespace/hud/name.html}</li>
     *     <li>{@code namespace:hud/name.html} / {@code namespace:name.html} → 同上</li>
     *     <li>无命名空间时按原版 {@link ResourceLocation#tryParse} 规则使用 minecraft</li>
     *     <li>旧 {@code .json} 写法返回 null, 不再生效</li>
     * </ul>
     */
    public static ResourceLocation parseHudId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        if (value.toLowerCase(Locale.ROOT).endsWith(".json")) return null;
        int colon = value.indexOf(':');
        String namespace = colon > 0 ? value.substring(0, colon).trim() : "minecraft";
        String path = colon > 0 ? value.substring(colon + 1).trim() : value;
        if (path.toLowerCase(Locale.ROOT).endsWith(".html")) {
            path = path.substring(0, path.length() - ".html".length());
        }
        if (path.startsWith("hud/")) {
            path = path.substring("hud/".length());
        }
        if (namespace.isEmpty() || path.isEmpty()) return null;
        return ResourceLocation.tryParse(namespace + ":" + path);
    }
}
