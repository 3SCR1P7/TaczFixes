package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.LimbDamageHelper;
import com.ssscript.taczfixes.common.util.OffhandBulletSource;
import com.ssscript.taczfixes.common.util.OffhandShooterManager;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.TacHitResult;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {EntityKineticBullet.class}, remap = false)
public abstract class MixinEntityKineticBullet implements OffhandBulletSource {

    @Inject(method = "onHitEntity", at = @At("HEAD"), remap = false)
    private void storeHitLocation(TacHitResult result, Vec3 startVec, Vec3 endVec, CallbackInfo ci) {
        LimbDamageHelper.storeHitPosition(((EntityKineticBullet) (Object) this).getId(), result.getLocation());
    }

    @Shadow
    private Vec3 startPos;

    @Unique
    private static final double DUAL_WIELD$OFFHAND_LATERAL_OFFSET = 0.22d;

    @Unique
    private static final double DUAL_WIELD$MUZZLE_FORWARD_OFFSET = 0.12d;

    @Unique
    private static final double DUAL_WIELD$MUZZLE_VERTICAL_OFFSET = -0.12d;

    @Unique
    private ShooterDataHolder dualWield$sourceData;

    @Unique
    private boolean dualWield$offhandSource;

    @Redirect(method = {"<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;ZLcom/tacz/guns/resource/pojo/data/gun/GunData;Lcom/tacz/guns/resource/pojo/data/gun/BulletData;)V"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/entity/IGunOperator;getCacheProperty()Lcom/tacz/guns/resource/modifier/AttachmentCacheProperty;"))
    private AttachmentCacheProperty dualWield$captureSource(IGunOperator operator, EntityType<? extends Projectile> type, Level level, LivingEntity shooter, ItemStack gunItem, ResourceLocation ammoId, ResourceLocation gunId, ResourceLocation gunDisplayId, boolean tracer, GunData gunData, BulletData bulletData) {
        ShooterDataHolder offhandData = OffhandShooterManager.findOffhandData(shooter, gunItem);
        this.dualWield$offhandSource = offhandData != null;
        if (offhandData != null && offhandData.cacheProperty != null) {
            this.dualWield$sourceData = offhandData;
            return offhandData.cacheProperty;
        }
        return operator.getCacheProperty();
    }

    @Inject(method = {"<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;ZLcom/tacz/guns/resource/pojo/data/gun/GunData;Lcom/tacz/guns/resource/pojo/data/gun/BulletData;)V"}, at = {@At("TAIL")})
    private void dualWield$moveOffhandPhysicalMuzzle(EntityType<? extends Projectile> type, Level level, LivingEntity shooter, ItemStack gunItem, ResourceLocation ammoId, ResourceLocation gunId, ResourceLocation gunDisplayId, boolean tracer, GunData gunData, BulletData bulletData, CallbackInfo callback) {
        if (!this.dualWield$offhandSource || shooter == null) {
            return;
        }
        EntityKineticBullet bullet = (EntityKineticBullet) (Object) this;
        Vec3 view = shooter.getViewVector(1.0f);
        Vec3 horizontalView = new Vec3(view.x, 0.0d, view.z);
        if (horizontalView.lengthSqr() < 1.0E-8d) {
            return;
        }
        Vec3 forward = view.normalize().scale(DUAL_WIELD$MUZZLE_FORWARD_OFFSET);
        Vec3 left = new Vec3(horizontalView.z, 0.0d, -horizontalView.x).normalize().scale(DUAL_WIELD$OFFHAND_LATERAL_OFFSET);
        Vec3 offset = left.add(forward).add(0.0d, DUAL_WIELD$MUZZLE_VERTICAL_OFFSET, 0.0d);
        Vec3 originalPosition = bullet.position();
        Vec3 physicalMuzzle = originalPosition.add(offset);
        if (level.clip(new ClipContext(originalPosition, physicalMuzzle, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter)).getType() != HitResult.Type.MISS) {
            return;
        }
        bullet.setPos(physicalMuzzle.x, physicalMuzzle.y, physicalMuzzle.z);
        this.startPos = physicalMuzzle;
    }

    @Inject(method = {"<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;ZLcom/tacz/guns/resource/pojo/data/gun/GunData;Lcom/tacz/guns/resource/pojo/data/gun/BulletData;)V"}, at = {@At("TAIL")})
    private void blocking$rotateShotDirection(EntityType<? extends Projectile> type, Level level, LivingEntity shooter,
                                              ItemStack gunItem, ResourceLocation ammoId, ResourceLocation gunId,
                                              ResourceLocation gunDisplayId, boolean tracer, GunData gunData,
                                              BulletData bulletData, CallbackInfo callback) {
        if (level.isClientSide() || !(shooter instanceof net.minecraft.world.entity.player.Player player)) {
            return;
        }
        com.ssscript.taczfixes.common.data.GunTaczFixesData.BlockingConfig cfg =
                com.ssscript.taczfixes.common.util.GunBlocking.resolve(gunItem);
        if (!com.ssscript.taczfixes.common.util.GunBlocking.isEnabled(gunItem)) {
            return;
        }
        double maxDistance = com.ssscript.taczfixes.common.util.GunBlocking.distanceMax(cfg);
        double minDistance = com.ssscript.taczfixes.common.util.GunBlocking.distanceMin(cfg);
        double factor = com.ssscript.taczfixes.common.util.GunBlocking.factor(player, maxDistance, minDistance);
        if (factor <= 0.0d) {
            return;
        }
        EntityKineticBullet bullet = (EntityKineticBullet) (Object) this;
        double obstacleDistance = com.ssscript.taczfixes.common.util.GunBlocking.nearestDistance(player, maxDistance);
        boolean dual = com.ssscript.taczfixes.common.util.DualWieldEligibility.isDualWielding(player);
        double facingDeg = dual
                ? com.ssscript.taczfixes.common.util.GunBlocking.facingDual(cfg)
                : com.ssscript.taczfixes.common.util.GunBlocking.facing(cfg, gunItem);
        Vec3 offset = com.ssscript.taczfixes.common.util.GunBlocking.shotOffset(
                player.getLookAngle(), obstacleDistance,
                com.ssscript.taczfixes.common.util.GunBlocking.angleDeg(cfg) * factor,
                com.ssscript.taczfixes.common.util.GunBlocking.deflection(cfg), facingDeg);
        if (offset.lengthSqr() < 1.0E-8d) {
            return;
        }
        Vec3 position = bullet.position().add(offset);
        bullet.setPos(position.x, position.y, position.z);
    }

    @Redirect(method = {"modifyProperty(Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;)Ljava/lang/Object;"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getMainHandItem()Lnet/minecraft/world/item/ItemStack;", remap = true))
    private ItemStack dualWield$resolveDamageGun(LivingEntity shooter) {
        return this.dualWield$sourceData == null ? shooter.getMainHandItem() : shooter.getOffhandItem();
    }

    @Redirect(method = {"modifyProperty(Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;)Ljava/lang/Object;"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/entity/IGunOperator;getDataHolder()Lcom/tacz/guns/entity/shooter/ShooterDataHolder;"))
    private ShooterDataHolder dualWield$resolveDamageData(IGunOperator operator) {
        return this.dualWield$sourceData == null ? operator.getDataHolder() : this.dualWield$sourceData;
    }

    @Inject(method = {"writeSpawnData"}, at = {@At("TAIL")})
    private void dualWield$writeHandSource(FriendlyByteBuf buffer, CallbackInfo callback) {
        buffer.writeBoolean(this.dualWield$offhandSource);
    }

    @Inject(method = {"readSpawnData"}, at = {@At("TAIL")})
    private void dualWield$readHandSource(FriendlyByteBuf buffer, CallbackInfo callback) {
        this.dualWield$offhandSource = buffer.readBoolean();
    }

    @Inject(method = {"m_8119_"}, at = {@At("HEAD")})
    private void dualWield$pushOffhandSkillContext(CallbackInfo callback) {
        EntityKineticBullet bullet = (EntityKineticBullet) (Object) this;
        if (this.dualWield$sourceData != null && !bullet.level().isClientSide()) {
            OffhandShooterManager.pushActiveData(this.dualWield$sourceData);
        }
    }

    @Inject(method = {"m_8119_"}, at = {@At("TAIL")})
    private void dualWield$popOffhandSkillContext(CallbackInfo callback) {
        EntityKineticBullet bullet = (EntityKineticBullet) (Object) this;
        if (this.dualWield$sourceData != null && !bullet.level().isClientSide()) {
            OffhandShooterManager.popActiveData();
        }
    }

    @Override
    public boolean dualWield$isOffhandSource() {
        return this.dualWield$offhandSource;
    }
}
