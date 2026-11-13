package com.ssscript.taczfixes.client.mixin;

import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 读取原版列表的行位置/行高, 避免在管理界面里重复计算滚动。 */
@Mixin(AbstractSelectionList.class)
public interface MixinAbstractSelectionListAccessor {
    @Invoker("getRowTop")
    int taczfixes$getRowTop(int index);

    @Accessor("itemHeight")
    int taczfixes$getItemHeight();
}
