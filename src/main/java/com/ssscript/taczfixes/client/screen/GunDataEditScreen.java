package com.ssscript.taczfixes.client.screen;

import com.google.gson.JsonParser;
import com.ssscript.taczfixes.common.util.GunDataEditorHelper;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class GunDataEditScreen extends Screen {
    private final String initial;
    private String content;
    private int cursor = 0;
    private int scroll = 0;
    private int hScroll = 0;
    private boolean followCaret = true;
    private boolean triedConfirm = false;
    private boolean modified = false;
    private Button confirmButton = null;
    private boolean draggingScroll = false;
    private int selectionStart = 0;
    private int selectionEnd = 0;
    private boolean mouseDragSelecting = false;
    private java.util.List<String> undoStack = new java.util.ArrayList<>();
    private java.util.List<String> redoStack = new java.util.ArrayList<>();


    private static final int LINE_H = 11;
    private int editW = 360;
    private int editH = 240;

    public GunDataEditScreen() {
        super(Component.translatable("gui.taczfixes.edit_data.title"));
        ItemStack held = Minecraft.getInstance().player == null
                ? ItemStack.EMPTY : Minecraft.getInstance().player.getMainHandItem();
        String text = GunDataEditorHelper.currentGunDataText(held);
        this.initial = text == null ? "{}" : text;
        this.content = this.initial;
    }

    private int topLeftX() {
        return (this.width - this.editW) / 2;
    }

    private int topLeftY() {
        return (this.height - this.editH) / 2;
    }

    @Override
    protected void init() {
        this.editW = Math.min(this.width - 80, 500);
        this.editH = Math.min(this.height - 110, 340);
        int right = this.width / 2 + this.editW / 2 - 6;
        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.taczfixes.edit_data.cancel"), b -> this.cancel())
                .bounds(right - 120, this.height - 28, 56, 16).build());
        this.confirmButton = new Button.Builder(Component.translatable("gui.taczfixes.edit_data.confirm"), b -> this.confirm())
                .bounds(right - 58, this.height - 28, 56, 16).build();
        this.confirmButton.active = this.modified;
        this.addRenderableWidget(this.confirmButton);
    }

    private void clearWidgetFocus() {
        this.setFocused(null);
        if (this.confirmButton != null) {
            this.confirmButton.setFocused(false);
        }
    }

    private void confirm() {
        if (!this.modified) {
            return;
        }
        GunData gunData = GunDataEditorHelper.parseGunData(this.content);
        if (gunData == null) {
            this.triedConfirm = true;
            this.clearWidgetFocus();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.hasPermissions(2)) {
            this.triedConfirm = true;
            this.clearWidgetFocus();
            return;
        }
        ItemStack held = mc.player.getMainHandItem();
        com.tacz.guns.api.item.IGun gun = com.tacz.guns.api.item.IGun.getIGunOrNull(held);
        ResourceLocation gunId = gun == null ? null : gun.getGunId(held);
        if (gun == null || gunId == null) {
            this.triedConfirm = true;
            this.clearWidgetFocus();
            return;
        }
        // 客户端本地立即生效(视觉/单机反馈), 服务器端由包处理(应用+暂存持久化)
        ResourceLocation dataId = com.ssscript.taczfixes.common.data.TaczFixesDataManager.resolveDataId(gunId);
        GunDataEditorHelper.applyTaczFixes(dataId, this.content);
        if (GunDataEditorHelper.applyByGunId(gunId, this.content)) {
            // 本地同样写入源枪包(多人联机时本机包独立于服务器)
            if (com.ssscript.taczfixes.common.util.GunDataOverrideStorage.save(dataId, this.content)) {
                com.ssscript.taczfixes.common.util.GunDataOverrideStorage.applyAll();
            }
            com.ssscript.taczfixes.common.network.NetworkHandler.CHANNEL.sendToServer(
                    new com.ssscript.taczfixes.common.network.ClientMessageApplyGunData(gunId, this.content));
            com.ssscript.taczfixes.client.util.CenterMessage.show(
                    Component.translatable("gui.taczfixes.edit_data.saved"));
            this.onClose();
        } else {
            this.triedConfirm = true;
            this.clearWidgetFocus();
        }
    }

    private void cancel() {
        this.onClose();
    }

    private void markModified() {
        this.modified = true;
        if (this.confirmButton != null) {
            this.confirmButton.active = true;
        }
    }

    private void snapshotUndo() {
        this.undoStack.add(this.content);
        if (this.undoStack.size() > 100) {
            this.undoStack.remove(0);
        }
        this.redoStack.clear();
    }

    private boolean hasSelection() {
        return selectionStart != selectionEnd;
    }

    private int selectionLow() {
        return Math.min(selectionStart, selectionEnd);
    }

    private int selectionHigh() {
        return Math.max(selectionStart, selectionEnd);
    }

    /** 删除选区并返回其长. 无选区返回0. */
    private int deleteSelection() {
        if (!hasSelection()) return 0;
        int low = selectionLow();
        int high = selectionHigh();
        this.content = this.content.substring(0, low) + this.content.substring(high);
        this.cursor = low;
        clearSelection();
        markModified();
        return high - low;
    }

    private void clearSelection() {
        selectionStart = cursor;
        selectionEnd = cursor;
    }

    private String[] lines() {
        return this.content.split("\n", -1);
    }

    /** 光标所在逻辑行列(字符索引)。 */
    private int[] cursorPos() {
        String[] lines = lines();
        int index = this.cursor;
        for (int i = 0; i < lines.length; i++) {
            int len = lines[i].length();
            if (index <= len) {
                return new int[]{i, index};
            }
            index -= len + 1;
        }
        return new int[]{lines.length - 1, index};
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int fx = topLeftX();
        int fy = topLeftY();
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        graphics.fill(fx - 2, fy - 2, fx + this.editW + 2, fy + this.editH + 2, 0xFF202020);
        graphics.fill(fx, fy, fx + this.editW, fy + this.editH, 0xFF141414);

        int[] pos = cursorPos();
        String[] logicalLines = lines();
        int maxLines = (this.editH - 8) / LINE_H;
        int topLimit = Math.max(0, logicalLines.length - maxLines);
        int textW = this.font.width(logicalLines[Math.min(pos[0], logicalLines.length - 1)]
                .substring(0, Math.min(pos[1], logicalLines[Math.min(pos[0], logicalLines.length - 1)].length())));
        int maxHS = 0;
        for (String l : logicalLines) {
            maxHS = Math.max(maxHS, this.font.width(l) - (this.editW - 8));
        }
        maxHS = Math.max(0, maxHS);
        if (this.followCaret) {
            if (pos[0] < this.scroll) {
                this.scroll = pos[0];
            } else if (pos[0] >= this.scroll + maxLines) {
                this.scroll = pos[0] - maxLines + 1;
            }
            this.scroll = Math.max(0, Math.min(this.scroll, topLimit));
            // 光标水平保持可见(左右边界)
            if (textW - this.hScroll > this.editW - 8) {
                this.hScroll = textW - (this.editW - 8);
            } else if (textW - this.hScroll < 0) {
                this.hScroll = Math.max(0, textW);
            }
        }
        this.hScroll = Math.max(0, Math.min(this.hScroll, maxHS));

        graphics.enableScissor(fx, fy, fx + this.editW, fy + this.editH);
        for (int i = 0; i < maxLines; i++) {
            int lineIndex = i + this.scroll;
            if (lineIndex >= logicalLines.length) break;
            String lineText = logicalLines[lineIndex];
            int y = fy + 4 + i * LINE_H;
            if (hasSelection()) {
                int rowStart = lineStart(lineIndex);
                int rowEnd = rowStart + lineText.length();
                int selLow = Math.max(rowStart, selectionLow());
                int selHigh = Math.min(rowEnd, selectionHigh());
                if (selHigh > selLow) {
                    int xStart = fx + 4 + this.font.width(lineText.substring(0, selLow - rowStart)) - this.hScroll;
                    int xEnd = fx + 4 + this.font.width(lineText.substring(0, selHigh - rowStart)) - this.hScroll;
                    graphics.fill(xStart, y, xEnd, y + 10, 0xFF3355CC);
                }
            }
            graphics.drawString(this.font, lineText, fx + 4 - this.hScroll, y, 0xFFE0E0E0);
        }
        graphics.disableScissor();

        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        if (blink) {
            int visualRow = pos[0] - this.scroll;
            if (visualRow >= 0 && visualRow < maxLines) {
                String lineText = logicalLines[pos[0]];
                int cx = fx + 4 + this.font.width(lineText.substring(0, Math.min(pos[1], lineText.length()))) - this.hScroll;
                int cy = fy + 4 + visualRow * LINE_H;
                graphics.fill(cx, cy, cx + 1, cy + 9, 0xFFFFFFFF);
            }
        }

        if (this.triedConfirm) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.taczfixes.edit_data.invalid").withStyle(ChatFormatting.RED),
                    this.width / 2, this.height - 24, 0xFFFF5555);
        }
        renderScrollbar(graphics);
        // 持续清除按钮焦点(仿照本模组其它界面做法), 避免确认失败后焦点停留在按钮上
        this.setFocused(null);
        if (this.confirmButton != null) {
            this.confirmButton.setFocused(false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int scrollbarX() {
        return topLeftX() + this.editW + 4;
    }

    private int scrollbarTop() {
        return topLeftY();
    }

    private int scrollbarHeight() {
        return this.editH;
    }

    /** 轨道/滑块导航. */
    private void renderScrollbar(GuiGraphics graphics) {
        String[] lines = lines();
        int maxLines = (this.editH - 8) / LINE_H;
        int total = Math.max(1, lines.length);
        int trackY = scrollbarTop();
        int trackH = scrollbarHeight();
        int x = scrollbarX();
        graphics.fill(x, trackY, x + 6, trackY + trackH, 0xFF303030);
        int trackH2 = Math.max(12, (int) ((double) maxLines / total * trackH));
        int span = Math.max(0, total - maxLines);
        int thumbY = span <= 0 ? trackY : trackY + (int) ((double) this.scroll / span * (trackH - trackH2));
        graphics.fill(x + 1, thumbY, x + 5, thumbY + trackH2, 0xFF808080);
    }

    private boolean inScrollbar(double mouseX, double mouseY) {
        return mouseX >= scrollbarX() - 4 && mouseX <= scrollbarX() + 10
                && mouseY >= scrollbarTop() && mouseY <= scrollbarTop() + scrollbarHeight();
    }

    /** 根据Y在滑轨上的位置换算 scroll, 并返回本次平移量. */
    private int scrollFromThumb(double mouseY) {
        String[] lines = lines();
        int maxLines = (this.editH - 8) / LINE_H;
        int total = Math.max(1, lines.length);
        int span = Math.max(0, total - maxLines);
        if (span <= 0) return 0;
        double ratio = (mouseY - scrollbarTop()) / (double) scrollbarHeight();
        return (int) Math.round(Math.max(0, Math.min(ratio, 1.0)) * span);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inScrollbar(mouseX, mouseY)) {
            this.draggingScroll = true;
            this.followCaret = false;
            this.scroll = scrollFromThumb(mouseY);
            return true;
        }
        int fx = topLeftX();
        int fy = topLeftY();
        boolean inBox = mouseX >= fx && mouseX <= fx + this.editW
                && mouseY >= fy && mouseY <= fy + this.editH;
        if (!inBox) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int maxLines = (this.editH - 8) / LINE_H;
        int visualRow = (int) ((mouseY - fy - 4) / LINE_H);
        int lineIndex = this.scroll + visualRow;
        String[] logical = lines();
        if (lineIndex < 0 || lineIndex >= logical.length) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        String lineText = logical[lineIndex];
        int clickX = (int) (mouseX - fx - 4) + this.hScroll;
        int column = 0;
        if (clickX <= 0) {
            column = 0;
        } else {
            for (int i = 0; i < lineText.length(); i++) {
                if (this.font.width(lineText.substring(0, i + 1)) >= clickX) {
                    column = i + 1;
                    break;
                }
            }
            if (column == 0) {
                column = lineText.length();
            } else if (this.font.width(lineText.substring(0, column)) >= clickX) {
                // 落点处于字符左半: 取前一列
                if (column > 0 && this.font.width(lineText.substring(0, column - 1))
                        >= clickX - this.font.width(lineText.substring(column - 1, column)) / 2) {
                    column = Math.max(0, column - 1);
                }
            }
        }
        int index = lineStart(lineIndex) + Math.min(column, lineText.length());
        this.cursor = Math.min(this.content.length(), index);
        this.clearSelection();
        this.mouseDragSelecting = true;
        this.followCaret = true;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingScroll) {
            this.scroll = scrollFromThumb(mouseY);
            return true;
        }
        if (this.mouseDragSelecting) {
            int fx = topLeftX();
            int fy = topLeftY();
            String[] logical = lines();
            int maxLines = (this.editH - 8) / LINE_H;
            int visualRow = (int) ((mouseY - fy - 4) / LINE_H);
            int lineIndex = Math.max(0, Math.min(logical.length - 1, this.scroll + visualRow));
            String lineText = logical[lineIndex];
            int clickX = (int) (mouseX - fx - 4) + this.hScroll;
            int column = 0;
            for (int i = 0; i < lineText.length(); i++) {
                if (this.font.width(lineText.substring(0, i + 1)) >= clickX) {
                    column = i + 1;
                    break;
                }
            }
            if (column == 0) column = lineText.length();
            int index = Math.min(this.content.length(), lineStart(lineIndex) + Math.min(column, lineText.length()));
            this.cursor = index;
            this.selectionEnd = index;
            this.followCaret = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.draggingScroll) {
            this.draggingScroll = false;
            return true;
        }
        this.mouseDragSelecting = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        String[] lines = lines();
        if (this.hasShiftDown()) {
            int steps = (int) Math.round(delta * 20);
            int maxHS = 0;
            for (String l : lines) {
                maxHS = Math.max(maxHS, this.font.width(l) - (this.editW - 8));
            }
            maxHS = Math.max(0, maxHS);
            this.hScroll = Math.max(0, Math.min(this.hScroll + steps, maxHS));
            return true;
        }
        this.followCaret = false;
        int maxLines = (this.editH - 8) / LINE_H;
        int steps = (int) Math.round(delta * 3);
        this.scroll = Math.max(0, Math.min(this.scroll - steps, Math.max(0, lines.length - maxLines)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_C: {
                    if (hasSelection()) {
                        String selected = this.content.substring(selectionLow(), selectionHigh());
                        Minecraft.getInstance().keyboardHandler.setClipboard(selected);
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_X: {
                    if (hasSelection()) {
                        String selected = this.content.substring(selectionLow(), selectionHigh());
                        Minecraft.getInstance().keyboardHandler.setClipboard(selected);
                        snapshotUndo();
                        deleteSelection();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_V: {
                    String pasted = Minecraft.getInstance().keyboardHandler.getClipboard();
                    if (pasted != null && !pasted.isEmpty()) {
                        this.followCaret = true;
                        snapshotUndo();
                        if (hasSelection()) {
                            int low = selectionLow();
                            int high = selectionHigh();
                            this.content = this.content.substring(0, low) + pasted + this.content.substring(high);
                            this.cursor = low + pasted.length();
                            clearSelection();
                        } else {
                            this.content = this.content.substring(0, this.cursor) + pasted
                                    + this.content.substring(this.cursor);
                            this.cursor += pasted.length();
                        }
                        markModified();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_A: {
                    this.selectionStart = 0;
                    this.selectionEnd = this.content.length();
                    this.cursor = this.content.length();
                    return true;
                }
                case GLFW.GLFW_KEY_Z: {
                    if (!this.undoStack.isEmpty()) {
                        this.redoStack.add(this.content);
                        this.content = this.undoStack.remove(this.undoStack.size() - 1);
                        this.cursor = Math.min(this.cursor, this.content.length());
                        clearSelection();
                        markModified();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_Y: {
                    if (!this.redoStack.isEmpty()) {
                        this.undoStack.add(this.content);
                        this.content = this.redoStack.remove(this.redoStack.size() - 1);
                        this.cursor = Math.min(this.cursor, this.content.length());
                        clearSelection();
                        markModified();
                    }
                    return true;
                }
                default:
                    return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            taczfixes$insertIndentedNewline();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            this.followCaret = true;
            snapshotUndo();
            String indent = "  ";
            if (hasSelection()) {
                int low = selectionLow();
                int high = selectionHigh();
                this.content = this.content.substring(0, low) + indent + this.content.substring(high);
                this.cursor = low + indent.length();
                clearSelection();
            } else {
                this.content = this.content.substring(0, this.cursor) + indent
                        + this.content.substring(this.cursor);
                this.cursor += indent.length();
            }
            markModified();
            return true;
        }
        this.followCaret = true;
        String[] lines = lines();
        int[] pos = cursorPos();
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT: {
                this.cursor = hasSelection() ? selectionLow() : Math.max(0, this.cursor - 1);
                clearSelection();
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT: {
                this.cursor = hasSelection() ? selectionHigh() : Math.min(this.content.length(), this.cursor + 1);
                clearSelection();
                return true;
            }
            case GLFW.GLFW_KEY_UP: {
                int desired = pos[1];
                int targetLine = Math.max(0, pos[0] - 1);
                this.cursor = lineStart(targetLine) + Math.min(desired, lines[targetLine].length());
                clearSelection();
                return true;
            }
            case GLFW.GLFW_KEY_DOWN: {
                int desired = pos[1];
                int targetLine = Math.min(lines.length - 1, pos[0] + 1);
                this.cursor = lineStart(targetLine) + Math.min(desired, lines[targetLine].length());
                clearSelection();
                return true;
            }
            case GLFW.GLFW_KEY_HOME: {
                this.cursor = lineStart(pos[0]);
                clearSelection();
                return true;
            }
            case GLFW.GLFW_KEY_END: {
                this.cursor = lineEnd(pos[0]);
                clearSelection();
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE: {
                this.followCaret = true;
                snapshotUndo();
                if (hasSelection()) {
                    deleteSelection();
                    return true;
                }
                if (this.cursor > 0) {
                    this.content = this.content.substring(0, this.cursor - 1)
                            + this.content.substring(this.cursor);
                    this.cursor--;
                    markModified();
                } else {
                    this.redoStack.clear();
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE: {
                this.followCaret = true;
                snapshotUndo();
                if (hasSelection()) {
                    deleteSelection();
                    return true;
                }
                if (this.cursor < this.content.length()) {
                    this.content = this.content.substring(0, this.cursor)
                            + this.content.substring(this.cursor + 1);
                    markModified();
                } else {
                    this.redoStack.clear();
                }
                return true;
            }
            default:
                return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    private int lineStart(int lineIndex) {
        String[] lines = lines();
        int index = 0;
        for (int i = 0; i < lineIndex && i < lines.length; i++) {
            index += lines[i].length() + 1;
        }
        return index;
    }

    private int lineEnd(int lineIndex) {
        String[] lines = lines();
        return lineStart(lineIndex) + lines[Math.min(lineIndex, lines.length - 1)].length();
    }

    /** 回车换行时自动补上当前行的缩进; 若光标前最后一个非空白字符是 { 或 [ 则额外补一级缩进。 */
    private void taczfixes$insertIndentedNewline() {
        this.followCaret = true;
        snapshotUndo();
        int[] pos = cursorPos();
        String[] lines = lines();
        String line = lines[Math.min(pos[0], lines.length - 1)];
        int col = Math.min(pos[1], line.length());
        int indentEnd = 0;
        while (indentEnd < col && (line.charAt(indentEnd) == ' ' || line.charAt(indentEnd) == '\t')) {
            indentEnd++;
        }
        String indent = line.substring(0, indentEnd);
        int before = this.cursor - 1;
        while (before >= 0 && (this.content.charAt(before) == ' ' || this.content.charAt(before) == '\t')) {
            before--;
        }
        if (before >= 0 && (this.content.charAt(before) == '{' || this.content.charAt(before) == '[')) {
            indent = indent + "  ";
        }
        String insert = "\n" + indent;
        if (hasSelection()) {
            int low = selectionLow();
            int high = selectionHigh();
            this.content = this.content.substring(0, low) + insert + this.content.substring(high);
            this.cursor = low + insert.length();
            clearSelection();
        } else {
            this.content = this.content.substring(0, this.cursor) + insert + this.content.substring(this.cursor);
            this.cursor += insert.length();
        }
        markModified();
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (codePoint == '\r' || codePoint == '\n') {
            taczfixes$insertIndentedNewline();
            return true;
        }
        char insertChar;
        if (codePoint >= 32) {
            insertChar = codePoint;
        } else {
            return super.charTyped(codePoint, modifiers);
        }
        this.followCaret = true;
        snapshotUndo();
        if (hasSelection()) {
            int low = selectionLow();
            int high = selectionHigh();
            this.content = this.content.substring(0, low) + insertChar + this.content.substring(high);
            this.cursor = low + 1;
            clearSelection();
        } else {
            this.content = this.content.substring(0, this.cursor) + insertChar
                    + this.content.substring(this.cursor);
            this.cursor++;
        }
        markModified();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
