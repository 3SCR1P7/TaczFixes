package com.ssscript.taczfixes.client.mixin;

import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取原版枪包管理界面的两个包列表, 用于判断鼠标点中了哪一行。 */
@Mixin(PackSelectionScreen.class)
public interface MixinPackSelectionScreenAccessor {
    @Accessor("availablePackList")
    TransferableSelectionList taczfixes$getAvailablePackList();

    @Accessor("selectedPackList")
    TransferableSelectionList taczfixes$getSelectedPackList();

    /** 1.21.1 的原版界面用 HeaderAndFooterLayout; 这里加高 footer 容纳更新按钮。 */
    @Accessor("layout")
    HeaderAndFooterLayout taczfixes$getLayout();
}
