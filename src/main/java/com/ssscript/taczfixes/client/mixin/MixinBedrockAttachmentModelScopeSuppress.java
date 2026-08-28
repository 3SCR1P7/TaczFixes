package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
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

    @Accessor("isScopeOcular")
    List<Boolean> taczfixes$isScopeOcular();

    @Accessor("scopeViewRadiusModifier")
    float taczfixes$scopeViewRadiusModifier();

    @Accessor("currentGunItem")
    void taczfixes$setCurrentGunItem(ItemStack gun);

    @Accessor("attachmentItem")
    void taczfixes$setAttachmentItem(ItemStack item);

    @Invoker("renderTempPart")
    void taczfixes$renderTempPart(PoseStack poseStack, ItemDisplayContext displayContext,
                                  RenderType renderType, int light, int overlay,
                                  List<BedrockPart> path);

    @Accessor("scopeBodyPath")
    List<BedrockPart> taczfixes$scopeBodyPath();

    @Accessor("ocularRingPath")
    List<BedrockPart> taczfixes$ocularRingPath();
}
