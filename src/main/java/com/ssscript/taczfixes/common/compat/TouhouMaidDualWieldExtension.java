package com.ssscript.taczfixes.common.compat;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.client.animation.HardcodedAnimationManger;

@LittleMaidExtension
public final class TouhouMaidDualWieldExtension implements ILittleMaid {
    @Override
    public void addHardcodeAnimation(HardcodedAnimationManger manger) {
        manger.addMaidAnimation(new TouhouMaidDualWieldAnimation());
    }
}
