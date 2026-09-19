package com.ssscript.taczfixes.client.handler;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.client.render.ClientGunLightManager;
import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** 纯客户端枪械动态光照: 开火/爆炸/曳光弹事件 -> 光照管理; 区块/世界卸载时清理。 */
@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public class GunLightHandler {

    @SubscribeEvent
    public static void onGunFire(GunFireEvent event) {
        if (event.getLogicalSide() != LogicalSide.CLIENT) {
            return;
        }
        LivingEntity shooter = event.getShooter();
        if (shooter == null) {
            return;
        }
        GunTaczFixesData.LightConfig light = TaczFixesDataManager.resolveLight(event.getGunItemStack());
        if (light == null || light.fire == null) {
            return;
        }
        Vec3 pos = shooter.getEyePosition().add(shooter.getLookAngle().scale(0.5d));
        ClientGunLightManager.add(BlockPos.containing(pos), light.fire);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Start event) {
        if (!(event.getLevel() instanceof ClientLevel)) {
            return;
        }
        Entity source = event.getExplosion().getDirectSourceEntity();
        if (!(source instanceof EntityKineticBullet bullet)) {
            return;
        }
        GunTaczFixesData.LightConfig light = TaczFixesDataManager.resolveLight(bullet.getGunId());
        if (light == null || light.explosion == null) {
            return;
        }
        ClientGunLightManager.add(BlockPos.containing(event.getExplosion().getPosition()), light.explosion);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ClientGunLightManager.tick();
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        ClientGunLightManager.removeInChunk(event.getChunk().getPos());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        ClientGunLightManager.clear();
    }
}
