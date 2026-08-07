package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.Config;
import com.ssscript.taczfixes.util.GunEnchantmentHelper;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 子弹出生时根据射手手中的枪械附魔调整：
 * - 穿透：每级 +pierce 穿透数
 * - 冲击：每级增加子弹击退强度
 * - 火矢：强制点燃实体并延长燃烧时长
 */
@Mixin(EntityKineticBullet.class)
public class MixinEntityKineticBulletEnchant {
    @Shadow(remap = false)
    private int pierce;
    @Shadow(remap = false)
    private float knockback;
    @Shadow(remap = false)
    private boolean igniteEntity;
    @Shadow(remap = false)
    private int igniteEntityTime;

    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;ZLcom/tacz/guns/resource/pojo/data/gun/GunData;Lcom/tacz/guns/resource/pojo/data/gun/BulletData;)V", at = @At("TAIL"))
    private void taczfixes$applyBulletEnchants(EntityType<? extends Projectile> type, Level worldIn, LivingEntity throwerIn,
                                               ItemStack gunItem, ResourceLocation ammoId, ResourceLocation gunId,
                                               ResourceLocation gunDisplayId, boolean isTracerAmmo,
                                               GunData gunData, BulletData bulletData, CallbackInfo ci) {
        if (gunItem.isEmpty()) {
            return;
        }
        // 穿透
        int piercing = GunEnchantmentHelper.getLevel(gunItem, Enchantments.PIERCING);
        if (piercing > 0) {
            this.pierce += piercing * Config.ENCH_PIERCE_PER_LEVEL.get();
        }
        // 冲击：按基础击退比例 + 固定值
        int punch = GunEnchantmentHelper.getLevel(gunItem, Enchantments.PUNCH_ARROWS);
        if (punch > 0) {
            double mult = Config.ENCH_PUNCH_BULLET_KNOCKBACK_MULT.get() * punch;
            double flat = Config.ENCH_PUNCH_BULLET_KNOCKBACK_FLAT.get() * punch;
            this.knockback = (float) (this.knockback * (1.0 + mult) + flat);
        }
        // 火矢：强制点燃
        int flame = GunEnchantmentHelper.getLevel(gunItem, Enchantments.FLAMING_ARROWS);
        if (flame > 0) {
            this.igniteEntity = true;
            this.igniteEntityTime += flame * Config.ENCH_FLAME_IGNITE_TICKS.get();
        }
    }
}