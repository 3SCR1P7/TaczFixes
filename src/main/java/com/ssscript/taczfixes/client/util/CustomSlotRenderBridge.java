package com.ssscript.taczfixes.client.util;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 由 {@code BedrockGunModel} 的 mixin 实现, 使外部渲染路径(例如第三人称
 * {@code GunItemRendererWrapper.lambda$renderByItem$6} 走 SBM 外部网格, 不经过
 * {@code BedrockGunModel.render})也能触发自定义槽位配件的渲染。
 */
public interface CustomSlotRenderBridge {

    void taczfixes$renderCustomSlotsFor(ItemStack gun, PoseStack pose, ItemDisplayContext displayContext,
                                        int light, int overlay);
}
