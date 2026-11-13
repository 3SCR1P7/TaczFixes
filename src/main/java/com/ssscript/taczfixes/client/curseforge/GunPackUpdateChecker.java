package com.ssscript.taczfixes.client.curseforge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 后台检查“从 CurseForge 安装过的枪包”是否有新版本。
 * 启动时强制刷新文件列表检查一次; 打开枪包管理界面时, 若本次启动还没有完成检查或距上次检查超过 30 分钟再补查一次。
 * 检查过程完全安静, 失败/断网/无记录都不报错、不写日志。
 */
public final class GunPackUpdateChecker {
    /** 两次检查之间的最小间隔。 */
    private static final long MIN_CHECK_INTERVAL_MS = 30L * 60L * 1000L;
    private static final Object LOCK = new Object();

    public record Update(CurseForgeInstalledPacks.Installed installed, CurseForgeApi.FileInfo latest) {
    }

    private static volatile Map<Long, Update> updatesByModId = Map.of();
    private static volatile boolean checking;
    private static volatile boolean checked;
    private static volatile long lastCheckAt;

    private GunPackUpdateChecker() {
    }

    /** 启动时调用: 绕过 45 分钟磁盘缓存, 拿到最新远端文件列表。 */
    public static void checkOnStartupAsync() {
        checkAsync(true);
    }

    /** 打开管理界面时调用: 本次启动已检查且未超过 30 分钟时跳过。 */
    public static void checkAsync() {
        checkAsync(false);
    }

    private static void checkAsync(boolean forceRefresh) {
        final List<CurseForgeInstalledPacks.Installed> installed;
        synchronized (LOCK) {
            if (checking) return;
            long now = System.currentTimeMillis();
            if (!forceRefresh && checked && now - lastCheckAt < MIN_CHECK_INTERVAL_MS) return;
            installed = CurseForgeInstalledPacks.all();
            if (installed.isEmpty()) {
                updatesByModId = Map.of();
                checked = true;
                lastCheckAt = now;
                return;
            }
            checking = true;
        }
        List<CompletableFuture<Update>> futures = new ArrayList<>(installed.size());
        try {
            for (CurseForgeInstalledPacks.Installed entry : installed) {
                futures.add(CurseForgeApi.listFiles(entry.modId(), true)
                        .thenApply(files -> findUpdate(entry, CurseForgeApi.pickLatestGunPackFile(files)))
                        // 单个枪包检查失败不影响其它枪包, 也不向玩家报错
                        .exceptionally(error -> null));
            }
        } catch (Throwable ignored) {
            synchronized (LOCK) {
                checking = false;
                checked = true;
                lastCheckAt = System.currentTimeMillis();
            }
            return;
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).whenComplete((ignored, error) -> {
            Map<Long, Update> found = new HashMap<>();
            for (CompletableFuture<Update> future : futures) {
                Update update = future.getNow(null);
                if (update != null) found.put(update.installed().modId(), update);
            }
            updatesByModId = Map.copyOf(found);
            synchronized (LOCK) {
                checking = false;
                checked = true;
                lastCheckAt = System.currentTimeMillis();
            }
        });
    }

    private static Update findUpdate(CurseForgeInstalledPacks.Installed entry, CurseForgeApi.FileInfo latest) {
        if (latest == null || latest.fileId() <= 0L || latest.fileId() == entry.fileId()) {
            return null;
        }
        // 只有确实比已安装文件更新时才提示, 避免把“回退到旧正式版”当成更新
        if (entry.fileDate() > 0L && latest.date() > 0L && latest.date() <= entry.fileDate()) {
            return null;
        }
        return new Update(entry, latest);
    }

    /** 当前安装记录是否有可用更新。 */
    public static Update updateFor(CurseForgeInstalledPacks.Installed installed) {
        if (installed == null) return null;
        return updatesByModId.get(installed.modId());
    }

    /** 更新完成后从列表移除对应条目。 */
    public static void forget(long modId) {
        Update removed = updatesByModId.get(modId);
        if (removed == null) return;
        Map<Long, Update> remaining = new HashMap<>(updatesByModId);
        remaining.remove(modId);
        updatesByModId = Map.copyOf(remaining);
    }

    /** 新安装/更新后让下一次打开管理界面时重新检查。 */
    public static void invalidate() {
        lastCheckAt = 0L;
    }
}
