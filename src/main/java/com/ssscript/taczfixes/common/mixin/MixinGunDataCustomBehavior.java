package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.CustomFireModeManager;
import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.pojo.data.gun.BurstData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunFireModeAdjustData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 自定义开火模式: fireModeAdjust/burstData 查询时, 若流程内激活了自定义模式(TreadLocal), 优先返回其配置。 */
@Mixin(value = GunData.class, remap = false)
public class MixinGunDataCustomBehavior {

    @Inject(method = "getFireModeAdjustData", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$customAdjust(FireMode mode, CallbackInfoReturnable<GunFireModeAdjustData> cir) {
        GunTaczFixesData.CustomFireModeConfig cfg = CustomFireModeManager.active();
        if (cfg == null || cfg.adjust == null) return;
        GunFireModeAdjustData data = toAdjustData(cfg);
        if (data != null) {
            cir.setReturnValue(data);
        }
    }

    @Inject(method = "getBurstData", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$customBurst(CallbackInfoReturnable<BurstData> cir) {
        GunTaczFixesData.CustomFireModeConfig cfg = CustomFireModeManager.active();
        if (cfg == null || cfg.burst_data == null) return;
        cir.setReturnValue(resolveBurst(cfg));
    }

    /** burst 模式发射间隔: getBurstShootInterval() 内部直读 burstData 字段, 不走 getBurstData(), 需单独拦截。 */
    @Inject(method = "getBurstShootInterval", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$customBurstInterval(CallbackInfoReturnable<Long> cir) {
        GunTaczFixesData.CustomFireModeConfig cfg = CustomFireModeManager.active();
        if (cfg == null || cfg.burst_data == null) return;
        BurstData bd = resolveBurst(cfg);
        if (bd == null || bd.getBpm() <= 0) return;
        cir.setReturnValue(60_000L / bd.getBpm());
    }

    @Unique
    private static BurstData resolveBurst(GunTaczFixesData.CustomFireModeConfig cfg) {
        BurstData fallback = new BurstData();
        MixinBurstDataAccessor acc = (MixinBurstDataAccessor) fallback;
        acc.taczfixes$setContinuousShoot(cfg.burst_data.continuous_shoot != null && cfg.burst_data.continuous_shoot);
        acc.taczfixes$setCount(cfg.burst_data.count == null ? 3 : cfg.burst_data.count);
        acc.taczfixes$setBpm(cfg.burst_data.bpm == null ? 200 : cfg.burst_data.bpm);
        acc.taczfixes$setMinInterval(cfg.burst_data.min_interval == null ? 1.0 : cfg.burst_data.min_interval);
        // 优先使用嵌套的 TACZ 原生 burst_data 结构
        return cfg.burst_data.resolve(fallback);
    }

    @Unique
    private static GunFireModeAdjustData toAdjustData(GunTaczFixesData.CustomFireModeConfig cfg) {
        java.util.Map<String, Double> adjust = cfg.adjust;
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        // 注意: GunFireModeAdjustData 字段使用 @SerializedName, GSON 反序列化仅匹配注解名。
        put(json, "damage", adjust.get("damage"));
        put(json, "rpm", adjust.get("rpm"));
        put(json, "speed", adjust.get("speed"));
        put(json, "knockback", adjust.get("knockback"));
        put(json, "armor_ignore", adjust.get("armor_ignore"));
        put(json, "head_shot_multiplier", adjust.get("head_shot_multiplier"));
        put(json, "aim_inaccuracy", adjust.get("aim_inaccuracy"));
        put(json, "other_inaccuracy", adjust.get("other_inaccuracy"));
        return com.tacz.guns.resource.CommonAssetsManager.GSON.fromJson(json, GunFireModeAdjustData.class);
    }

    @Unique
    private static void put(com.google.gson.JsonObject json, String key, Double value) {
        if (value != null) {
            json.addProperty(key, value);
        }
    }
}
