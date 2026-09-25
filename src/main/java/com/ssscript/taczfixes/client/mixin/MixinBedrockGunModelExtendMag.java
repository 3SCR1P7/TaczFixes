package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 扩容弹匣等级 >= 4 时的模型选择:
 * - 模型里存在 mag_extended_N 组(如 mag_extended_4)时按等级使用对应组;
 * - 没有对应等级时使用现有最高等级的 mag_extended 组(如只有 1-3 级则用 3 级)。
 * 原版只给 mag_extended_1/2/3 注册可见性判断, 4 级以上所有弹匣组都会被隐藏。
 */
@Mixin(value = BedrockGunModel.class, remap = false)
public class MixinBedrockGunModelExtendMag {

    @Unique
    private static final int TACZFIXES_MAX_EXTEND_GROUP = 64;

    @Shadow
    private int currentExtendMagLevel;

    @Unique
    private int[] taczfixes$extendLevels = new int[0];

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void taczfixes$registerExtendMagGroups(BedrockModelPOJO pojo, BedrockVersion version, CallbackInfo ci) {
        BedrockModel self = (BedrockModel) (Object) this;
        // 只认和标准弹匣(mag_standard)同一父节点的 mag_extended_N 才是等级模型组;
        // 挂在其它组下面的同名骨骼(如 mag_extended_7 挂在 mag_extended_1 下)是细节模型, 不能当等级组。
        BedrockPart anchor = self.getNode("mag_standard");
        if (anchor == null) {
            anchor = self.getNode("mag_extended_1");
        }
        BedrockPart parent = anchor == null ? null : anchor.getParent();
        if (parent == null) {
            return;
        }
        List<Integer> levels = new ArrayList<>();
        for (int k = 1; k <= TACZFIXES_MAX_EXTEND_GROUP; k++) {
            String name = "mag_extended_" + k;
            BedrockPart part = self.getNode(name);
            if (part == null || part.getParent() != parent) {
                continue;
            }
            levels.add(k);
            int groupLevel = k;
            ((BedrockAnimatedModel) (Object) this).setFunctionalRenderer(name, p -> {
                p.visible = taczfixes$effectiveLevel() == groupLevel;
                return null;
            });
        }
        taczfixes$extendLevels = levels.stream().mapToInt(Integer::intValue).toArray();
    }

    @Unique
    private int taczfixes$effectiveLevel() {
        int level = currentExtendMagLevel;
        if (level <= 0 || taczfixes$extendLevels.length == 0) {
            return level;
        }
        int chosen = 0;
        for (int k : taczfixes$extendLevels) {
            if (k <= level && k > chosen) {
                chosen = k;
            }
        }
        if (chosen == 0) {
            chosen = taczfixes$extendLevels[taczfixes$extendLevels.length - 1];
        }
        return chosen;
    }
}
