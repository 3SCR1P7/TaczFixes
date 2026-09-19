package com.ssscript.taczfixes.client.render;

import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 纯客户端动态光照: 开火/曳光弹/爆炸按枪械 data 的 light 字段在方块光照引擎外发光, 到期精确移除并重算光照。 */
@OnlyIn(Dist.CLIENT)
public final class ClientGunLightManager {
    private static final Map<Long, ActiveLight> LIGHTS = new ConcurrentHashMap<>();
    private static final Map<Integer, TrackedBullet> BULLETS = new ConcurrentHashMap<>();

    private ClientGunLightManager() {
    }

    private static final class ActiveLight {
        final long startMillis;
        final long endMillis;
        final int levelMax;
        final int levelMin;
        volatile int current;

        ActiveLight(long startMillis, long endMillis, int levelMax, int levelMin, int current) {
            this.startMillis = startMillis;
            this.endMillis = endMillis;
            this.levelMax = levelMax;
            this.levelMin = levelMin;
            this.current = current;
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

    public static void add(BlockPos pos, GunTaczFixesData.LightEntry entry) {
        if (pos == null || entry == null) {
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
        int maxLights = com.ssscript.taczfixes.common.config.Config.GUN_LIGHT_MAX_LIGHTS.get();
        if (LIGHTS.size() >= maxLights && !LIGHTS.containsKey(pos.asLong())) {
            return;
        }
        int levelMax = Mth.clamp(entry.level_max == null ? 15 : entry.level_max, 0, 15);
        int levelMin = Mth.clamp(entry.level_min == null ? 0 : entry.level_min, 0, 15);
        long now = System.currentTimeMillis();
        LIGHTS.put(pos.asLong(), new ActiveLight(now, now + time, levelMax, levelMin, levelMax));
        relight(level, pos);
    }

    /** 服务端命中/销毁同步包: 爆炸光或回程 bullet 线段光(参数由服务端下发, 不依赖客户端数据)。 */
    public static void onLightPacket(boolean explosion, int time, int levelMax, int levelMin, Vec3 from, Vec3 to) {
        if (time <= 0) {
            return;
        }
        if (explosion) {
            addRaw(BlockPos.containing(to), time, levelMax, levelMin);
            return;
        }
        addSegmentLightsRaw(from, to, time, levelMax, levelMin);
    }

    /** 直接用给定参数添加光照(服务端同步用); 满额时优先挤掉最早到期的光源。 */
    public static void addRaw(BlockPos pos, int time, int levelMax, int levelMin) {
        if (pos == null || time <= 0) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.hasChunkAt(pos)) {
            return;
        }
        int maxLights = com.ssscript.taczfixes.common.config.Config.GUN_LIGHT_MAX_LIGHTS.get();
        if (LIGHTS.size() >= maxLights && !LIGHTS.containsKey(pos.asLong())) {
            Long oldestKey = null;
            long oldestEnd = Long.MAX_VALUE;
            for (Map.Entry<Long, ActiveLight> entry : LIGHTS.entrySet()) {
                if (entry.getValue().endMillis < oldestEnd) {
                    oldestEnd = entry.getValue().endMillis;
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey != null) {
                LIGHTS.remove(oldestKey);
                relight(level, BlockPos.of(oldestKey));
            }
        }
        long now = System.currentTimeMillis();
        LIGHTS.put(pos.asLong(), new ActiveLight(now, now + time,
                Mth.clamp(levelMax, 0, 15), Mth.clamp(levelMin, 0, 15), Mth.clamp(levelMax, 0, 15)));
        relight(level, pos);
    }

    /** 线段光照(服务端同步用)。 */
    public static void addSegmentLightsRaw(Vec3 from, Vec3 to, int time, int levelMax, int levelMin) {
        if (from == null || to == null || time <= 0) {
            return;
        }
        double distance = from.distanceTo(to);
        if (distance < 1.0E-6) {
            addRaw(BlockPos.containing(to), time, levelMax, levelMin);
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
            addRaw(pos, time, levelMax, levelMin);
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
            GunTaczFixesData.LightConfig config = TaczFixesDataManager.resolveLight(bullet.getGunId());
            if (config == null || config.bullet == null) {
                return;
            }
            tracked = new TrackedBullet(bullet.getGunId().toString(), new Vec3(bullet.xo, bullet.yo, bullet.zo), config.bullet);
            BULLETS.put(id, tracked);
        }
        tracked.lastPos = bullet.position();
        tracked.ticks++;
        addSegmentLights(new Vec3(bullet.xo, bullet.yo, bullet.zo), bullet.position(), tracked.bullet);
    }

    /** 光照引擎查询入口: 该方块位置当前的自定义光照等级。 */
    public static int emissionAt(long packedPos) {
        ActiveLight light = LIGHTS.get(packedPos);
        return light == null ? 0 : light.current;
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
                LIGHTS.remove(entry.getKey());
                changed.add(BlockPos.of(entry.getKey()));
                continue;
            }
            float progress = (float) (now - light.startMillis) / (float) (light.endMillis - light.startMillis);
            int target = Mth.clamp(Math.round(Mth.lerp(progress, light.levelMax, light.levelMin)), 0, 15);
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
        LIGHTS.keySet().removeIf(packed -> new net.minecraft.world.level.ChunkPos(packed).equals(chunkPos));
        BULLETS.values().removeIf(tracked -> new net.minecraft.world.level.ChunkPos(
                BlockPos.containing(tracked.lastPos)).equals(chunkPos));
    }

    /** 世界卸载/退出: 光照引擎随之重建, 直接清空, 不留残留。 */
    public static void clear() {
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

    private static void relight(ClientLevel level, BlockPos pos) {
        try {
            level.getLightEngine().checkBlock(pos);
        } catch (Throwable ignored) {
        }
    }
}
