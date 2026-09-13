package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.common.data.CustomSlotDefinition;

/** 自定义槽位在改装界面中的一项: 槽位 id、定义与当前是否不可用。 */
public final class CustomSlotEntry {

    public final String id;
    public final CustomSlotDefinition def;
    public final boolean unavailable;

    public CustomSlotEntry(String id, CustomSlotDefinition def, boolean unavailable) {
        this.id = id;
        this.def = def;
        this.unavailable = unavailable;
    }
}
