package com.ssscript.taczfixes.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ssscript.taczfixes.common.util.AttachmentGroupOffsetHelper;
import com.ssscript.taczfixes.common.util.PosAlterStorage;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Optional;

/** group_offset 平移了瞄具的渲染位置, 必须同步计入开镜瞄准定位(否则镜片中心与瞄准点错位)。 */
@Mixin(targets = "com.tacz.guns.client.event.FirstPersonRenderGunEvent", remap = false)
public class MixinFirstPersonRenderGunEventAimScope {

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
}
