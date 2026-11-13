package com.ssscript.taczfixes.client.attachment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 配件动画实例的键：按配件 id + 配件/枪械的物品内容（item + 数据组件）+ 第一/第三人称区分。
 * 渲染时传入的 ItemStack 实例可能每帧变化，因此不能用对象身份比较，否则每帧都会重建实例、
 * 动画进度永远从第一帧开始（表现为“动画不生效”）。
 */
public final class AttachmentAnimationKey {
    private final ResourceLocation attachmentId;
    private final ItemStack attachmentStack;
    private final ItemStack gunStack;
    private final boolean firstPerson;

    public AttachmentAnimationKey(ResourceLocation attachmentId, ItemStack attachmentStack,
                                  ItemStack gunStack, boolean firstPerson) {
        this.attachmentId = attachmentId;
        this.attachmentStack = attachmentStack;
        this.gunStack = gunStack;
        this.firstPerson = firstPerson;
    }

    public ResourceLocation attachmentId() {
        return attachmentId;
    }

    public ItemStack attachmentStack() {
        return attachmentStack;
    }

    public ItemStack gunStack() {
        return gunStack;
    }

    public boolean firstPerson() {
        return firstPerson;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AttachmentAnimationKey other)) {
            return false;
        }
        return firstPerson == other.firstPerson
                && attachmentId.equals(other.attachmentId)
                && ItemStack.isSameItemSameComponents(attachmentStack, other.attachmentStack)
                && ItemStack.isSameItemSameComponents(gunStack, other.gunStack);
    }

    @Override
    public int hashCode() {
        int result = attachmentId.hashCode();
        result = 31 * result + ItemStack.hashItemAndComponents(attachmentStack);
        result = 31 * result + ItemStack.hashItemAndComponents(gunStack);
        result = 31 * result + (firstPerson ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return attachmentId + (firstPerson ? "@fp" : "@tp");
    }
}
