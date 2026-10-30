package com.ssscript.taczfixes.client.compat.create;

import com.tacz.guns.client.particle.HitSurfaceParticleSpawner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ContraptionHitEffects {
    private static final int MAX_OVERRIDES = 512;
    private static final Map<BlockPos, BlockState> STATE_OVERRIDES =
            new LinkedHashMap<>(MAX_OVERRIDES, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<BlockPos, BlockState> eldest) {
                    return size() > MAX_OVERRIDES;
                }
            };

    private ContraptionHitEffects() {
    }

    public static BlockState overrideState(Level level, BlockPos pos) {
        BlockState override = STATE_OVERRIDES.get(pos);
        return override == null ? level.getBlockState(pos) : override;
    }

    public static void spawn(Vec3 hit, Direction direction, BlockState state,
                             String ammoId, String gunId, String gunDisplayId, boolean incendiary) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        BlockPos pos = BlockPos.containing(hit);
        STATE_OVERRIDES.put(pos, state);
        HitSurfaceParticleSpawner.spawn(level, hit, direction, pos, ammoId, gunId, gunDisplayId, incendiary);
    }
}
