package com.ssscript.taczfixes.common.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ssscript.taczfixes.common.data.AdapterAttachmentDataManager;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

/** 让槽位适配器的原生 data 进入属性缓存(柱状图/实际伤害等都读它)。 */
@Mixin(value = AttachmentCacheProperty.class, remap = false)
public class MixinAttachmentCachePropertyAdapter {

    /** 包住 eval 内对 getAllAttachmentData 的调用, 追加喂入已安装适配器的 data。 */
    @WrapOperation(method = "eval", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/util/AttachmentDataUtils;getAllAttachmentData(Lnet/minecraft/world/item/ItemStack;Lcom/tacz/guns/resource/pojo/data/gun/GunData;Ljava/util/function/Consumer;)V",
            remap = false), remap = false)
    private static void taczfixes$feedAdapterData(ItemStack gunItem, GunData gunData,
                                                  Consumer<AttachmentData> consumer, Operation<Void> original) {
        original.call(gunItem, gunData, consumer);
        AdapterAttachmentDataManager.forEachAdapterData(gunItem, consumer);
    }
}
