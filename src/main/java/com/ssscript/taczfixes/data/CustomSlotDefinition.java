package com.ssscript.taczfixes.data;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

public class CustomSlotDefinition {
    public String type;
    public String name;
    @SerializedName(value = "slot", alternate = {"solt"})
    public String slot;
    public List<String> allow_attachments;
    public List<String> dependence;
    public List<String> conflict;
    public float angle;
    public float offset;

    public List<String> getAllowAttachments() {
        return allow_attachments == null ? Collections.emptyList() : allow_attachments;
    }

    public List<String> getDependence() {
        return dependence == null ? Collections.emptyList() : dependence;
    }

    public List<String> getConflict() {
        return conflict == null ? Collections.emptyList() : conflict;
    }

    public boolean isCustom() {
        return type == null || type.isEmpty() || "custom".equalsIgnoreCase(type);
    }
}
