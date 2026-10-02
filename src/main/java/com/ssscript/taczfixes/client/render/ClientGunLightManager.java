package com.ssscript.taczfixes.client.render;

import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.util.GunLightColor;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 纯客户端动态光照: 开火/曳光弹/爆炸按枪械 data 的 light 字段产生光照。
 * 无颜色光走原版方块光照引擎; 彩色光直接注册到 Shimmer 的 LightManager(ColorPointLight),
 * 由 Shimmer 的彩色光照着色管线真实照亮方块/实体/粒子。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientGunLightManager {
    private static final Map<Long, ActiveLight> LIGHTS = new ConcurrentHashMap<>();
    private static final Map<Integer, TrackedBullet> BULLETS = new ConcurrentHashMap<>();

    private ClientGunLightManager() {
    }

    private static final class ActiveLight {
        int color;
        long startMillis;
        long endMillis;
        int levelMax;
        int levelMin;
        Vec3 position;
        ColorPointLight shimmerLight;
        volatile int current;

        ActiveLight(long startMillis, long endMillis, int levelMax, int levelMin, int current, int color, Vec3 position) {
            this.startMillis = startMillis;
            this.endMillis = endMillis;
            this.levelMax = levelMax;
            this.levelMin = levelMin;
            this.current = current;
            this.color = color;
            this.position = position;
        }

        void refresh(Vec3 newPosition, int newColor, int newLevelMax, int newLevelMin, long newStart, long newEnd) {
            this.position = newPosition;
            this.color = newColor;
            this.levelMax = newLevelMax;
            this.levelMin = newLevelMin;
            this.startMillis = newStart;
            this.endMillis = newEnd;
            this.current = newLevelMax;
        }
    }

    private static final class TrackedBullet {
        final String gunId;
        final Vec3 spawnPos;
        final GunTaczFixesData.LightEntry bullet;
        volatile Vec3 lastPos;
        volatile int ticks;

        TrackedBullet(String gunId, Vec3 spawnPos, GunTaczFixesData.LightEntry bullet) {
            this.gunId = gunId;
            this.spawnPos = spawnPos;
            this.bullet = bullet;
        }
    }

    public static void add(Vec3 position, GunTaczFixesData.LightEntry entry) {
        if (position == null) {
            return;
        }
        addInternal(BlockPos.containing(position), position, entry, false);
    }

    public static void add(BlockPos pos, GunTaczFixesData.LightEntry entry) {
        if (pos == null) {
            return;
        }
        addInternal(pos, Vec3.atCenterOf(pos), entry, false);
    }

    private static void addInternal(BlockPos pos, Vec3 position, GunTaczFixesData.LightEntry entry, boolean evictOldest) {
        if (pos == null || position == null || entry == null) {
            return;
        }
        Integer time = entry.time;
        if (time == null || time <= 0) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.hasChunkAt(pos)) {
            return;
        }
        long key = pos.asLong();
        ActiveLight existing = LIGHTS.get(key);
        if (existing == null && LIGHTS.size() >= com.ssscript.taczfixes.common.config.Config.GUN_LIGHT_MAX_LIGHTS.get()) {
            if (!evictOldest || !evictOldest(level)) {
                return;
            }
        }
        int levelMax = Mth.clamp(entry.level_max == null ? 15 : entry.level_max, 0, 15);
        int levelMin = Mth.clamp(entry.level_min == null ? 0 : entry.level_min, 0, 15);
        int color = GunLightColor.parse(entry.color);
        long now = System.currentTimeMillis();
        if (existing != null) {
            existing.refresh(position, color, levelMax, levelMin, now, now + time);
            if (existing.shimmerLight == null) {
                existing.shimmerLight = createShimmerLight(position, color, levelMax, levelMax);
            } else {
                updateShimmerLight(existing.shimmerLight, position, color, levelMax, levelMax);
            }
        } else {
            ActiveLight light = new ActiveLight(now, now + time, levelMax, levelMin, levelMax, color, position);
            light.shimmerLight = createShimmerLight(position, color, levelMax, levelMax);
            LIGHTS.put(key, light);
        }
        if (color < 0) {
            relight(level, pos);
        }
    }

    private static boolean evictOldest(ClientLevel level) {
        Long oldestKey = null;
        long oldestEnd = Long.MAX_VALUE;
        for (Map.Entry<Long, ActiveLight> entry : LIGHTS.entrySet()) {
            if (entry.getValue().endMillis < oldestEnd) {
                oldestEnd = entry.getValue().endMillis;
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey == null) {
            return false;
        }
        removeLight(level, oldestKey, BlockPos.of(oldestKey));
        return true;
    }

    /** 服务端命中/销毁同步包: 爆炸光或回程 bullet 线段光(参数由服务端下发, 不依赖客户端数据)。 */
    public static void onLightPacket(boolean explosion, int time, int levelMax, int levelMin, int color, Vec3 from, Vec3 to) {
        if (time <= 0) {
            return;
        }
        if (explosion) {
            addRaw(BlockPos.containing(to), time, levelMax, levelMin, color);
            return;
        }
        addSegmentLightsRaw(from, to, time, levelMax, levelMin, color);
    }

    /** 直接用给定参数添加光照(服务端同步用); 满额时优先挤掉最早到期的光源。 */
    public static void addRaw(BlockPos pos, int time, int levelMax, int levelMin, int color) {
        if (pos == null || time <= 0) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.hasChunkAt(pos)) {
            return;
        }
        long key = pos.asLong();
        if (!LIGHTS.containsKey(key) && LIGHTS.size() >= com.ssscript.taczfixes.common.config.Config.GUN_LIGHT_MAX_LIGHTS.get()) {
            if (!evictOldest(level)) {
                return;
            }
        }
        int clampedMax = Mth.clamp(levelMax, 0, 15);
        int clampedMin = Mth.clamp(levelMin, 0, 15);
        Vec3 position = Vec3.atCenterOf(pos);
        long now = System.currentTimeMillis();
        ActiveLight existing = LIGHTS.get(key);
        if (existing != null) {
            existing.refresh(position, color, clampedMax, clampedMin, now, now + time);
            if (existing.shimmerLight == null) {
                existing.shimmerLight = createShimmerLight(position, color, clampedMax, clampedMax);
            } else {
                updateShimmerLight(existing.shimmerLight, position, color, clampedMax, clampedMax);
            }
        } else {
            ActiveLight light = new ActiveLight(now, now + time, clampedMax, clampedMin, clampedMax, color, position);
            light.shimmerLight = createShimmerLight(position, color, clampedMax, clampedMax);
            LIGHTS.put(key, light);
        }
        if (color < 0) {
            relight(level, pos);
        }
    }

    /** 线段光照(服务端同步用)。 */
    public static void addSegmentLightsRaw(Vec3 from, Vec3 to, int time, int levelMax, int levelMin, int color) {
        if (from == null || to == null || time <= 0) {
            return;
        }
        double distance = from.distanceTo(to);
        if (distance < 1.0E-6) {
            addRaw(BlockPos.containing(to), time, levelMax, levelMin, color);
            return;
        }
        int steps = Math.max(1, (int) Math.ceil(distance * 2.0d));
        BlockPos previous = null;
        List<BlockPos> visited = new ArrayList<>();
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            BlockPos pos = BlockPos.containing(Mth.lerp(t, from.x, to.x), Mth.lerp(t, from.y, to.y), Mth.lerp(t, from.z, to.z));
            if (pos.equals(previous) || visited.contains(pos)) {
                continue;
            }
            previous = pos;
            visited.add(pos);
            addRaw(pos, time, levelMax, levelMin, color);
        }
    }

    /** 客户端子弹每 tick: 沿线生成光照并记录轨迹。 */
    public static void bulletLight(EntityKineticBullet bullet) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        int id = bullet.getId();
        TrackedBullet tracked = BULLETS.get(id);
        if (tracked == null) {
            GunTaczFixesData.LightConfig config;
            if (bullet instanceof com.ssscript.taczfixes.common.util.LightBulletAccess access
                    && access.taczfixes$isLightCaptured()) {
                config = access.taczfixes$getLightConfig();
            } else {
                config = TaczFixesDataManager.resolveLight(bullet.getGunId());
            }
            if (config == null || config.bullet == null || config.bullet.time == null || config.bullet.time <= 0) {
                return;
            }
            tracked = new TrackedBullet(bullet.getGunId().toString(), new Vec3(bullet.xo, bullet.yo, bullet.zo), config.bullet);
            BULLETS.put(id, tracked);
        }
        tracked.lastPos = bullet.position();
        tracked.ticks++;
        addSegmentLights(new Vec3(bullet.xo, bullet.yo, bullet.zo), bullet.position(), tracked.bullet);
    }

    /** 光照引擎查询入口: 方块位置当前的无颜色光照等级(彩色光由 Shimmer 渲染, 不参与原版光照引擎)。 */
    public static int emissionAt(long packedPos) {
        ActiveLight light = LIGHTS.get(packedPos);
        return light == null || light.color >= 0 ? 0 : light.current;
    }

    public static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            clear();
            return;
        }
        long now = System.currentTimeMillis();
        List<BlockPos> changed = new ArrayList<>();
        for (Map.Entry<Long, ActiveLight> entry : LIGHTS.entrySet()) {
            ActiveLight light = entry.getValue();
            if (now >= light.endMillis) {
                removeLight(level, entry.getKey(), BlockPos.of(entry.getKey()));
                continue;
            }
            float progress = (float) (now - light.startMillis) / (float) (light.endMillis - light.startMillis);
            int target = Mth.clamp(Math.round(Mth.lerp(progress, light.levelMax, light.levelMin)), 0, 15);
            if (light.shimmerLight != null) {
                updateShimmerLight(light.shimmerLight, light.position, light.color, light.levelMax, target);
                light.current = target;
                continue;
            }
            if (target != light.current) {
                light.current = target;
                changed.add(BlockPos.of(entry.getKey()));
            }
        }
        for (BlockPos pos : changed) {
            relight(level, pos);
        }

        // 只是清理已经不在客户端的子弹轨迹记录(不产生光源)
        BULLETS.entrySet().removeIf(entry -> {
            Entity entity = level.getEntity(entry.getKey());
            return !(entity instanceof EntityKineticBullet) || !entity.isAlive();
        });
    }

    /** 区块卸载: 该区块内的光照与子弹轨迹直接移除(区块已不加载)。 */
    public static void removeInChunk(net.minecraft.world.level.ChunkPos chunkPos) {
        List<Long> removed = new ArrayList<>();
        for (Map.Entry<Long, ActiveLight> entry : LIGHTS.entrySet()) {
            if (new net.minecraft.world.level.ChunkPos(entry.getKey()).equals(chunkPos)) {
                removed.add(entry.getKey());
                removeShimmerLight(entry.getValue().shimmerLight);
            }
        }
        LIGHTS.keySet().removeAll(removed);
        BULLETS.values().removeIf(tracked -> new net.minecraft.world.level.ChunkPos(
                BlockPos.containing(tracked.lastPos == null ? tracked.spawnPos : tracked.lastPos)).equals(chunkPos));
    }

    /** 世界卸载/退出: 光照引擎随之重建, 直接清空, 不留残留。 */
    public static void clear() {
        for (ActiveLight light : LIGHTS.values()) {
            removeShimmerLight(light.shimmerLight);
        }
        LIGHTS.clear();
        BULLETS.clear();
    }

    /** 在线段 AB 经过的所有方块上生成光照。 */
    private static void addSegmentLights(Vec3 from, Vec3 to, GunTaczFixesData.LightEntry entry) {
        double distance = from.distanceTo(to);
        if (distance < 1.0E-6) {
            add(BlockPos.containing(to), entry);
            return;
        }
        int steps = Math.max(1, (int) Math.ceil(distance * 2.0d));
        BlockPos previous = null;
        List<BlockPos> visited = new ArrayList<>();
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            BlockPos pos = BlockPos.containing(Mth.lerp(t, from.x, to.x), Mth.lerp(t, from.y, to.y), Mth.lerp(t, from.z, to.z));
            if (pos.equals(previous) || visited.contains(pos)) {
                continue;
            }
            previous = pos;
            visited.add(pos);
            add(pos, entry);
        }
    }

    private static void removeLight(ClientLevel level, long key, BlockPos pos) {
        ActiveLight light = LIGHTS.remove(key);
        if (light == null) {
            return;
        }
        removeShimmerLight(light.shimmerLight);
        if (light.color < 0) {
            relight(level, pos);
        }
    }

    private static ColorPointLight createShimmerLight(Vec3 position, int color, float radius, float level) {
        if (color < 0) {
            return null;
        }
        ColorPointLight light = LightManager.INSTANCE.addLight(
                new Vector3f((float) position.x, (float) position.y, (float) position.z), color, radius, false);
        if (light != null) {
            applyShimmerLight(light, position, color, radius, level);
        }
        return light;
    }

    private static void updateShimmerLight(ColorPointLight light, Vec3 position, int color, float radius, float level) {
        if (light == null || light.isRemoved()) {
            return;
        }
        applyShimmerLight(light, position, color, radius, level);
    }

    private static void removeShimmerLight(ColorPointLight light) {
        if (light != null && !light.isRemoved()) {
            light.remove();
        }
    }

    private static void applyShimmerLight(ColorPointLight light, Vec3 position, int color, float radius, float level) {
        float r = ((color >> 16) & 0xff) / 255f;
        float g = ((color >> 8) & 0xff) / 255f;
        float b = (color & 0xff) / 255f;
        float intensity = Mth.clamp(level / 15.0f, 0.0f, 1.0f);
        light.setPos((float) position.x, (float) position.y, (float) position.z);
        light.setColor(r, g, b, intensity);
        light.radius = radius;
        light.setEnable(intensity > 0.001f);
    }

    private static void relight(ClientLevel level, BlockPos pos) {
        try {
            level.getLightEngine().checkBlock(pos);
        } catch (Throwable ignored) {
        }
    }
}
