package com.ssscript.taczfixes.common.compat;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * YSM 双持兼容层的轻量健康检查。
 * <p>1.20.1 版本依靠每 tick 的 {@code LOGGER.debug} 汇报钩子命中情况; 移植到 1.21.1 时去掉了周期性调试日志,
 * 只在“本地玩家第三人称双持并持续渲染若干帧”后做一次性校验: 若关键 mixin 钩子始终没有命中,
 * 说明安装的 YSM 字节码与兼容层不匹配, 此时输出一次性警告, 便于用户排查。
 */
@OnlyIn(Dist.CLIENT)
public final class YsmCompatibilityDiagnostics {
    private static final int VERIFY_AFTER_FRAMES = 100;
    private static final AtomicLong HELD_LAYER_ENTRIES = new AtomicLong();
    private static final AtomicLong PRIMARY_HELPER_CALLS = new AtomicLong();
    private static final AtomicLong PRE_MESH_POSE_HOOKS = new AtomicLong();
    private static final AtomicLong FINAL_POSE_APPLICATIONS = new AtomicLong();
    private static final AtomicLong RPG_POSE_SELECTIONS = new AtomicLong();
    private static final AtomicLong BACK_GUN_HOOKS = new AtomicLong();
    private static final AtomicLong EXTRA_LOCATOR_SUPPRESSIONS = new AtomicLong();
    private static final AtomicBoolean VERIFIED = new AtomicBoolean();
    private static final AtomicBoolean STALE_FRAME_WARNING_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean FRAME_MISMATCH_WARNING_LOGGED = new AtomicBoolean();
    private static int relevantFrames;

    private YsmCompatibilityDiagnostics() {
    }

    public static void markHeldLayerEntry() {
        if (VERIFIED.get()) {
            return;
        }
        HELD_LAYER_ENTRIES.incrementAndGet();
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.getCameraType().isFirstPerson() || !DualWieldEligibility.isDualWielding(player)) {
            relevantFrames = 0;
            return;
        }
        if (++relevantFrames < VERIFY_AFTER_FRAMES || !VERIFIED.compareAndSet(false, true)) {
            return;
        }
        List<String> missingHooks = new ArrayList<>();
        if (PRIMARY_HELPER_CALLS.get() == 0L) {
            missingHooks.add("held-item primary hand redirect");
        }
        if (PRE_MESH_POSE_HOOKS.get() == 0L || FINAL_POSE_APPLICATIONS.get() == 0L) {
            missingHooks.add("pre-mesh arm pose hook");
        }
        if (RPG_POSE_SELECTIONS.get() == 0L) {
            missingHooks.add("TaCZ RPG-pose selector");
        }
        if (!missingHooks.isEmpty()) {
            TaczFixesMod.LOGGER.warn(
                    "YSM 2.6.5 dual-wield compatibility could not observe hooks after {} third-person dual-wield frames: {}. "
                            + "This normally means that the installed YSM bytecode differs from the supported NeoForge 1.21.1 release.",
                    Integer.valueOf(VERIFY_AFTER_FRAMES),
                    String.join(", ", missingHooks));
        }
    }

    public static void markPrimaryHelperCall() {
        PRIMARY_HELPER_CALLS.incrementAndGet();
    }

    public static void markFinalPoseApplied() {
        FINAL_POSE_APPLICATIONS.incrementAndGet();
    }

    public static void markPreMeshPoseHook() {
        PRE_MESH_POSE_HOOKS.incrementAndGet();
    }

    public static void markRpgPoseSelected() {
        RPG_POSE_SELECTIONS.incrementAndGet();
    }

    /** 后置枪械隐藏钩子与额外挂点抑制计数仅用于排查, 不参与自动校验 (没有后置枪械挂点的模型永远不会触发)。 */
    public static void markBackGunHook() {
        BACK_GUN_HOOKS.incrementAndGet();
    }

    public static void markExtraLocatorSuppressed() {
        EXTRA_LOCATOR_SUPPRESSIONS.incrementAndGet();
    }

    public static void reportStaleFrameRecovery() {
        if (STALE_FRAME_WARNING_LOGGED.compareAndSet(false, true)) {
            TaczFixesMod.LOGGER.warn("Recovered a stale YSM held-item render frame left by an interrupted render");
        }
    }

    public static void reportFrameEntityMismatch() {
        if (FRAME_MISMATCH_WARNING_LOGGED.compareAndSet(false, true)) {
            TaczFixesMod.LOGGER.warn("Discarded a mismatched YSM held-item render frame before offhand rendering");
        }
    }
}
