package com.ssscript.taczfixes.client.hud;

/**
 * 一帧需要写入 ApricityUI 页面的枪械状态。作为不可变快照使用,
 * 数值未变化时不会重复操作 DOM。
 */
public record CustomHudSnapshot(
        String gunId,
        String ammoText,
        String ammoPadded,
        int ammo,
        int ammoMax,
        int ammoPercent,
        String ammoStyle,
        boolean ammoEmpty,
        boolean useInventoryAmmo,
        boolean inventoryInfinite,
        int inventoryAmmo,
        String inventoryText,
        String ammoTypeId,
        String ammoTypeKey,
        String ammoTypeName,
        String fireMode,
        boolean hasHeat,
        float heatValue,
        float heatMax,
        int heatPercent,
        boolean heatLocked,
        boolean aiming,
        float aimProgress,
        boolean reloading,
        boolean offhandReloading,
        boolean sprinting,
        boolean holdBreath,
        boolean dualWield,
        boolean crawling,
        boolean sneaking,
        String hudTexture,
        String hudEmptyTexture,
        boolean showCrosshair
) {
}
