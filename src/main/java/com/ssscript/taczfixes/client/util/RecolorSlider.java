package com.ssscript.taczfixes.client.util;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;

/** 调色面板的 0..1 滑块。 */
public class RecolorSlider extends AbstractSliderButton implements RecolorPanelWidget {

    private final Runnable onChange;
    private Runnable onCommit;
    private java.util.function.DoubleFunction<String> label =
            value -> String.format(Locale.US, "%.2f", value);

    public RecolorSlider(int x, int y, int width, int height, double value, Runnable onChange) {
        super(x, y, width, height, Component.empty(), value);
        this.onChange = onChange;
        this.updateMessage();
    }

    /** 自定义数值文本。 */
    public RecolorSlider label(java.util.function.DoubleFunction<String> label) {
        this.label = label;
        this.updateMessage();
        return this;
    }

    /** 松开鼠标时触发(适合触发重建等重操作)。 */
    public RecolorSlider onCommit(Runnable onCommit) {
        this.onCommit = onCommit;
        return this;
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease(mouseX, mouseY);
        if (onCommit != null) {
            onCommit.run();
        }
    }

    public double value() {
        return this.value;
    }

    /** 不触发回调地设置数值(用于切换选中分组时刷新)。 */
    public void setSilently(double value) {
        this.value = Mth.clamp(value, 0.0D, 1.0D);
        this.updateMessage();
    }

    @Override
    protected void updateMessage() {
        this.setMessage(Component.literal(label.apply(this.value)));
    }

    @Override
    protected void applyValue() {
        this.onChange.run();
    }
}
