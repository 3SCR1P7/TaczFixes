package com.ssscript.taczfixes.common.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * 供 common mixin 调用的客户端分发入口。
 * common mixin 内不得直接引用任何客户端类(含 lambda/方法体), 否则 Mixin 在专用服务器
 * 预处理时会解析客户端类元数据并抛 ClassMetadataNotFoundException, 整个类加载失败。
 */
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
