package com.ssscript.taczfixes.common.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraftforge.fml.ModList;
import org.apache.maven.artifact.versioning.VersionRange;

/**
 * gunpack.meta.json 中 dependencies 的校验。
 * 与 TaCZ 原逻辑一致(逐个模组检查版本范围), 但只记录问题, 由调用方决定是否继续加载。
 */
public final class GunPackDependencies {

    public enum IssueType {
        /** 前置模组未安装。 */
        MISSING,
        /** 已安装但版本不在声明范围内。 */
        VERSION_MISMATCH
    }

    public record Issue(String modId, String range, IssueType type) {
    }

    private GunPackDependencies() {
    }

    /** 当前是否加载了该模组。 */
    public static boolean isLoaded(String modId) {
        if (modId == null || modId.isBlank()) return false;
        try {
            return ModList.get().getModContainerById(modId).isPresent();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 当前版本是否满足声明的 Maven 版本范围。 */
    public static boolean matches(String modId, String range) {
        if (!isLoaded(modId)) return false;
        if (range == null || range.isBlank()) return true;
        try {
            VersionRange versionRange = VersionRange.createFromVersionSpec(range);
            return ModList.get().getModContainerById(modId)
                    .map(mod -> versionRange.containsVersion(mod.getModInfo().getVersion()))
                    .orElse(false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 当前模组版本字符串, 未安装时为 null。 */
    public static String versionOf(String modId) {
        try {
            return ModList.get().getModContainerById(modId)
                    .map(mod -> mod.getModInfo().getVersion().toString())
                    .orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 收集依赖问题(缺少前置 / 版本范围不符)。 */
    public static List<Issue> check(Map<String, String> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) return List.of();
        List<Issue> issues = new ArrayList<>();
        for (Map.Entry<String, String> entry : dependencies.entrySet()) {
            String modId = entry.getKey();
            if (modId == null || modId.isBlank()) continue;
            String range = entry.getValue();
            if (!isLoaded(modId)) {
                issues.add(new Issue(modId, range, IssueType.MISSING));
            } else if (!matches(modId, range)) {
                issues.add(new Issue(modId, range, IssueType.VERSION_MISMATCH));
            }
        }
        return List.copyOf(issues);
    }
}
