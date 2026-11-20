package com.ssscript.taczfixes.common.util;

import org.luaj.vm2.LuaValue;

/** 逻辑机 Lua 脚本发布的自定义 HUD 参数: 值到页面文本的转换。 */
public final class HudParamValues {

    private HudParamValues() {
    }

    /**
     * 把 Lua 值转换成 HUD 页面文本。支持 number / string / boolean;
     * nil 或其他不可表示的值返回 null, 表示删除该参数。
     */
    public static String toText(LuaValue value) {
        if (value == null || value.isnil()) {
            return null;
        }
        if (value.isboolean()) {
            return value.toboolean() ? "true" : "false";
        }
        if (value.isint()) {
            return Integer.toString(value.toint());
        }
        if (value.isnumber()) {
            double number = value.todouble();
            if (Double.isNaN(number) || Double.isInfinite(number)) {
                return null;
            }
            if (number == Math.rint(number) && Math.abs(number) < 1.0E15D) {
                return Long.toString((long) number);
            }
            return Double.toString(number);
        }
        if (value.isstring()) {
            return value.tojstring();
        }
        return null;
    }
}
