package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.util.RenderHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.opengl.GL11;

/** 镜内模板语义工具: 按当前使用中的瞄具(active 自定义槽优先, 其次原生解析 scope 槽)设置模板剔除查询函数。 */
public final class ScopeStencilHelper {

    private ScopeStencilHelper() {
    }

    /** 应用当前使用中的瞄具的模板语义(筒状/组合镜, sight 型不设置)。 */
    public static void apply(ItemStack gun) {
        String active = ScopeSwitchState.getActiveSlot(gun);
        if (active != null) {
            ItemStack actItem = CustomSlotStorage.get(gun, active);
            if (actItem != null && !actItem.isEmpty() && isScopeLikeAttachment(actItem)) {
                applyFunc(actItem);
                return;
            }
        }
        ItemStack lens = readStandardScope(gun);
        if (lens.isEmpty()) {
            IGun igun = IGun.getIGunOrNull(gun);
            if (igun != null) {
                lens = igun.getBuiltinAttachment(gun, AttachmentType.SCOPE);
            }
        }
        if (isScopeLikeAttachment(lens)) {
            applyFunc(lens);
        }
    }

    private static void applyFunc(ItemStack lens) {
        IAttachment attachment = IAttachment.getIAttachmentOrNull(lens);
        if (attachment == null) return;
        ResourceLocation id = attachment.getAttachmentId(lens);
        if (id == null) return;
        TimelessAPI.getClientAttachmentIndex(id).ifPresent(index -> {
            if (index.isScope() && index.isSight()) {
                // 组合镜: 只有 ocular_scope 区域(>127 高值)剔除
                RenderHelper.enableItemEntityStencilTest();
                com.mojang.blaze3d.systems.RenderSystem.stencilFunc(GL11.GL_GREATER, 127, 255);
            } else if (index.isScope()) {
                // 纯筒状镜: 镜片外(EQUAL 0)剔除
                RenderHelper.enableItemEntityStencilTest();
                com.mojang.blaze3d.systems.RenderSystem.stencilFunc(GL11.GL_EQUAL, 0, 255);
            }
            // sight 型(红点/全息): 不设置任何模板语义, 不剔除
        });
    }

    private static boolean isScopeLikeAttachment(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        IAttachment ia = IAttachment.getIAttachmentOrNull(item);
        if (ia == null) return false;
        return TimelessAPI.getClientAttachmentIndex(ia.getAttachmentId(item))
                .map(index -> index.isScope() || index.isSight()).orElse(false);
    }

    private static ItemStack readStandardScope(ItemStack gun) {
        CompoundTag tag = gun.getTag();
        if (tag != null) {
            String key = com.tacz.guns.api.item.nbt.GunItemDataAccessor.GUN_ATTACHMENT_BASE + AttachmentType.SCOPE.name();
            if (tag.contains(key, 10)) {
                ItemStack scope = ItemStack.of(tag.getCompound(key));
                if (!scope.isEmpty()) return scope;
            }
        }
        return ItemStack.EMPTY;
    }
}
