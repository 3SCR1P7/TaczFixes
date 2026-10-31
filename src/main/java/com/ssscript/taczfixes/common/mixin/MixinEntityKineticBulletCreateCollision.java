package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.compat.create.ContraptionBlockHitResult;
import com.ssscript.taczfixes.common.compat.create.CreateContraptionCollisionHelper;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.common.network.ServerMessageContraptionHit;
import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.ExplodeUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import com.ssscript.taczfixes.common.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityKineticBullet.class)
public class MixinEntityKineticBulletCreateCollision {
    @Shadow
    private boolean explosion;
    @Shadow
    private float explosionDamage;
    @Shadow
    private float explosionRadius;
    @Shadow
    private boolean explosionKnockback;
    @Shadow
    private boolean explosionDestroyBlock;
    @Shadow
    private boolean igniteBlock;
    @Shadow
    private ResourceLocation ammoId;
    @Shadow
    private ResourceLocation gunId;
    @Shadow
    private ResourceLocation gunDisplayId;
    @Shadow
    private boolean igniteEntity;

    @Unique
    private ContraptionBlockHitResult taczfixes$contraptionHit;

    @ModifyArg(method = "onBulletTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/ClipContext;<init>(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;Lnet/minecraft/world/entity/Entity;)V"),
            index = 1, remap = false)
    private Vec3 taczfixes$shortenRayForContraption(Vec3 end) {
        EntityKineticBullet bullet = (EntityKineticBullet) (Object) this;
        Vec3 start = bullet.position();
        ContraptionBlockHitResult hit = CreateContraptionCollisionHelper.findContraptionHit(
                bullet.level(), start, end);
        if (hit == null || start.distanceToSqr(hit.getLocation()) > start.distanceToSqr(end)) {
            this.taczfixes$contraptionHit = null;
            return end;
        }
        this.taczfixes$contraptionHit = hit;
        return hit.getLocation();
    }

    @ModifyVariable(method = "onBulletTick", at = @At("STORE"), ordinal = 0, remap = false)
    private BlockHitResult taczfixes$addContraptionCollision(BlockHitResult vanilla) {
        ContraptionBlockHitResult hit = this.taczfixes$contraptionHit;
        this.taczfixes$contraptionHit = null;
        if (hit == null) {
            return vanilla;
        }
        EntityKineticBullet bullet = (EntityKineticBullet) (Object) this;
        Vec3 start = bullet.position();
        if (vanilla.getType() == HitResult.Type.MISS) {
            return hit;
        }
        double hitDistance = start.distanceToSqr(hit.getLocation());
        double vanillaDistance = start.distanceToSqr(vanilla.getLocation());
        return hitDistance <= vanillaDistance ? hit : vanilla;
    }

    @Inject(method = "onHitBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$onContraptionBlockHit(BlockHitResult result, Vec3 startVec, Vec3 endVec,
                                                 CallbackInfo callback) {
        if (!(result instanceof ContraptionBlockHitResult hitResult)) {
            return;
        }
        EntityKineticBullet bullet = (EntityKineticBullet) (Object) this;
        if (NeoForge.EVENT_BUS.post(new AmmoHitBlockEvent(
                bullet.level(), hitResult, hitResult.getState(), bullet)).isCanceled()) {
            callback.cancel();
            return;
        }

        Vec3 hitVec = hitResult.getLocation();
        if (this.explosion) {
            ExplodeUtil.createExplosion(bullet.getOwner(), bullet, this.explosionDamage, this.explosionRadius,
                    this.explosionKnockback, this.explosionDestroyBlock, hitVec);
            bullet.discard();
            callback.cancel();
            return;
        }

        if (bullet.level() instanceof ServerLevel serverLevel) {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                            hitVec.x, hitVec.y, hitVec.z, 64.0D, serverLevel.dimension())),
                    new ServerMessageContraptionHit(hitVec, hitResult.getDirection(), hitResult.getState(),
                            this.ammoId.toString(), this.gunId.toString(), this.gunDisplayId.toString(),
                            this.igniteEntity));
            if (this.igniteBlock) {
                serverLevel.sendParticles(ParticleTypes.LAVA, hitVec.x, hitVec.y, hitVec.z, 1,
                        0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        bullet.discard();
        callback.cancel();
    }
}
