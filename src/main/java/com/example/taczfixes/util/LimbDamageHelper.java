package com.example.taczfixes.util;

import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public class LimbDamageHelper {
    private static final Map<Integer, Vec3> HIT_POSITIONS = new HashMap<>();

    public static void storeHitPosition(int bulletId, Vec3 position) {
        HIT_POSITIONS.put(bulletId, position);
    }

    public static Vec3 getHitPosition(int bulletId) {
        return HIT_POSITIONS.remove(bulletId);
    }
}
