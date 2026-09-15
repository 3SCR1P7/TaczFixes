package com.ssscript.taczfixes.common.compat;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = {Dist.CLIENT})
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/common/compat/YsmCompatibilityDiagnostics.class */
public final class YsmCompatibilityDiagnostics {
    private static final int DIAGNOSTIC_DELAY_TICKS = 100;
    private static final AtomicLong HELD_LAYER_ENTRIES = new AtomicLong();
    private static final AtomicLong PRIMARY_HELPER_CALLS = new AtomicLong();
    private static final AtomicLong FINAL_POSE_APPLICATIONS = new AtomicLong();
    private static final AtomicLong PRE_MESH_POSE_HOOKS = new AtomicLong();
    private static final AtomicLong RPG_POSE_SELECTIONS = new AtomicLong();
    private static final AtomicLong BACK_GUN_HOOKS = new AtomicLong();
    private static final AtomicLong EXTRA_LOCATOR_SUPPRESSIONS = new AtomicLong();
    private static final AtomicBoolean DIAGNOSTIC_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean STALE_FRAME_WARNING_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean FRAME_MISMATCH_WARNING_LOGGED = new AtomicBoolean();
    private static int relevantThirdPersonTicks;

    private YsmCompatibilityDiagnostics() {
    }

    public static void markHeldLayerEntry() {
        HELD_LAYER_ENTRIES.incrementAndGet();
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

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ModList.get().isLoaded("yes_steve_model")) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.getCameraType().isFirstPerson() || !DualWieldEligibility.isDualWielding(player)) {
            relevantThirdPersonTicks = 0;
            return;
        }
        relevantThirdPersonTicks++;
        if (relevantThirdPersonTicks < DIAGNOSTIC_DELAY_TICKS || !DIAGNOSTIC_LOGGED.compareAndSet(false, true)) {
            return;
        }
        List<String> missingHooks = new ArrayList<>();
        if (HELD_LAYER_ENTRIES.get() == 0) {
            missingHooks.add("held-layer entry");
        }
        if (PRIMARY_HELPER_CALLS.get() == 0) {
            missingHooks.add("primary hand helper redirect");
        }
        if (FINAL_POSE_APPLICATIONS.get() == 0 || PRE_MESH_POSE_HOOKS.get() == 0) {
            missingHooks.add("pre-mesh arm pose hook");
        }
        if (RPG_POSE_SELECTIONS.get() == 0) {
            missingHooks.add("TaCZ RPG-pose selector");
        }
        if (BACK_GUN_HOOKS.get() == 0) {
            missingHooks.add("TaCZ back-gun suppression");
        }
        if (!missingHooks.isEmpty()) {
            TaczFixesMod.LOGGER.warn("YSM 2.6.5 dual-wield compatibility could not observe hooks after {} ticks: {}. This normally means that the installed YSM bytecode differs from the supported Forge 1.20.1 release.", Integer.valueOf(DIAGNOSTIC_DELAY_TICKS), String.join(", ", missingHooks));
        } else {
            TaczFixesMod.LOGGER.debug("YSM 2.6.5 dual-wield hooks verified; extra locator suppressions={}", Long.valueOf(EXTRA_LOCATOR_SUPPRESSIONS.get()));
        }
    }
}
