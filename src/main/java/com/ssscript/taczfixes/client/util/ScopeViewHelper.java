package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.client.mixin.MixinAttachmentModelAccessor;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

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
        int zoomNumber = tag == null ? 0 : AttachmentItemDataAccessor.getZoomNumberFromTag(tag);
        int viewIndex = views[Math.floorMod(zoomNumber, views.length)] - 1;
        try {
            List<Boolean> ocular = ((MixinAttachmentModelAccessor) (Object) model).taczfixes$getIsScopeOcular();
            if (ocular != null && viewIndex >= 0 && viewIndex < ocular.size()) {
                return Boolean.TRUE.equals(ocular.get(viewIndex));
            }
        } catch (Throwable ignored) {
        }
        return index.isScope();
    }

    /** 当前视图是否为 sight 视图。 */
    public static boolean isSightView(ClientAttachmentIndex index, CompoundTag tag) {
        return !isScopeView(index, tag);
    }

    /** 当前视图对应的模型/镜内 FOV(display 的 views_fov 按视图取值); 无法确定返回 -1。 */
    public static float modelFovForCurrentView(ClientAttachmentIndex index, CompoundTag tag) {
        if (index == null) {
            return -1.0f;
        }
        float[] viewsFov = index.getViewsFov();
        int[] views = index.getViews();
        if (viewsFov == null || viewsFov.length == 0) {
            return -1.0f;
        }
        if (views == null || views.length == 0) {
            return viewsFov[0];
        }
        int zoomNumber = tag == null ? 0 : AttachmentItemDataAccessor.getZoomNumberFromTag(tag);
        int viewIndex = views[Math.floorMod(zoomNumber, views.length)] - 1;
        if (viewIndex >= 0 && viewIndex < viewsFov.length) {
            return viewsFov[viewIndex];
        }
        return viewsFov[0];
    }
}
