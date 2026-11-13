package com.ssscript.taczfixes.client.screen;

/** 枪包管理界面向原版包条目渲染代码提供“更新红点”状态。 */
public interface GunPackUpdateBadgeSource {
    boolean taczfixes$hasUpdate(String packId);
}
