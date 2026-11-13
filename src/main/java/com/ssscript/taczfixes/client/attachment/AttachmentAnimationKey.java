package com.ssscript.taczfixes.client.attachment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * 配件动画实例的键：配件 id + 枪械物品类型 + 第一/第三人称 + 主/副手。
 *
 * <p>渲染时传入的 ItemStack 每帧都可能是新副本，且枪械 ItemStack 的 NBT 会随开火/换弹变化；
 * 若把这些内容放进键里，开火时实例会被重建、动画回到第一帧。这里只使用跨帧稳定的信息
 * （配件 id、枪械 Item、视角、手持侧），保证开火/换弹期间动画实例保持不变。
 */
public final class AttachmentAnimationKey {
    private final ResourceLocation attachmentId;
    private final Item gunItem;
    private final ItemStack gunStack;
    private final boolean firstPerson;
    private final boolean offhand;

    public AttachmentAnimationKey(ResourceLocation attachmentId, Item gunItem, ItemStack gunStack,
                                  boolean firstPerson, boolean offhand) {
        this.attachmentId = attachmentId;
        this.gunItem = gunItem;
        this.gunStack = gunStack;
        this.firstPerson = firstPerson;
        this.offhand = offhand;
    }

    public ResourceLocation attachmentId() {
        return attachmentId;
    }

    public Item gunItem() {
        return gunItem;
    }

    /** 仅用于判断枪械是否仍由本地玩家手持，不参与 equals/hashCode。 */
    public ItemStack gunStack() {
        return gunStack;
    }

    public boolean firstPerson() {
        return firstPerson;
    }

    public boolean offhand() {
        return offhand;
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
                && offhand == other.offhand
                && attachmentId.equals(other.attachmentId)
                && gunItem == other.gunItem;
    }

    @Override
    public int hashCode() {
        int result = attachmentId.hashCode();
        result = 31 * result + System.identityHashCode(gunItem);
        result = 31 * result + (firstPerson ? 1 : 0);
        result = 31 * result + (offhand ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return attachmentId + "@" + System.identityHashCode(gunItem)
                + (firstPerson ? ":fp" : ":tp") + (offhand ? ":off" : ":main");
    }
}
