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
    public Map<String, JsonElement> dependence;
    public Map<String, JsonElement> conflict;
    public float angle;
    public float offset;

    public List<String> getAllowAttachments() {
        return allow_attachments == null ? Collections.emptyList() : allow_attachments;
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