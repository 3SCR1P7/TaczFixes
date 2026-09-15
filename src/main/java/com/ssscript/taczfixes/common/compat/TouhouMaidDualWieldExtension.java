package com.ssscript.taczfixes.common.compat;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.client.animation.HardcodedAnimationManger;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@LittleMaidExtension
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/common/compat/TouhouMaidDualWieldExtension.class */
public final class TouhouMaidDualWieldExtension implements ILittleMaid {
    @OnlyIn(Dist.CLIENT)
    public void addHardcodeAnimation(HardcodedAnimationManger manger) {
        manger.addMaidAnimation(new TouhouMaidDualWieldAnimation());
    }
}
