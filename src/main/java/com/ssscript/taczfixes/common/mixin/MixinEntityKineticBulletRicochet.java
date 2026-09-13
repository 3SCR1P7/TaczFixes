package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.register.Config;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(EntityKineticBullet.class)
public class MixinEntityKineticBulletRicochet {
    @Shadow(remap = false)
    private int pierce;
    @Shadow(remap = false)
    private ResourceLocation gunId;

    private boolean taczfixes_ricocheted = false;
    private double taczfixes_rx, taczfixes_ry, taczfixes_rz;
    private float taczfixes_ricochetDamage = 1.0f;

    @Inject(method = "onHitBlock",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/entity/EntityKineticBullet;m_146870_()V", ordinal = 1),
            cancellable = true, remap = false)
    private void beforeDiscard(BlockHitResult result, Vec3 startVec, Vec3 endVec, CallbackInfo ci) {
        EntityKineticBullet bullet = (EntityKineticBullet) (Object) this;
        GunTaczFixesData.RicochetConfig ric = gunId == null ? null : TaczFixesDataManager.resolveRicochet(gunId);
        if (ric == null || !ric.enable) return;
        if (result.getType() == HitResult.Type.MISS) return;

        if (!hasRicochetTag(ric.block_tags, bullet.level().getBlockState(result.getBlockPos()))) return;
        if (isGunDisabled()) return;

        Vec3 velocity = bullet.getDeltaMovement();
        double speed = velocity.length();
        if (speed < 1e-7) return;

        Vec3 normal = Vec3.atLowerCornerOf(result.getDirection().getNormal());
        double cosAngle = Math.abs(velocity.dot(normal) / speed);
        double angle = Math.acos(Math.min(cosAngle, 1.0));
        double minAngle = Math.toRadians(ric.min_angle);
        if (angle < minAngle) return;
        if (this.pierce < 1) return;

        if (!ric.top_bottom_enable
                && (result.getDirection() == Direction.UP
                || result.getDirection() == Direction.DOWN)) {
            return;
        }

        double maxAngle = Math.toRadians(ric.max_angle);
        double chanceMin = ric.chance_min;
        double chanceMax = ric.chance_max;
        double chance = angle >= maxAngle ? chanceMax
                : chanceMin + (chanceMax - chanceMin) * (angle - minAngle) / (maxAngle - minAngle);
        if (Math.random() >= chance) return;

        ci.cancel();
        this.pierce--;

        double dot = velocity.dot(normal);
        double ratioMin = ric.reflect_angle_ratio_min;
        double ratioMax = ric.reflect_angle_ratio_max;
        double ratio = ratioMin + Math.random() * (ratioMax - ratioMin);
        if (ratio < 0.0) ratio = 0.0;
        if (ratio > 1.0) ratio = 1.0;
        Vec3 reflected;
        if (ratio >= 1.0) {
            reflected = velocity.subtract(normal.scale(2 * dot));
        } else {
            double absDot = Math.abs(dot);
            Vec3 tangent = velocity.subtract(normal.scale(dot));
            double tLen = tangent.length();
            if (tLen < 1e-10) {
                reflected = normal.scale(speed);
            } else {
                double theta = Math.atan2(tLen, absDot);
                double thetaR = Math.PI / 2 - (Math.PI / 2 - theta) * ratio;
                Vec3 tangentDir = tangent.scale(1.0 / tLen);
                reflected = normal.scale(speed * Math.cos(thetaR))
                        .add(tangentDir.scale(speed * Math.sin(thetaR)));
            }
        }

        bullet.setPos(result.getLocation().add(normal.scale(0.1)));
        bullet.setDeltaMovement(Vec3.ZERO);

        this.taczfixes_ricocheted = true;
        this.taczfixes_ricochetDamage = ric.damage_multiplier.floatValue();
        this.taczfixes_rx = reflected.x;
        this.taczfixes_ry = reflected.y;
        this.taczfixes_rz = reflected.z;
    }

    @Inject(method = "m_8119_", at = @At("RETURN"), remap = false)
    private void afterTick(CallbackInfo ci) {
        if (this.taczfixes_ricocheted) {
            ((EntityKineticBullet) (Object) this).setDeltaMovement(new Vec3(this.taczfixes_rx, this.taczfixes_ry, this.taczfixes_rz));
            this.taczfixes_ricocheted = false;
        }
    }

    @Inject(method = "getDamage", at = @At("RETURN"), cancellable = true, remap = false)
    private void onGetDamage(CallbackInfoReturnable<Float> cir) {
        if (this.taczfixes_ricochetDamage < 1.0f) {
            cir.setReturnValue(cir.getReturnValue() * this.taczfixes_ricochetDamage);
            this.taczfixes_ricochetDamage = 1.0f;
        }
    }

    private static boolean hasRicochetTag(java.util.List<String> tags, BlockState state) {
        if (tags == null) return false;
        for (String tagStr : tags) {
            ResourceLocation loc = ResourceLocation.tryParse(tagStr);
            if (loc == null) continue;
            TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, loc);
            if (state.is(tagKey)) return true;
        }
        return false;
    }

    private boolean isGunDisabled() {
        List<? extends String> disabled = Config.BULLET_RICOCHET_DISABLED_GUNS.get();
        return disabled.contains(this.gunId.toString());
    }
}
