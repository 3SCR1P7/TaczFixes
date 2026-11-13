package com.ssscript.taczfixes.client.hud.aui;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
import com.ssscript.taczfixes.client.hud.ApricityHudBackend;
import com.ssscript.taczfixes.client.hud.CustomHudSnapshot;

import java.util.List;
import java.util.Locale;

/**
 * 直接引用 ApricityUI 类的隔离实现。只会在模组已加载时由
 * {@code ApricityHudSupport} 通过反射实例化。
 *
 * <p>页面约定见 README 与 example/example_custom_hud.html; Java 侧只更新
 * 约定 id 的文本/属性, 不要求页面使用 JavaScript。</p>
 */
public final class ApricityHudBridge implements ApricityHudBackend {

    private Document document;
    private String path;
    private long generation = -1L;
    private CustomHudSnapshot lastSnapshot;

    @Override
    public boolean open(String logicalPath) {
        if (logicalPath == null || logicalPath.isBlank()) return false;
        try {
            if (isOpen() && logicalPath.equals(path)) return true;
            close();
            String template = HTML.getTemple(logicalPath);
            if (template == null) {
                HTML.reload(logicalPath);
                template = HTML.getTemple(logicalPath);
            }
            if (template == null || template.isBlank()) return false;
            Document created = ApricityUI.createDocument(logicalPath);
            if (created == null || created.isDisposed()) return false;
            document = created;
            path = logicalPath;
            generation = created.getRefreshGeneration();
            lastSnapshot = null;
            return true;
        } catch (Throwable ignored) {
            close();
            return false;
        }
    }

    @Override
    public void close() {
        Document current = document;
        document = null;
        path = null;
        generation = -1L;
        lastSnapshot = null;
        if (current == null) return;
        try {
            current.remove();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean isOpen() {
        return document != null && !document.isDisposed();
    }

    @Override
    public String getOpenPath() {
        return path;
    }

    @Override
    public void update(CustomHudSnapshot snapshot) {
        Document current = document;
        if (current == null || current.isDisposed() || snapshot == null) return;
        try {
            long currentGeneration = current.getRefreshGeneration();
            if (currentGeneration != generation) {
                generation = currentGeneration;
                lastSnapshot = null;
            }
            if (snapshot.equals(lastSnapshot)) return;
            Document.runWithContext(current, () -> apply(current, snapshot));
            lastSnapshot = snapshot;
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void reloadTemplate(String logicalPath) {
        if (logicalPath == null || logicalPath.isBlank()) return;
        try {
            HTML.reload(logicalPath);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void reloadTemplates(List<String> logicalPaths) {
        if (logicalPaths == null) return;
        for (String logicalPath : logicalPaths) {
            reloadTemplate(logicalPath);
        }
    }

    private void apply(Document doc, CustomHudSnapshot s) {
        Element body = doc.body;
        if (body != null) {
            setAttr(body, "data-taczfixes", "active");
            setAttr(body, "data-taczfixes-hud", path == null ? "" : path);
            setAttr(body, "data-taczfixes-gun", s.gunId());
            setAttr(body, "data-taczfixes-ammo", intText(s.ammo()));
            setAttr(body, "data-taczfixes-ammo-max", intText(s.ammoMax()));
            setAttr(body, "data-taczfixes-ammo-percent", intText(s.ammoPercent()));
            setAttr(body, "data-taczfixes-ammo-empty", boolText(s.ammoEmpty()));
            setAttr(body, "data-taczfixes-ammo-style", s.ammoStyle());
            setAttr(body, "data-taczfixes-heat", floatText(s.heatValue()));
            setAttr(body, "data-taczfixes-heat-percent", intText(s.heatPercent()));
            setAttr(body, "data-taczfixes-heat-locked", boolText(s.heatLocked()));
            setAttr(body, "data-taczfixes-has-heat", boolText(s.hasHeat()));
            setAttr(body, "data-taczfixes-fire-mode", s.fireMode());
            setAttr(body, "data-taczfixes-aiming", boolText(s.aiming()));
            setAttr(body, "data-taczfixes-aim-progress",
                    intText(Math.round(s.aimProgress() * 100.0F)));
            setAttr(body, "data-taczfixes-reloading", boolText(s.reloading()));
            setAttr(body, "data-taczfixes-offhand-reloading", boolText(s.offhandReloading()));
            setAttr(body, "data-taczfixes-sprinting", boolText(s.sprinting()));
            setAttr(body, "data-taczfixes-hold-breath", boolText(s.holdBreath()));
            setAttr(body, "data-taczfixes-dual-wield", boolText(s.dualWield()));
            setAttr(body, "data-taczfixes-crawling", boolText(s.crawling()));
            setAttr(body, "data-taczfixes-sneaking", boolText(s.sneaking()));
            setAttr(body, "data-taczfixes-show-crosshair", boolText(s.showCrosshair()));
        }

        Element magazine = doc.getElementById("ammo-mag");
        if (magazine != null) {
            setText(magazine, s.ammoText());
            setAttr(magazine, "data-value", intText(s.ammo()));
            setAttr(magazine, "data-max", intText(s.ammoMax()));
            setAttr(magazine, "data-percent", intText(s.ammoPercent()));
            setAttr(magazine, "data-padded", s.ammoPadded());
            setAttr(magazine, "data-empty", boolText(s.ammoEmpty()));
            setAttr(magazine, "data-style", s.ammoStyle());
        }

        Element inventory = doc.getElementById("ammo-inventory");
        if (inventory != null) {
            setText(inventory, s.inventoryText());
            setAttr(inventory, "data-value", intText(s.inventoryAmmo()));
            setAttr(inventory, "data-infinite", boolText(s.inventoryInfinite()));
            setAttr(inventory, "data-hidden", boolText(s.useInventoryAmmo()));
        }

        Element ammoType = doc.getElementById("ammo-type");
        if (ammoType != null) {
            setText(ammoType, s.ammoTypeName());
            setAttr(ammoType, "data-id", s.ammoTypeId());
            setAttr(ammoType, "data-key", s.ammoTypeKey());
        }

        Element ammoIcon = doc.getElementById("ammo-type-icon");
        if (ammoIcon != null) {
            setAttr(ammoIcon, "data-id", s.ammoTypeId());
            if (isTag(ammoIcon, "ITEM")) {
                setText(ammoIcon, s.ammoTypeId());
            } else {
                setAttr(ammoIcon, "data-item", s.ammoTypeId());
            }
        }

        Element fireMode = doc.getElementById("fire-mode");
        if (fireMode != null) {
            setText(fireMode, s.fireMode().toUpperCase(Locale.ROOT));
            setAttr(fireMode, "data-mode", s.fireMode());
        }

        Element fireModeIcon = doc.getElementById("fire-mode-icon");
        if (fireModeIcon != null) {
            setAttr(fireModeIcon, "data-mode", s.fireMode());
            if (isTag(fireModeIcon, "TEXTURE")) {
                setAttr(fireModeIcon, "src", fireModeTexture(s.fireMode()));
            }
        }

        Element gunHud = doc.getElementById("gun-hud");
        if (gunHud != null) {
            setAttr(gunHud, "data-empty", boolText(s.ammoEmpty()));
            if (isTag(gunHud, "TEXTURE")) {
                String texture = s.ammoEmpty() && s.hudEmptyTexture() != null
                        && !s.hudEmptyTexture().isBlank()
                        ? s.hudEmptyTexture() : s.hudTexture();
                if (texture != null && !texture.isBlank()) {
                    setAttr(gunHud, "src", texture);
                }
            }
        }

        Element heatBar = doc.getElementById("heat-bar");
        if (heatBar != null) {
            setAttr(heatBar, "data-active", boolText(s.hasHeat()));
            setAttr(heatBar, "data-value", floatText(s.heatValue()));
            setAttr(heatBar, "data-max", floatText(s.heatMax()));
            setAttr(heatBar, "data-percent", intText(s.heatPercent()));
            setAttr(heatBar, "data-locked", boolText(s.heatLocked()));
        }

        Element heatFill = doc.getElementById("heat-fill");
        if (heatFill != null) {
            setInlineStyle(heatFill, "width", (s.hasHeat() ? s.heatPercent() : 0) + "%");
            setAttr(heatFill, "data-percent", intText(s.heatPercent()));
        }

        Element heatText = doc.getElementById("heat-text");
        if (heatText != null) {
            setText(heatText, s.hasHeat() ? s.heatPercent() + "%" : "--");
        }

        setStatus(doc, "status-aiming", s.aiming());
        setStatus(doc, "status-reloading", s.reloading() || s.offhandReloading());
        setStatus(doc, "status-sprinting", s.sprinting());
        setStatus(doc, "status-hold-breath", s.holdBreath());
        setStatus(doc, "status-dual-wield", s.dualWield());
        setStatus(doc, "status-crawling", s.crawling());
        setStatus(doc, "status-sneaking", s.sneaking());

        Element crosshair = doc.getElementById("crosshair");
        if (crosshair != null) {
            boolean visible = s.showCrosshair() && !s.reloading() && !s.aiming();
            setAttr(crosshair, "data-active", boolText(visible));
            setAttr(crosshair, "data-aiming", boolText(s.aiming()));
            setAttr(crosshair, "data-reloading", boolText(s.reloading()));
        }
    }

    private static void setStatus(Document doc, String id, boolean active) {
        Element element = doc.getElementById(id);
        if (element != null) {
            setAttr(element, "data-active", boolText(active));
        }
    }

    private static void setText(Element element, String value) {
        if (element == null || value == null) return;
        String current = element.getTextContent();
        if (!value.equals(current)) {
            element.setTextContent(value);
        }
    }

    private static void setAttr(Element element, String name, String value) {
        if (element == null || name == null || value == null) return;
        String current = element.getAttribute(name);
        if (!value.equals(current)) {
            element.setAttribute(name, value);
        }
    }

    private static void setInlineStyle(Element element, String property, String value) {
        if (element == null || property == null || value == null) return;
        String current = element.getInlineStylePropertyValue(property);
        if (!value.equals(current)) {
            element.setInlineStyleProperty(property, value);
        }
    }

    private static boolean isTag(Element element, String tag) {
        return element != null && tag.equalsIgnoreCase(element.getNodeName());
    }

    private static String boolText(boolean value) {
        return value ? "true" : "false";
    }

    private static String intText(int value) {
        return Integer.toString(value);
    }

    private static String floatText(float value) {
        return Float.toString(value);
    }

    private static String fireModeTexture(String mode) {
        return switch (mode == null ? "" : mode) {
            case "auto" -> "tacz:textures/hud/fire_mode_auto.png";
            case "burst" -> "tacz:textures/hud/fire_mode_burst.png";
            default -> "tacz:textures/hud/fire_mode_semi.png";
        };
    }
}
