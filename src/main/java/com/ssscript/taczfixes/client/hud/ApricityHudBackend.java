package com.ssscript.taczfixes.client.hud;

import java.util.List;

/**
 * ApricityUI 的隔离接口。所有直接引用 ApricityUI 类的代码都放在
 * {@code client.hud.aui.ApricityHudBridge} 中, 由 {@link ApricityHudSupport}
 * 在确认模组已加载后通过反射实例化, 未安装 ApricityUI 时不会加载该类。
 */
public interface ApricityHudBackend {

    boolean open(String logicalPath);

    void close();

    boolean isOpen();

    String getOpenPath();

    void update(CustomHudSnapshot snapshot);

    void reloadTemplate(String logicalPath);

    void reloadTemplates(List<String> logicalPaths);
}
