package com.ssscript.taczfixes.util;

/**
 * 由 MixinAttachmentDisplayStepless 附加到 AttachmentDisplay 的访问器，
 * 供运行时读取 display 文件中的 "stepless" 配置。
 */
public interface SteplessDisplayAccessor {
    SteplessConfig getStepless();

    void setStepless(SteplessConfig stepless);
}
