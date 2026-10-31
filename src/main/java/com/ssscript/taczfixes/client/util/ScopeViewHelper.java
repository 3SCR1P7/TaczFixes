package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.client.mixin.model.attachment.MixinBedrockAttachmentModelScopeSuppress;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** 组合瞄具(tacz 多视图瞄具)按当前视图判定是 scope 视图还是 sight 视图。 */
@OnlyIn(Dist.CLIENT)
public final class ScopeViewHelper {

    private ScopeViewHelper() {
    }

    /** 是否为多视图(组合)瞄具: views 中引用了多个不同的视图。 */
    public static boolean isCombinedSight(ClientAttachmentIndex index) {
        int[] views = index == null ? null : index.getViews();
        if (views == null || views.length == 0) {
            return false;
        }
        for (int view : views) {
            if (view != views[0]) {
                return true;
            }
        }
        return false;
    }

    /** 当前视图是否为 scope(镜内放大)视图; 非组合瞄具按瞄具类型 isScope 判定。 */
    public static boolean isScopeView(ClientAttachmentIndex index, CompoundTag tag) {
        if (index == null) {
            return false;
        }
        int[] views = index.getViews();
        BedrockAttachmentModel model = index.getAttachmentModel();
        if (views == null || views.length == 0 || model == null) {
            return index.isScope();
        }
        if (!isCombinedSight(index)) {
            return index.isScope();
        }
        int zoomNumber = tag == null ? 0 : AttachmentItemDataAccessor.getZoomNumberFromTag(tag);
        int viewIndex = views[Math.floorMod(zoomNumber, views.length)] - 1;
        try {
            MixinBedrockAttachmentModelScopeSuppress accessor =
                    (MixinBedrockAttachmentModelScopeSuppress) (Object) model;
            List<List<BedrockPart>> ocularPaths = accessor.taczfixes$ocularNodePaths();
            List<Boolean> ocularFlags = accessor.taczfixes$isScopeOcular();
            List<List<BedrockPart>> viewPaths = accessor.taczfixes$scopeViewPaths();
            if (ocularPaths != null && !ocularPaths.isEmpty()
                    && ocularFlags != null && !ocularFlags.isEmpty()
                    && viewPaths != null && viewIndex >= 0 && viewIndex < viewPaths.size()) {
                BedrockPart viewNode = taczfixes$lastNode(viewPaths.get(viewIndex));
                if (viewNode != null) {
                    int bestIndex = -1;
                    double bestDistance = 1.0D;
                    for (int i = 0; i < ocularPaths.size(); i++) {
                        BedrockPart ocularNode = taczfixes$lastNode(ocularPaths.get(i));
                        if (ocularNode == null) {
                            continue;
                        }
                        double dx = viewNode.x - ocularNode.x;
                        double dy = viewNode.y - ocularNode.y;
                        double distance = dx * dx + dy * dy;
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            bestIndex = i;
                        }
                    }
                    if (bestIndex >= 0) {
                        return Boolean.TRUE.equals(ocularFlags.get(bestIndex));
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return index.isScope();
    }

    private static BedrockPart taczfixes$lastNode(List<BedrockPart> path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        return path.get(path.size() - 1);
    }

    /** 当前视图是否为 sight 视图。 */
    public static boolean isSightView(ClientAttachmentIndex index, CompoundTag tag) {
        return !isScopeView(index, tag);
    }

}
