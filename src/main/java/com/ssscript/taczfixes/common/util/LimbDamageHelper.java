package com.ssscript.taczfixes.common.util;

import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public class LimbDamageHelper {
    private static final Map<Integer, Vec3> HIT_POSITIONS = new HashMap<>();
    private static final Map<Integer, CrawlZoneHit> CRAWL_ZONES = new HashMap<>();

    public static void storeHitPosition(int bulletId, Vec3 position) {
        HIT_POSITIONS.put(bulletId, position);
    }

    public static Vec3 getHitPosition(int bulletId) {
        return HIT_POSITIONS.remove(bulletId);
    }

    public static void storeCrawlZone(int bulletId, int entityId, CrawlHitboxHelper.Zone zone) {
        CRAWL_ZONES.put(bulletId, new CrawlZoneHit(entityId, zone));
    }

    /** 取出并移除指定子弹对指定实体的爬行受击箱分区, 不匹配或不存在时返回 null。 */
    public static CrawlHitboxHelper.Zone getCrawlZone(int bulletId, int entityId) {
        CrawlZoneHit hit = CRAWL_ZONES.remove(bulletId);
        if (hit == null || hit.entityId() != entityId) {
            return null;
        }
        return hit.zone();
    }

    private record CrawlZoneHit(int entityId, CrawlHitboxHelper.Zone zone) {
    }
}
