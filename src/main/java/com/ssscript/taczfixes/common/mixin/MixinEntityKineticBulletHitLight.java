package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.common.network.ServerMessageGunLight;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.ssscript.taczfixes.common.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 鏈嶅姟绔瓙寮瑰懡涓?鏂瑰潡/瀹炰綋)鏃? 鍚戦檮杩戝鎴风鍚屾鍔ㄦ€佸厜鐓?鐖嗙偢鍏?+ 鏈?tick 璧风偣鍒板懡涓偣鐨?bullet 鍏?銆?*/
@Mixin(EntityKineticBullet.class)
public class MixinEntityKineticBulletHitLight {

    @Inject(method = "onHitBlock", at = @At("HEAD"), remap = false)
    private void taczfixes$hitBlockLight(BlockHitResult hitResult, Vec3 from, Vec3 to, CallbackInfo callback) {
        // TaCZ 姣?tick 閮戒細璋冪敤 onHitBlock(鏈懡涓椂 result 涓?MISS), 鍙湁鐪熷疄鍛戒腑鎵嶅簲瑙﹀彂鍏夌収銆?
        if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
            return;
        }
        taczfixes$sendHitLight((EntityKineticBullet) (Object) this, from, to);
    }

    @Inject(method = "onHitEntity", at = @At("HEAD"), remap = false)
    private void taczfixes$hitEntityLight(TacHitResult hitResult, Vec3 from, Vec3 to, CallbackInfo callback) {
        taczfixes$sendHitLight((EntityKineticBullet) (Object) this, from, hitResult.getLocation());
    }

    private static void taczfixes$sendHitLight(EntityKineticBullet bullet, Vec3 from, Vec3 hitPos) {
        if (bullet.level().isClientSide) {
            return;
        }
        GunTaczFixesData.LightConfig light;
        if (bullet instanceof com.ssscript.taczfixes.common.util.LightBulletAccess access && access.taczfixes$isLightCaptured()) {
            light = access.taczfixes$getLightConfig();
        } else {
            light = TaczFixesDataManager.resolveLight(bullet.getGunId());
        }
        if (light == null) {
            return;
        }
        Vec3 to = hitPos == null ? bullet.position() : hitPos;
        Vec3 start = from == null ? new Vec3(bullet.xo, bullet.yo, bullet.zo) : from;
        PacketDistributor.TargetPoint point = new PacketDistributor.TargetPoint(
                to.x, to.y, to.z, 64.0d, bullet.level().dimension());
        if (light.explosion != null && light.explosion.time != null && light.explosion.time > 0) {
            NetworkHandler.CHANNEL.send(PacketDistributor.NEAR.with(() -> point),
                    new ServerMessageGunLight(true, light.explosion.time,
                            orDefault(light.explosion.level_max, 15), orDefault(light.explosion.level_min, 0),
                            com.ssscript.taczfixes.common.util.GunLightColor.parse(light.explosion.color),
                            to.x, to.y, to.z, to.x, to.y, to.z));
        }
        if (light.bullet != null && light.bullet.time != null && light.bullet.time > 0) {
            NetworkHandler.CHANNEL.send(PacketDistributor.NEAR.with(() -> point),
                    new ServerMessageGunLight(false, light.bullet.time,
                            orDefault(light.bullet.level_max, 15), orDefault(light.bullet.level_min, 0),
                            com.ssscript.taczfixes.common.util.GunLightColor.parse(light.bullet.color),
                            start.x, start.y, start.z, to.x, to.y, to.z));
        }
    }

    private static int orDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
