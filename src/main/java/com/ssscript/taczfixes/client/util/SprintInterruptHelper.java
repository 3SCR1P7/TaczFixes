package com.ssscript.taczfixes.client.util;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.client.gameplay.LocalPlayerSprint;
import net.minecraft.client.player.LocalPlayer;

/** 与 tacz LocalPlayerSprint.getProcessedSprintStatus 一致的非开镜打断判定(开火/换弹)。 */
public final class SprintInterruptHelper {

    private SprintInterruptHelper() {
    }

    /** tacz 是否要求打断疾跑(开火尝试中或换弹中)。 */
    public static boolean taczBlocksSprintReengage(LocalPlayer player) {
        IGunOperator operator = IGunOperator.fromLivingEntity(player);
        if (operator == null) return false;
        ReloadState.StateType state = operator.getSynReloadState().getStateType();
        return LocalPlayerSprint.stopSprint || (state.isReloading() && !state.isReloadFinishing());
    }
}
