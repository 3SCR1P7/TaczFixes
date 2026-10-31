package com.ssscript.taczfixes.common.compat;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.client.animation.HardcodedAnimationManger;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@LittleMaidExtension
public final class TouhouMaidDualWieldExtension implements ILittleMaid {
    @OnlyIn(Dist.CLIENT)
    public void addHardcodeAnimation(HardcodedAnimationManger manger) {
        manger.addMaidAnimation(new TouhouMaidDualWieldAnimation());
    }
}
