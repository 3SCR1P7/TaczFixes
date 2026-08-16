package com.ssscript.taczfixes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(BedrockAttachmentModel.class)
public interface MixinBedrockAttachmentModelScopeSuppress {

    @Accessor("divisionNodePaths")
    List<List<BedrockPart>> taczfixes$divisionNodePaths();

    @Accessor("ocularNodePaths")
    List<List<BedrockPart>> taczfixes$ocularNodePaths();

    @Accessor("scopeViewRadiusModifier")
    float taczfixes$scopeViewRadiusModifier();

    @Invoker("renderTempPart")
    void taczfixes$renderTempPart(PoseStack poseStack, ItemDisplayContext displayContext,
                                  RenderType renderType, int light, int overlay,
                                  List<BedrockPart> path);
}
