package com.ssscript.taczfixes.client.attachment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * 配件动画实例的键：配件 id + 配件物品内容 + 枪械 id + 第一/第三人称 + 主/副手。
 *
 * <p>渲染时传入的 ItemStack 每帧都可能是新副本，且枪械 ItemStack 的 NBT 会随开火/换弹变化，
 * 因此键不能包含枪械 ItemStack 的完整内容（否则开火时实例会重建、动画回到第一帧），
 * 也不能用对象身份比较。这里只用稳定的“枪械 id + 手持侧”区分不同枪械。
 */
public final class AttachmentAnimationKey {
    private final ResourceLocation attachmentId;
    private final ItemStack attachmentStack;
    private final ItemStack gunStack;
    private final ResourceLocation gunId;
    private final boolean firstPerson;
    private final boolean offhand;

    public AttachmentAnimationKey(ResourceLocation attachmentId, ItemStack attachmentStack,
                                  ItemStack gunStack, ResourceLocation gunId,
                                  boolean firstPerson, boolean offhand) {
        this.attachmentId = attachmentId;
        this.attachmentStack = attachmentStack;
        this.gunStack = gunStack;
        this.gunId = gunId;
        this.firstPerson = firstPerson;
        this.offhand = offhand;
    }

    public ResourceLocation attachmentId() {
        return attachmentId;
    }

    public ItemStack attachmentStack() {
        return attachmentStack;
    }

    /** 仅用于判断枪械是否仍由本地玩家手持，不参与 equals/hashCode。 */
    public ItemStack gunStack() {
        return gunStack;
    }

    public ResourceLocation gunId() {
        return gunId;
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
                && Objects.equals(gunId, other.gunId)
                && ItemStack.isSameItemSameTags(attachmentStack, other.attachmentStack);
    }

    @Override
    public int hashCode() {
        int result = attachmentId.hashCode();
        result = 31 * result + hashStack(attachmentStack);
        result = 31 * result + Objects.hashCode(gunId);
        result = 31 * result + (firstPerson ? 1 : 0);
        result = 31 * result + (offhand ? 1 : 0);
        return result;
    }

    private static int hashStack(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return 31 * stack.getItem().hashCode() + (tag == null ? 0 : tag.hashCode());
    }

    @Override
    public String toString() {
        return attachmentId + "@" + gunId + (firstPerson ? ":fp" : ":tp") + (offhand ? ":off" : ":main");
    }
}
