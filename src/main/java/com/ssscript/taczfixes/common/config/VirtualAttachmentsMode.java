package com.ssscript.taczfixes.common.config;

import java.util.Locale;

/** 虚拟配件模式。 */
public enum VirtualAttachmentsMode {
    /** 所有玩家的配件均为虚拟配件。 */
    TRUE,
    /** 仅创造模式玩家的配件为虚拟配件。 */
    CREATIVE,
    /** 关闭(保持原有逻辑)。 */
    FALSE;

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
