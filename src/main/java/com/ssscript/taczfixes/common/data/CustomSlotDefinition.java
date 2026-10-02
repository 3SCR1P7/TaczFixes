package com.ssscript.taczfixes.common.data;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CustomSlotDefinition {
    public String type;
    public String name;
    @SerializedName(value = "slot", alternate = {"solt"})
    public String slot;
    public List<String> allow_attachments;
    /** 黑名单(支持 #tag), 列出的配件永远无法装入此槽, 优先级最高。 */
    public List<String> blacklist;
    public Map<String, JsonElement> dependence;
    public Map<String, JsonElement> conflict;
    @SerializedName("builtin_attachments")
    public CustomBuiltinAttachment builtin_attachments;
    /** 本槽位 pos_alter 滑条的取值范围 [min, max]; 配件定义的槽用它开放调整。 */
    public List<Double> pos_alter;
    public float angle;
    public float offset;

    public List<String> getAllowAttachments() {
        return allow_attachments == null ? Collections.emptyList() : allow_attachments;
    }

    public List<String> getBlacklist() {
        return blacklist == null ? Collections.emptyList() : blacklist;
    }

    public List<String> getBuiltinAttachmentIds() {
        return builtin_attachments == null ? Collections.emptyList() : builtin_attachments.getAttachments();
    }

    public Map<String, JsonElement> getDependence() {
        return dependence == null ? Collections.emptyMap() : dependence;
    }

    public Map<String, JsonElement> getConflict() {
        return conflict == null ? Collections.emptyMap() : conflict;
    }

    public boolean isCustom() {
        return type == null || type.isEmpty() || "custom".equalsIgnoreCase(type);
    }
}