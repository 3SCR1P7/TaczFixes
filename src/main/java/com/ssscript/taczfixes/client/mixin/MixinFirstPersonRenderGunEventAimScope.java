package com.ssscript.taczfixes.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.ssscript.taczfixes.common.util.AttachmentGroupOffsetHelper;
import com.ssscript.taczfixes.common.util.PosAlterStorage;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** group_offset 平移了瞄具的渲染位置, 必须同步计入开镜瞄准定位(否则镜片中心与瞄准点错位)。 */
@Mixin(targets = "com.tacz.guns.client.event.FirstPersonRenderGunEvent", remap = false)
public class MixinFirstPersonRenderGunEventAimScope {

    /** 镜内视图模型/缩放使用当前自定义槽瞄具, 挂载偏移保持原生槽。 */
    @WrapOperation(method = "applyFirstPersonPositioningTransform",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/TimelessAPI;getClientAttachmentIndex(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;"),
            remap = false)
    private static Optional<com.tacz.guns.client.resource.index.ClientAttachmentIndex> taczfixes$activeScopeIndex(
            ResourceLocation id, Operation<Optional<com.tacz.guns.client.resource.index.ClientAttachmentIndex>> original,
            @Local(argsOnly = true) ItemStack gunStack) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null || ScopeSwitchState.getActiveSlot(gunStack) == null) return original.call(id);
        ResourceLocation activeId = ScopeSwitchState.attachmentId(gun, gunStack, AttachmentType.SCOPE);
        if (activeId == null || com.tacz.guns.api.DefaultAssets.isEmptyAttachmentId(activeId)) return original.call(id);
        return com.tacz.guns.api.TimelessAPI.getClientAttachmentIndex(activeId);
    }

    @WrapOperation(method = "applyFirstPersonPositioningTransform",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/item/IGun;getAttachmentTag(Lnet/minecraft/world/item/ItemStack;Lcom/tacz/guns/api/item/attachment/AttachmentType;)Lnet/minecraft/nbt/CompoundTag;"),
            remap = false)
    private static CompoundTag taczfixes$activeScopeTag(IGun gun, ItemStack stack, AttachmentType type,
                                                        Operation<CompoundTag> original) {
        return type == AttachmentType.SCOPE ? ScopeSwitchState.attachmentTag(gun, stack, type) : original.call(gun, stack, type);
    }

    /** 瞄准定位矩阵由 getScopeMountOffset(...)->getPositioningNodeInverse(path, mountOffset, view) 生成;
     *  mountOffset 以 (-x/16, y/16, -z/16) 平移写入矩阵, 故补入渲染端等值位移需要 (gx, -gy, gz)。 */
    @WrapOperation(method = "applyFirstPersonPositioningTransform",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/client/event/FirstPersonRenderGunEvent;getScopeMountOffset(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/resources/ResourceLocation;Ljava/util/Optional;)Lorg/joml/Vector3f;"),
            require = 0, remap = false)
    private static Vector3f taczfixes$aimWithGroupOffset(ItemStack gunStack, ResourceLocation scopeId, Optional variant,
                                                         Operation<Vector3f> original) {
        Vector3f base = original.call(gunStack, scopeId, variant);
        if (gunStack == null || gunStack.isEmpty()) return base;
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return base;
        EnumMap<AttachmentType, ItemStack> attachments = new EnumMap<>(AttachmentType.class);
        for (AttachmentType type : AttachmentType.values()) {
            ItemStack attachment = gun.getAttachment(gunStack, type);
            if (attachment != null && !attachment.isEmpty()) {
                attachments.put(type, attachment);
            }
        }
        float[] sum = AttachmentGroupOffsetHelper.sumOffsets(attachments, AttachmentType.SCOPE.name().toLowerCase(Locale.ROOT));
        float x = sum == null ? 0.0F : sum[0];
        float y = sum == null ? 0.0F : sum[1];
        float z = sum == null ? 0.0F : sum[2];
        float posAlterZ = PosAlterStorage.get(gunStack, "scope");
        if (x == 0.0F && y == 0.0F && z == 0.0F && posAlterZ == 0.0F) return base;
        Vector3f v = base == null ? new Vector3f() : base;
        v.x += x;
        v.y -= y;
        v.z += z + posAlterZ;
        return v;
    }

    /**
     * iron_view: 机瞄(未安装瞄具, mountOffset 为 null)时, 按 group_offset.iron_view 平移开镜瞄准定位(机瞄相机)。
     * 与 scope 的 mountOffset 写入方式一致: 平移前置, 偏移量按 (-x/16, -y/16, -z/16) 写入。
     */
    @WrapOperation(method = "applyFirstPersonPositioningTransform",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/client/event/FirstPersonRenderGunEvent;getPositioningNodeInverse(Ljava/util/List;Lorg/joml/Vector3f;I)Lorg/joml/Matrix4f;"),
            require = 0, remap = false)
    private static Matrix4f taczfixes$ironViewOffset(List<BedrockPart> path, Vector3f mountOffset, int viewIndex,
                                                     Operation<Matrix4f> original,
                                                     @Local(argsOnly = true) ItemStack gunStack) {
        Matrix4f matrix = original.call(path, mountOffset, viewIndex);
        if (mountOffset != null || gunStack == null || gunStack.isEmpty()) return matrix;
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return matrix;
        EnumMap<AttachmentType, ItemStack> attachments = new EnumMap<>(AttachmentType.class);
        for (AttachmentType type : AttachmentType.values()) {
            ItemStack attachment = gun.getAttachment(gunStack, type);
            if (attachment != null && !attachment.isEmpty()) {
                attachments.put(type, attachment);
            }
        }
        float[] sum = AttachmentGroupOffsetHelper.sumOffsets(attachments, "iron_view");
        if (sum == null) return matrix;
        return new Matrix4f()
                .translate(-sum[0] / 16.0F, -sum[1] / 16.0F, -sum[2] / 16.0F)
                .mul(matrix);
    }
}
