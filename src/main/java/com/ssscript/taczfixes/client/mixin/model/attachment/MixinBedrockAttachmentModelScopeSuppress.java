package com.ssscript.taczfixes.client.mixin.model.attachment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@SuppressWarnings({"target", "mapping", "public-target", "unresolvable-target", "visibility", "constraints", "overwrite", "default-package", "raw-types"})
@Mixin(BedrockAttachmentModel.class)
public interface MixinBedrockAttachmentModelScopeSuppress {

    @Accessor(value = "scopeViewPaths", remap = false)
    List<List<BedrockPart>> taczfixes$scopeViewPaths();

    @Accessor(value = "ocularNodePaths", remap = false)
    List<List<BedrockPart>> taczfixes$ocularNodePaths();

    @Accessor(value = "isScopeOcular", remap = false)
    List<Boolean> taczfixes$isScopeOcular();

    @Accessor(value = "isScopeOcular", remap = false)
    void taczfixes$setIsScopeOcular(List<Boolean> flags);

    @Accessor(value = "divisionNodePaths", remap = false)
    List<List<BedrockPart>> taczfixes$divisionNodePaths();

    @Accessor(value = "currentGunItem", remap = false)
    void taczfixes$setCurrentGunItem(ItemStack gun);

    @Accessor(value = "attachmentItem", remap = false)
    ItemStack taczfixes$attachmentItem();

    @Accessor(value = "attachmentItem", remap = false)
    void taczfixes$setAttachmentItem(ItemStack item);

    @Accessor(value = "scopeBodyPath", remap = false)
    List<BedrockPart> taczfixes$scopeBodyPath();

    @Accessor(value = "ocularRingPath", remap = false)
    List<BedrockPart> taczfixes$ocularRingPath();

    @Accessor(value = "isScope", remap = false)
    boolean taczfixes$isScope();

    @Accessor(value = "isScope", remap = false)
    void taczfixes$setIsScope(boolean isScope);

    @Accessor(value = "isSight", remap = false)
    boolean taczfixes$isSight();

    @Accessor(value = "isSight", remap = false)
    void taczfixes$setIsSight(boolean isSight);

    @Invoker(value = "renderTempPart", remap = false)
    void taczfixes$renderTempPart(PoseStack poseStack, ItemDisplayContext displayContext,
                                  RenderType renderType, int light, int overlay,
                                  List<BedrockPart> path, MultiBufferSource bufferSource);
}
