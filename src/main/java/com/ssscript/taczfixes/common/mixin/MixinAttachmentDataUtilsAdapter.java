package com.ssscript.taczfixes.common.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ssscript.taczfixes.common.data.AdapterAttachmentDataManager;
import com.tacz.guns.resource.modifier.custom.WeightModifier;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.resource.pojo.data.attachment.Modifier;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** 让槽位适配器的原生 data 像配件一样参与属性计算(缓存/柱状图/弹药伤害等)。 */
@Mixin(value = AttachmentDataUtils.class, remap = false)
public class MixinAttachmentDataUtilsAdapter {

    @ModifyReturnValue(method = "getModifiers", at = @At("RETURN"), remap = false)
    private static List<Modifier> taczfixes$addAdapterModifiers(List<Modifier> original, ItemStack gunItem,
                                                                GunData gunData, String id) {
        List<Modifier> adapterModifiers = AdapterAttachmentDataManager.collectModifiers(gunItem, id);
        if (adapterModifiers.isEmpty()) {
            return original;
        }
        List<Modifier> result = original == null ? new ArrayList<>() : new ArrayList<>(original);
        result.addAll(adapterModifiers);
        return result;
    }

    /** 整枪重量: 把已安装适配器的 weight 修饰符并入配件列表后再求值。 */
    @WrapOperation(method = "getWightWithAttachment", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/resource/modifier/AttachmentPropertyManager;eval(Ljava/util/List;D)D", remap = false), remap = false)
    private static double taczfixes$adapterWeight(List<Modifier> modifiers, double defaultValue,
                                                  Operation<Double> original, ItemStack gunItem, GunData gunData) {
        List<Modifier> list = modifiers == null ? new ArrayList<>() : new ArrayList<>(modifiers);
        AdapterAttachmentDataManager.forEachAdapterData(gunItem, data -> {
            var property = data.getModifier().get(WeightModifier.ID);
            if (property != null && property.getValue() instanceof Modifier modifier) {
                list.add(modifier);
            } else {
                Modifier modifier = new Modifier();
                modifier.setAddend(data.getWeight());
                list.add(modifier);
            }
        });
        return original.call(list, defaultValue);
    }
}
