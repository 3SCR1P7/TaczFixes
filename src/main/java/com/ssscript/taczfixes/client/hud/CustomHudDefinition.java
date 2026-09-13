package com.ssscript.taczfixes.client.hud;

import java.util.List;
import java.util.Map;

/** 自定义枪械 HUD 定义(assets/<ns>/hud/<path>.json)。 */
public class CustomHudDefinition {

    public HudElement gun_hud;
    public TextElement ammo_in_magazine;
    public TextElement ammo_in_inventory;
    public TextElement bullet_type_text;
    public IconElement bullet_type_icon;
    public FireModeElement fire_mode;
    public AdditionElement addition;
    public CrosshairElement crosshair;
    public HudElement overheat_bar;

    public static class HudElement {
        public Boolean hidden;
        public String location;
        public float[] position;
        public float size;
        /** 枪械图标与弹量之间的竖线分隔, 默认显示。 */
        public Boolean show_divider;

        public boolean isHidden() {
            return Boolean.TRUE.equals(hidden);
        }

        public boolean showDivider() {
            return !Boolean.FALSE.equals(show_divider);
        }

        public String location() {
            return location == null || location.isBlank() ? "buttom_right" : location.trim().toLowerCase();
        }

        public float size() {
            return size <= 0 ? 1.0f : size;
        }

        public float posX() {
            return position != null && position.length > 0 ? position[0] : 0f;
        }

        public float posY() {
            return position != null && position.length > 1 ? position[1] : 0f;
        }
    }

    public static class TextElement extends HudElement {
        public String color;
        public String color_empty;
        public List<String> format;
    }

    public static class IconElement extends HudElement {
    }

    public static class FireModeElement extends HudElement {
        /** semi/auto/burst -> 图标 id(assets/<ns>/textures/<path>.png)。未配置的模式使用 tacz 原生贴图。 */
        public Map<String, String> icon;
    }

    public static class AdditionElement extends HudElement {
        public String texture;
    }

    public static class CrosshairElement {
        public Boolean hidden;
        public String texture;

        public boolean isHidden() {
            return Boolean.TRUE.equals(hidden);
        }
    }
}
