package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityKineticBullet.class)
public class MixinEntityKineticBulletBulletProperty {

    @Shadow(remap = false)
    private float friction;
    @Shadow(remap = false)
    private float gravity;
    @Shadow(remap = false)
    private int life;

    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;ZLcom/tacz/guns/resource/pojo/data/gun/GunData;Lcom/tacz/guns/resource/pojo/data/gun/BulletData;)V",
            at = @At("RETURN"), remap = false)
    private void taczfixes$applyBulletProperty(EntityType<?> type, Level level, LivingEntity shooter, ItemStack gunItem,
                                               ResourceLocation gunId, ResourceLocation ammoId, ResourceLocation bulletId,
                                               boolean tracer, GunData gunData, BulletData bulletData, CallbackInfo ci) {
        if (gunItem == null || gunItem.isEmpty()) return;
        this.friction = AttachmentTaczFixesManager.applyFriction(gunItem, this.friction);
        this.gravity = AttachmentTaczFixesManager.applyGravity(gunItem, this.gravity);
        this.life = (int) (AttachmentTaczFixesManager.applyBulletLife(gunItem, this.life / 20.0f) * 20.0f);
    }
}