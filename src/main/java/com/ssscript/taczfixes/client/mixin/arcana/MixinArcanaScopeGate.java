package com.ssscript.taczfixes.client.mixin.arcana;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.ssscript.taczfixes.client.util.ScopeViewHelper;
import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.item.ModernKineticGunItem;
import group.taczexpands.common.accessor.IAccessorAttachmentData;
import group.taczexpands.dist.YKThsud9;
import group.taczexpands.dist.oARXeJn4;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "group/taczexpands/dist/WFkyOIm9", remap = false)
public class MixinArcanaScopeGate {

    @ModifyReturnValue(method = "VR4u5uBF", at = @At("RETURN"), remap = false)
    private boolean taczfixes$customScopeMagnification(boolean original) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return original;

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty() || !(stack.getItem() instanceof ModernKineticGunItem gun)) return original;

        String active = ScopeSwitchState.getActiveSlot(stack);
        if (active != null) {
            ItemStack activeItem = CustomSlotStorage.get(stack, active);
            if (!activeItem.isEmpty()) {
                IAttachment attachment = IAttachment.getIAttachmentOrNull(activeItem);
                ResourceLocation actId = attachment == null ? null : attachment.getAttachmentId(activeItem);
                if (actId != null && !DefaultAssets.isEmptyAttachmentId(actId)) {
                    ClientAttachmentIndex index = TimelessAPI.getClientAttachmentIndex(actId).orElse(null);
                    if (index != null && index.isScope()) {
                        // 与标准槽一致: 组合瞄具切到 sight 视图时按配置关闭镜内放大。
                        CompoundTag customTag = activeItem.getTag();
                        if (Config.DISABLE_ARCANA_MAGNIFICATION_FOR_SIGHT.get()
                                && taczfixes$disablesMagnification(index, customTag)) {
                            return false;
                        }
                        // 镜内放大状态跟随当前瞄具自身的 advanced_rendering 设置;
                        // 未配置时沿用全局默认值。
                        if (!oARXeJn4.vvsWkp6U.wsK63xlE().get()) {
                            return false;
                        }
                        YKThsud9 data = IAccessorAttachmentData.Iauo30Fz(actId);
                        if (data != null) {
                            return data.aInPX54D != null
                                    ? data.aInPX54D
                                    : oARXeJn4.vvsWkp6U.OfFXaRZm().get();
                        }
                        return true;
                    }
                }
            }
        }

        if (!Config.DISABLE_ARCANA_MAGNIFICATION_FOR_SIGHT.get()) return original;

        ResourceLocation id = gun.getAttachmentId(stack, AttachmentType.SCOPE);
        if (id == null || id.equals(DefaultAssets.EMPTY_ATTACHMENT_ID)) {
            id = gun.getBuiltInAttachmentId(stack, AttachmentType.SCOPE);
        }
        if (id == null || id.equals(DefaultAssets.EMPTY_ATTACHMENT_ID)) {
            return false;
        }

        CompoundTag tag = gun.getAttachmentTag(stack, AttachmentType.SCOPE);
        ClientAttachmentIndex index = TimelessAPI.getClientAttachmentIndex(id).orElse(null);
        if (index != null && taczfixes$disablesMagnification(index, tag)) {
            return false;
        }
        return original;
    }

    /** 是否禁用镜内放大: 组合瞄具仅在使用 sight 视图时禁用, 单视图瞄具在非 scope 类型时禁用。 */
    private static boolean taczfixes$disablesMagnification(ClientAttachmentIndex index, CompoundTag tag) {
        if (ScopeViewHelper.isCombinedSight(index)) {
            return ScopeViewHelper.isSightView(index, tag);
        }
        return !index.isScope();
    }
}
