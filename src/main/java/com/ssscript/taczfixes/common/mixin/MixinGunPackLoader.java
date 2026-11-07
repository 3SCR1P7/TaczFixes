package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.data.GunPackHooks;
import com.ssscript.taczfixes.common.data.GunPackIds;
import com.ssscript.taczfixes.common.data.GunPackSettings;
import com.tacz.guns.resource.GunPackLoader;
import com.tacz.guns.resource.PackMeta;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 按本模组的枪包设置过滤/排序 TaCZ 扫描到的枪包(顺序即资源覆盖优先级, 越前越优先)。 */
@Mixin(value = GunPackLoader.class, remap = false)
public abstract class MixinGunPackLoader {
    /** 已经警告过的“枪包+版本要求”, 避免每次资源重载都刷日志。 */
    @Unique
    private static final Set<String> TACZFIXES$WARNED = ConcurrentHashMap.newKeySet();

    @Inject(method = "scanExtensions", at = @At("RETURN"), cancellable = true, remap = false)
    private static void taczfixes$applyGunPackSettings(Path path, CallbackInfoReturnable<List<?>> cir) {
        if (Boolean.TRUE.equals(GunPackHooks.RAW_SCAN.get())) return;
        List<?> packList = cir.getReturnValue();
        if (packList == null || packList.isEmpty()) return;
        Map<String, List<Object>> byId = new LinkedHashMap<>();
        for (Object pack : packList) {
            String name = GunPackHooks.nameOf(pack);
            Path packPath = GunPackHooks.pathOf(pack);
            if (name != null && packPath != null) {
                String id = GunPackIds.of(name, packPath);
                byId.computeIfAbsent(id, key -> new ArrayList<>()).add(pack);
            }
        }
        List<String> arranged = GunPackSettings.arrange(new ArrayList<>(byId.keySet()));
        List<Object> result = new ArrayList<>();
        for (String id : arranged) {
            List<Object> packs = byId.get(id);
            if (packs != null) result.addAll(packs);
        }
        cir.setReturnValue(result);
    }

    /**
     * 枪包在 gunpack.meta.json 里用 dependencies 声明依赖的 TaCZ 版本范围。
     * 原版 TaCZ 遇到不匹配会直接跳过整个枪包; 这里改为“只警告、继续加载”(仅针对 tacz 依赖),
     * 其它模组依赖仍按原逻辑检查, 避免装错包。
     */
    @Inject(method = "modVersionAllMatch", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$warnOnlyTaczVersion(PackMeta info,
                                                      CallbackInfoReturnable<Boolean> cir) {
        HashMap<String, String> dependencies;
        try {
            dependencies = info.getDependencies();
        } catch (Throwable ignored) {
            return;
        }
        if (dependencies == null || dependencies.isEmpty()) return;

        String taczRange = null;
        for (Map.Entry<String, String> entry : dependencies.entrySet()) {
            String modId = entry.getKey();
            String range = entry.getValue();
            if (modId == null) continue;
            boolean matches = taczfixes$modVersionMatches(modId, range);
            if ("tacz".equalsIgnoreCase(modId)) {
                if (!matches) {
                    taczRange = range == null ? "" : range;
                }
            } else if (!matches) {
                // 其它模组的依赖不匹配: 保持 TaCZ 原逻辑(跳过该枪包)
                return;
            }
        }
        if (taczRange == null) return;

        String namespace = "?";
        try {
            String name = info.getName();
            if (name != null && !name.isBlank()) namespace = name;
        } catch (Throwable ignored) {
        }
        String currentVersion = "?";
        try {
            currentVersion = ModList.get().getModContainerById("tacz")
                    .map(mod -> mod.getModInfo().getVersion().toString())
                    .orElse("?");
        } catch (Throwable ignored) {
        }
        String key = namespace + "|" + taczRange;
        if (TACZFIXES$WARNED.add(key)) {
            TaczFixesMod.LOGGER.warn("Gun pack '{}' requires TaCZ {} but current version is {}: version mismatch, warning only and continuing to load",
                    namespace, taczRange, currentVersion);
        }
        cir.setReturnValue(true);
    }

    @Unique
    private static boolean taczfixes$modVersionMatches(String modId, String spec) {
        try {
            org.apache.maven.artifact.versioning.VersionRange range =
                    org.apache.maven.artifact.versioning.VersionRange.createFromVersionSpec(spec);
            return ModList.get().getModContainerById(modId)
                    .map(mod -> range.containsVersion(mod.getModInfo().getVersion()))
                    .orElse(false);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
