package com.ssscript.taczfixes.common.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import com.ssscript.taczfixes.common.util.DistExecutor;

/**
 * 渚?common mixin 璋冪敤鐨勫鎴风鍒嗗彂鍏ュ彛銆? * common mixin 鍐呬笉寰楃洿鎺ュ紩鐢ㄤ换浣曞鎴风绫?鍚?lambda/鏂规硶浣?, 鍚﹀垯 Mixin 鍦ㄤ笓鐢ㄦ湇鍔″櫒
 * 棰勫鐞嗘椂浼氳В鏋愬鎴风绫诲厓鏁版嵁骞舵姏 ClassMetadataNotFoundException, 鏁翠釜绫诲姞杞藉け璐ャ€? */
public final class ClientOnlyDispatch {

    private ClientOnlyDispatch() {
    }

    public static void playDryFireFeedback(LivingEntity shooter, ItemStack stack) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.ssscript.taczfixes.client.util.ClientDryFireFeedback.play(shooter, stack));
    }

    public static void registerTextShow(String name) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.ssscript.taczfixes.client.util.TextShowPapiCompat.register(name));
    }
}
