package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.data.GunPackDependencies;
import com.ssscript.taczfixes.common.data.GunPackHooks;
import com.ssscript.taczfixes.common.data.GunPackIds;
import com.ssscript.taczfixes.common.data.GunPackSettings;
import com.tacz.guns.resource.GunPackLoader;
import com.tacz.guns.resource.PackMeta;
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
    /** 已经警告过的“枪包+依赖问题”, 避免每次资源重载都刷日志。 */
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
     * 枪包在 gunpack.meta.json 里用 dependencies 声明依赖的模组版本范围。
     * 原版 TaCZ 遇到不匹配会直接跳过整个枪包; 这里改为“只警告、继续加载”,
     * 覆盖 TaCZ 版本不符、前置模组未安装、其它模组版本不符三种情况。
     * 依赖有问题的枪包仍会出现在枪包管理页面, 并像原版“不兼容资源包”一样标红。
     */
    @Inject(method = "modVersionAllMatch", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$tolerateDependencyMismatch(PackMeta info,
                                                             CallbackInfoReturnable<Boolean> cir) {
        HashMap<String, String> dependencies;
        try {
            dependencies = info.getDependencies();
        } catch (Throwable ignored) {
            return;
        }
        if (dependencies == null || dependencies.isEmpty()) return;

        List<GunPackDependencies.Issue> issues;
        try {
            issues = GunPackDependencies.check(dependencies);
        } catch (Throwable ignored) {
            return;
        }
        // 全部匹配则交回 TaCZ 原逻辑
        if (issues.isEmpty()) return;

        String namespace = "?";
        try {
            String name = info.getName();
            if (name != null && !name.isBlank()) namespace = name;
        } catch (Throwable ignored) {
        }
        for (GunPackDependencies.Issue issue : issues) {
            String key = namespace + "|" + issue.modId() + "|" + issue.range();
            if (!TACZFIXES$WARNED.add(key)) continue;
            if (issue.type() == GunPackDependencies.IssueType.MISSING) {
                TaczFixesMod.LOGGER.warn("gunspack '{}' requires mod '{}', which is not installed: loading anyway.",
                        namespace, issue.modId());
            } else {
                TaczFixesMod.LOGGER.warn("gunspack '{}' needs {} {}, but current version is {}: loading anyway.",
                        namespace, issue.modId(), issue.range(), GunPackDependencies.versionOf(issue.modId()));
            }
        }
        cir.setReturnValue(true);
    }
}
