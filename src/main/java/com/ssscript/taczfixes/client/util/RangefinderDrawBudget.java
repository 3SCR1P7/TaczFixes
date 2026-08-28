package com.ssscript.taczfixes.client.util;

/**
 * 火控瞄具预测框的渲染预算:
 * RangefinderManager 为全局单例, 标准 scope 与自定义 scope2 两把火控瞄具同装时
 * 两个 functional renderer 都会尝试绘制预测框。
 * 由渲染链设置"瞄准场景"标志: 标准槽开镜或自定义槽激活开镜期间放行,
 * 非瞄准渲染(standby/备用镜外观)一律拒绝, 从而稳确定性消除第二个框。
 */
public final class RangefinderDrawBudget {
    private static boolean aimingScene = false;

    private RangefinderDrawBudget() {
    }

    /** 标记当前渲染是否处于瞄准场景(开镜)。 */
    public static void setAimingScene(boolean aiming) {
        aimingScene = aiming;
    }

    /** 仅瞄准场景允许绘制预测框。 */
    public static boolean tryConsume() {
        return aimingScene;
    }
}
