package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.client.model.BedrockAttachmentModel;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取瞄具各视图的眼罩类型(scope/sight), 用于组合瞄具按视图判定镜内放大。 */
@Mixin(BedrockAttachmentModel.class)
public interface MixinAttachmentModelAccessor {
    @Accessor("isScopeOcular")
    List<Boolean> taczfixes$getIsScopeOcular();
}
