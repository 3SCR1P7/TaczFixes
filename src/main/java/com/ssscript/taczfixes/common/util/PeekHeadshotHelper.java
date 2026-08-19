package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.Config;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mods.gd656peek.PeekHitboxGeometry;
import org.mods.gd656peek.PeekHitboxGeometry.HitboxSet;
import org.mods.gd656peek.PeekHitboxGeometry.OrientedBox;
import org.mods.gd656peek.compat.tacz.TaczPeekHitboxHelper;
import org.mods.gd656peek.server.PeekServerRuntime;

public final class PeekHeadshotHelper {
    private static final Logger LOGGER = LogManager.getLogger("taczfixes");
    private static final boolean GD656PEEK_PRESENT = checkGd656PeekPresent();

    private PeekHeadshotHelper() {
    }

    private static boolean checkGd656PeekPresent() {
        try {
            Class.forName("org.mods.gd656peek.compat.tacz.TaczPeekHitboxHelper");
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
    }

    public static boolean shouldDemoteToBodyShot(Entity entity, Vec3 hitPos) {
        if (!GD656PEEK_PRESENT) {
            return false;
        }
        if (!(entity instanceof ServerPlayer player)) {
            return false;
        }
        try {
            if (!TaczPeekHitboxHelper.shouldUseCustomHitbox(player)) {
                return false;
            }
            float angle = PeekServerRuntime.getCurrentAngle(player);
            float offset = PeekServerRuntime.getCurrentOffset(player);
            HitboxSet set = PeekHitboxGeometry.build(player, angle, offset);
            OrientedBox upper = set.upperBody();
            Vec3 center = upper.center();
            Vec3 axisUp = upper.axisUp();
            double localUp = hitPos.subtract(center).dot(axisUp);
            double headshotHeight = Config.PEEK_HEADSHOT_HEIGHT.get();
            double limit = upper.halfUp() - headshotHeight;
            return localUp < limit;
        } catch (Throwable t) {
            LOGGER.error("taczfixes peek-headshot: geometry check failed", t);
            return false;
        }
    }
}
