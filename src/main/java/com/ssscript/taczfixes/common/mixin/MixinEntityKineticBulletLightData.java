package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.util.LightBulletAccess;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 子弹生成时按枪械(含配件)算好动态光照配置并缓存, 随生成数据同步到客户端, 供弹道/爆炸光照使用。 */
@Mixin(EntityKineticBullet.class)
public class MixinEntityKineticBulletLightData implements LightBulletAccess {

    @Unique
    private GunTaczFixesData.LightConfig taczfixes$lightConfig;

    @Unique
    private boolean taczfixes$lightCaptured;

    @Override
    public boolean taczfixes$isLightCaptured() {
        return this.taczfixes$lightCaptured;
    }

    @Override
    public GunTaczFixesData.LightConfig taczfixes$getLightConfig() {
        return this.taczfixes$lightConfig;
    }

    @Override
    public void taczfixes$setLightConfig(GunTaczFixesData.LightConfig config, boolean captured) {
        this.taczfixes$lightConfig = config;
        this.taczfixes$lightCaptured = captured;
    }

    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;ZLcom/tacz/guns/resource/pojo/data/gun/GunData;Lcom/tacz/guns/resource/pojo/data/gun/BulletData;)V",
            at = @At("RETURN"), remap = false)
    private void taczfixes$captureLightConfig(EntityType<?> type, Level level, LivingEntity shooter, ItemStack gunStack,
                                              ResourceLocation ammoId, ResourceLocation gunId,
                                              ResourceLocation gunDisplayId, boolean tracer, GunData gunData,
                                              BulletData bulletData, CallbackInfo ci) {
        if (level.isClientSide) {
            return;
        }
        this.taczfixes$setLightConfig(TaczFixesDataManager.resolveLight(gunStack), true);
    }

    @Inject(method = "writeSpawnData", at = @At("TAIL"), remap = false)
    private void taczfixes$writeLightConfig(FriendlyByteBuf buf, CallbackInfo ci) {
        boolean captured = this.taczfixes$lightCaptured;
        boolean hasConfig = captured && this.taczfixes$lightConfig != null;
        buf.writeBoolean(captured);
        buf.writeBoolean(hasConfig);
        if (!hasConfig) {
            return;
        }
        GunTaczFixesData.LightConfig config = this.taczfixes$lightConfig;
        taczfixes$writeEntry(buf, config.fire);
        taczfixes$writeEntry(buf, config.explosion);
        taczfixes$writeEntry(buf, config.bullet);
    }

    @Inject(method = "readSpawnData", at = @At("TAIL"), remap = false)
    private void taczfixes$readLightConfig(FriendlyByteBuf buf, CallbackInfo ci) {
        boolean captured = buf.readBoolean();
        boolean hasConfig = buf.readBoolean();
        GunTaczFixesData.LightConfig config = null;
        if (hasConfig) {
            config = new GunTaczFixesData.LightConfig();
            config.fire = taczfixes$readEntry(buf);
            config.explosion = taczfixes$readEntry(buf);
            config.bullet = taczfixes$readEntry(buf);
        }
        this.taczfixes$setLightConfig(config, captured);
    }

    @Unique
    private static void taczfixes$writeEntry(FriendlyByteBuf buf, GunTaczFixesData.LightEntry entry) {
        if (entry == null) {
            buf.writeBoolean(false);
            return;
        }
        buf.writeBoolean(true);
        buf.writeVarInt(entry.time == null ? 0 : entry.time);
        buf.writeVarInt(entry.level_max == null ? 0 : entry.level_max);
        buf.writeVarInt(entry.level_min == null ? 0 : entry.level_min);
    }

    @Unique
    private static GunTaczFixesData.LightEntry taczfixes$readEntry(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return null;
        }
        GunTaczFixesData.LightEntry entry = new GunTaczFixesData.LightEntry();
        entry.time = buf.readVarInt();
        entry.level_max = buf.readVarInt();
        entry.level_min = buf.readVarInt();
        return entry;
    }
}
