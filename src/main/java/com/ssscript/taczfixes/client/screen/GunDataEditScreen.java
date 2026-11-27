package com.ssscript.taczfixes.client.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.ssscript.taczfixes.common.util.AttachmentDataEditorHelper;
import com.ssscript.taczfixes.common.util.GunDataEditorHelper;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * 枪械/配件数据编辑器。深色代码编辑器风格: 头部信息条 + 行号栏 + JSON 语法着色 + 当前行高亮 +
 * 底部状态栏, 数据校验状态实时显示在头部右侧。
 */
public class GunDataEditScreen extends Screen {
    private static final int LINE_H = 11;
    private static final int HEADER_H = 32;
    private static final int FOOTER_H = 22;

    // 面板配色
    private static final int COLOR_PANEL_TOP = 0xFF1A1F27;
    private static final int COLOR_PANEL_BOTTOM = 0xFF12151B;
    private static final int COLOR_BORDER = 0xFF2E3643;
    private static final int COLOR_BORDER_LIGHT = 0xFF3E4858;
    private static final int COLOR_HEADER = 0xFF1F2530;
    private static final int COLOR_HEADER_LINE = 0xFF2C3441;
    private static final int COLOR_ACCENT = 0xFFE0A94A;
    private static final int COLOR_TEXT = 0xFFEDF1F7;
    private static final int COLOR_TEXT_DIM = 0xFF8B95A6;
    private static final int COLOR_TEXT_FAINT = 0xFF5C6675;
    private static final int COLOR_GUTTER_BG = 0xFF151920;
    private static final int COLOR_EDITOR_BG = 0xFF0F1217;
    private static final int COLOR_CURRENT_LINE = 0x227FB0D8;
    private static final int COLOR_SELECTION = 0x665073B0;
    private static final int COLOR_SCROLL_TRACK = 0xFF20252E;
    private static final int COLOR_SCROLL_THUMB = 0xFF4E5868;
    private static final int COLOR_SCROLL_THUMB_HOVER = 0xFF76839A;
    private static final int COLOR_VALID = 0xFF7BC98A;
    private static final int COLOR_INVALID = 0xFFFF6B6B;
    private static final int COLOR_MODIFIED = 0xFFE0A94A;
    private static final int COLOR_CARET = 0xFFF2F5FA;

    // JSON 语法配色
    private static final int COLOR_SYNTAX_KEY = 0xFF7FB7E8;
    private static final int COLOR_SYNTAX_STRING = 0xFF9FD39A;
    private static final int COLOR_SYNTAX_NUMBER = 0xFFE8B479;
    private static final int COLOR_SYNTAX_LITERAL = 0xFFC99AE8;
    private static final int COLOR_SYNTAX_BRACKET = 0xFFB8C4D9;
    private static final int COLOR_SYNTAX_PUNCT = 0xFF7C8798;

    private final String initial;
    private String content;
    private final ItemStack heldStack;
    private final String itemName;
    private final String dataIdText;
    private final boolean editingAttachment;
    private boolean contentValid = true;
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

    private int editW = 360;
    private int editH = 240;

    public GunDataEditScreen() {
        super(Component.translatable("gui.taczfixes.edit_data.title"));
        ItemStack held = Minecraft.getInstance().player == null
                ? ItemStack.EMPTY : Minecraft.getInstance().player.getMainHandItem();
        this.heldStack = held;
        this.editingAttachment = held.getItem() instanceof IAttachment;
        ResourceLocation dataId;
        if (this.editingAttachment) {
            dataId = AttachmentDataEditorHelper.attachmentIdOf(held);
        } else {
            IGun gun = IGun.getIGunOrNull(held);
            dataId = gun == null ? null : gun.getGunId(held);
        }
        this.dataIdText = dataId == null ? "" : dataId.toString();
        this.itemName = held.isEmpty() ? "" : held.getHoverName().getString();
        String text = this.editingAttachment
                ? AttachmentDataEditorHelper.currentAttachmentDataText(held)
                : GunDataEditorHelper.currentGunDataText(held);
        this.initial = text == null ? "{}" : text;
        this.content = this.initial;
        this.contentValid = isJsonObject(this.content);
    }

    // ---------------------------------------------------------------- 布局

    private int topLeftX() {
        return (this.width - this.editW) / 2;
    }

    private int topLeftY() {
        return (this.height - this.editH) / 2;
    }

    private int gutterW() {
        int count = lines().length;
        return Math.max(this.font.width("000"), this.font.width(String.valueOf(count))) + 10;
    }

    private int gutterLeft() {
        return topLeftX() + 8;
    }

    private int gutterRight() {
        return gutterLeft() + gutterW();
    }

    private int dividerX() {
        return gutterRight() + 2;
    }

    private int textLeft() {
        return dividerX() + 4;
    }

    private int scrollX() {
        return topLeftX() + this.editW - 10;
    }

    private int textTop() {
        return topLeftY() + HEADER_H + 4;
    }

    private int textWidth() {
        return Math.max(24, scrollX() - 4 - textLeft());
    }

    private int textHeight() {
        return Math.max(LINE_H, this.editH - HEADER_H - FOOTER_H - 10);
    }

    private int maxLines() {
        return Math.max(1, textHeight() / LINE_H);
    }

    @Override
    protected void init() {
        this.editW = Math.min(this.width - 80, 520);
        this.editH = Math.max(90, Math.min(this.height - 90, 380));
        int right = topLeftX() + this.editW;
        this.addRenderableWidget(new Button.Builder(
                Component.translatable("gui.taczfixes.edit_data.cancel"), b -> this.cancel())
                .bounds(right - 136, this.height - 26, 64, 18).build());
        this.confirmButton = new Button.Builder(
                Component.translatable("gui.taczfixes.edit_data.confirm"), b -> this.confirm())
                .bounds(right - 68, this.height - 26, 64, 18).build();
        this.confirmButton.active = this.modified;
        this.addRenderableWidget(this.confirmButton);
    }

    private void clearWidgetFocus() {
        this.setFocused(null);
        if (this.confirmButton != null) {
            this.confirmButton.setFocused(false);
        }
    }

    // ---------------------------------------------------------------- 保存

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
        if (held.getItem() instanceof IAttachment) {
            ResourceLocation attachmentId = AttachmentDataEditorHelper.attachmentIdOf(held);
            if (attachmentId == null || !AttachmentDataEditorHelper.validateAttachmentData(this.content)) {
                this.triedConfirm = true;
                this.clearWidgetFocus();
                return;
            }
            ResourceLocation dataId = AttachmentDataEditorHelper.resolveDataId(attachmentId);
            AttachmentDataEditorHelper.applyTaczFixes(dataId, this.content);
            if (com.ssscript.taczfixes.common.util.AttachmentDataOverrideStorage.save(dataId, this.content)) {
                com.ssscript.taczfixes.common.util.AttachmentDataOverrideStorage.applyAll();
            }
            com.ssscript.taczfixes.common.network.NetworkHandler.CHANNEL.sendToServer(
                    new com.ssscript.taczfixes.common.network.ClientMessageApplyAttachmentData(
                            attachmentId, this.content));
            com.ssscript.taczfixes.client.util.CenterMessage.show(
                    Component.translatable("gui.taczfixes.edit_data.saved"));
            this.onClose();
            return;
        }
        IGun gun = IGun.getIGunOrNull(held);
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
        this.triedConfirm = false;
        this.contentValid = isJsonObject(this.content);
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

    // ---------------------------------------------------------------- 文本工具

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

    /** 语法/状态标记都用它: 文本是否为合法 JSON 对象(轻量, 不做语义校验)。 */
    private static boolean isJsonObject(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        try {
            JsonElement element = JsonParser.parseString(text);
            return element != null && element.isJsonObject();
        } catch (Exception ex) {
            return false;
        }
    }

    /** 仅用于显示: 制表符展开为两个空格, 其它控制字符替换为空格, 避免字体渲染成缺字方框. */
    private static String displayText(String text) {
        StringBuilder result = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String replacement = c == '\t' ? "  " : (Character.isISOControl(c) ? " " : null);
            if (replacement != null) {
                if (result == null) {
                    result = new StringBuilder(text.length() + 8);
                    result.append(text, 0, i);
                }
                result.append(replacement);
            } else if (result != null) {
                result.append(c);
            }
        }
        return result == null ? text : result.toString();
    }

    private int displayWidth(String text, int endIndex) {
        return this.font.width(displayText(text.substring(0, Math.max(0, Math.min(endIndex, text.length())))));
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

    /** 鼠标 X 落在该行的第几列(字符数)。 */
    private int columnAt(String lineText, int targetX) {
        if (targetX <= 0) {
            return 0;
        }
        int x = 0;
        for (int i = 0; i < lineText.length(); i++) {
            char c = lineText.charAt(i);
            String glyph = c == '\t' ? "  " : Character.toString(Character.isISOControl(c) ? ' ' : c);
            int w = this.font.width(glyph);
            if (x + w > targetX) {
                return (targetX - x < x + w - targetX) ? i : i + 1;
            }
            x += w;
        }
        return lineText.length();
    }

    private String trim(String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        int budget = maxWidth - this.font.width("...");
        if (budget <= 0) {
            return "...";
        }
        int x = 0;
        int end = 0;
        while (end < text.length()) {
            int w = this.font.width(Character.toString(text.charAt(end)));
            if (x + w > budget) {
                break;
            }
            x += w;
            end++;
        }
        return text.substring(0, end) + "...";
    }

    private static int lerpColor(int from, int to, float t) {
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ---------------------------------------------------------------- 渲染

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int fx = topLeftX();
        int fy = topLeftY();
        drawPanel(graphics, fx, fy);
        int tx = textLeft();
        int ty = textTop();
        int tw = textWidth();
        int th = textHeight();
        int maxL = maxLines();
        int[] pos = cursorPos();
        String[] logicalLines = lines();
        int topLimit = Math.max(0, logicalLines.length - maxL);
        String caretLine = logicalLines[Math.min(pos[0], logicalLines.length - 1)];
        int caretOffset = displayWidth(caretLine, Math.min(pos[1], caretLine.length()));
        int maxHS = 0;
        for (String line : logicalLines) {
            maxHS = Math.max(maxHS, this.font.width(displayText(line)) - tw);
        }
        maxHS = Math.max(0, maxHS);
        if (this.followCaret) {
            if (pos[0] < this.scroll) {
                this.scroll = pos[0];
            } else if (pos[0] >= this.scroll + maxL) {
                this.scroll = pos[0] - maxL + 1;
            }
            this.scroll = Math.max(0, Math.min(this.scroll, topLimit));
            // 光标水平保持可见(左右边界)
            if (caretOffset - this.hScroll > tw) {
                this.hScroll = caretOffset - tw;
            } else if (caretOffset - this.hScroll < 0) {
                this.hScroll = Math.max(0, caretOffset);
            }
        }
        this.scroll = Math.max(0, Math.min(this.scroll, topLimit));
        this.hScroll = Math.max(0, Math.min(this.hScroll, maxHS));

        // 编辑区底色
        graphics.fill(gutterLeft(), ty, tx + tw + 2, ty + th, COLOR_EDITOR_BG);
        graphics.enableScissor(tx, ty, tx + tw, ty + th);
        for (int i = 0; i < maxL; i++) {
            int lineIndex = i + this.scroll;
            if (lineIndex >= logicalLines.length) {
                break;
            }
            String lineText = logicalLines[lineIndex];
            int y = ty + i * LINE_H + 1;
            if (lineIndex == pos[0]) {
                graphics.fill(tx, y - 1, tx + tw, y + LINE_H - 2, COLOR_CURRENT_LINE);
            }
            drawSelection(graphics, lineIndex, lineText, tx, y);
            drawHighlightedLine(graphics, lineText, tx - this.hScroll, y);
        }
        graphics.disableScissor();

        drawGutter(graphics, logicalLines.length, ty, maxL, pos[0]);
        graphics.fill(dividerX(), ty, dividerX() + 1, ty + th, COLOR_BORDER);
        drawCaret(graphics, tx, ty, caretOffset);
        renderScrollbar(graphics, mouseX, mouseY);
        drawHeader(graphics, fx, fy);
        drawFooter(graphics, fx, fy);
        int titleY = fy - 16;
        if (titleY >= 4) {
            graphics.drawCenteredString(this.font, this.title, this.width / 2, titleY, COLOR_TEXT_DIM);
        }
        // 持续清除按钮焦点(仿照本模组其它界面做法), 避免确认失败后焦点停留在按钮上
        this.setFocused(null);
        if (this.confirmButton != null) {
            this.confirmButton.setFocused(false);
        }
    }

    /** 面板: 阴影 + 描边 + 纵向渐变底色。 */
    private void drawPanel(GuiGraphics graphics, int fx, int fy) {
        int fw = this.editW;
        int fh = this.editH;
        graphics.fill(fx - 6, fy - 6, fx + fw + 6, fy + fh + 6, 0x30000000);
        graphics.fill(fx - 3, fy - 3, fx + fw + 3, fy + fh + 3, 0x50000000);
        graphics.fill(fx - 1, fy - 1, fx + fw + 1, fy + fh + 1, COLOR_BORDER_LIGHT);
        graphics.fill(fx, fy, fx + fw, fy + fh, COLOR_BORDER);
        int strips = 8;
        int stripH = Math.max(1, fh / strips);
        for (int i = 0; i < strips; i++) {
            int y0 = fy + 1 + i * stripH;
            int y1 = i == strips - 1 ? fy + fh - 1 : y0 + stripH;
            float t = (float) i / (strips - 1);
            graphics.fill(fx + 1, y0, fx + fw - 1, y1, lerpColor(COLOR_PANEL_TOP, COLOR_PANEL_BOTTOM, t));
        }
    }

    /** 头部: 物品图标 + 名称/数据 id + 右侧校验与修改状态。 */
    private void drawHeader(GuiGraphics graphics, int fx, int fy) {
        int fw = this.editW;
        graphics.fill(fx + 1, fy + 1, fx + fw - 1, fy + HEADER_H, COLOR_HEADER);
        graphics.fill(fx + 1, fy + HEADER_H, fx + fw - 1, fy + HEADER_H + 1, COLOR_HEADER_LINE);
        // 左侧强调条
        graphics.fill(fx + 1, fy + 1, fx + 4, fy + HEADER_H, COLOR_ACCENT);
        int textX = fx + 12;
        if (!this.heldStack.isEmpty()) {
            graphics.renderItem(this.heldStack, fx + 12, fy + 8);
            textX += 22;
        }
        int badgeRight = fx + fw - 12;
        Component validText = Component.translatable(this.contentValid
                ? "gui.taczfixes.edit_data.json_ok" : "gui.taczfixes.edit_data.json_invalid");
        int validW = this.font.width(validText.getString());
        int validX = badgeRight - validW;
        int lineY = fy + (HEADER_H - 9) / 2;
        int validColor = this.contentValid ? COLOR_VALID : COLOR_INVALID;
        graphics.fill(validX - 7, lineY + 3, validX - 3, lineY + 7, validColor);
        graphics.drawString(this.font, validText.getString(), validX, lineY, validColor, true);
        int available = validX - 18 - textX;
        if (this.modified) {
            Component modText = Component.translatable("gui.taczfixes.edit_data.modified");
            int modW = this.font.width(modText.getString());
            int modX = validX - 16 - modW;
            graphics.fill(modX - 7, lineY + 3, modX - 3, lineY + 7, COLOR_MODIFIED);
            graphics.drawString(this.font, modText.getString(), modX, lineY, COLOR_MODIFIED, true);
            available = modX - 18 - textX;
        }
        String titleLine = this.itemName.isEmpty() ? this.title.getString() : this.itemName;
        graphics.drawString(this.font, trim(titleLine, available), textX, fy + 7, COLOR_TEXT, true);
        String kind = Component.translatable(this.editingAttachment
                ? "gui.taczfixes.edit_data.kind_attachment" : "gui.taczfixes.edit_data.kind_gun").getString();
        String sub = this.dataIdText.isEmpty() ? kind : kind + " · " + this.dataIdText;
        graphics.drawString(this.font, trim(sub, available), textX, fy + 18, COLOR_TEXT_DIM, true);
    }

    /** 底部状态栏: 快捷键提示 / 校验失败提示 + 行列位置。 */
    private void drawFooter(GuiGraphics graphics, int fx, int fy) {
        int fw = this.editW;
        int fh = this.editH;
        int topY = fy + fh - FOOTER_H;
        graphics.fill(fx + 1, topY, fx + fw - 1, topY + 1, COLOR_HEADER_LINE);
        int textY = topY + (FOOTER_H - 9) / 2;
        int leftX = fx + 12;
        int[] pos = cursorPos();
        String position = Component.translatable("gui.taczfixes.edit_data.position",
                String.valueOf(pos[0] + 1), String.valueOf(pos[1] + 1)).getString();
        int positionX = fx + fw - 12 - this.font.width(position);
        if (this.triedConfirm) {
            graphics.drawString(this.font, Component.translatable("gui.taczfixes.edit_data.invalid").getString(),
                    leftX, textY, COLOR_INVALID, true);
        } else {
            String hints = Component.translatable("gui.taczfixes.edit_data.hints").getString();
            graphics.drawString(this.font, trim(hints, positionX - 16 - leftX), leftX, textY,
                    COLOR_TEXT_FAINT, true);
        }
        graphics.drawString(this.font, position, positionX, textY, COLOR_TEXT_DIM, true);
    }

    /** 行号栏: 当前行号高亮。 */
    private void drawGutter(GuiGraphics graphics, int totalLines, int ty, int maxL, int caretLine) {
        int gx = gutterLeft();
        graphics.fill(gx, ty, dividerX(), ty + textHeight(), COLOR_GUTTER_BG);
        for (int i = 0; i < maxL; i++) {
            int lineIndex = i + this.scroll;
            if (lineIndex >= totalLines) {
                break;
            }
            int y = ty + i * LINE_H + 1;
            if (lineIndex == caretLine) {
                graphics.fill(gx, y - 1, dividerX(), y + LINE_H - 2, COLOR_CURRENT_LINE);
            }
            String num = String.valueOf(lineIndex + 1);
            graphics.drawString(this.font, num, gutterRight() - this.font.width(num), y,
                    lineIndex == caretLine ? COLOR_TEXT_DIM : COLOR_TEXT_FAINT, false);
        }
    }

    /** 选区底色。 */
    private void drawSelection(GuiGraphics graphics, int lineIndex, String lineText, int tx, int y) {
        if (!hasSelection()) {
            return;
        }
        int rowStart = lineStart(lineIndex);
        int rowEnd = rowStart + lineText.length();
        int selLow = Math.max(rowStart, selectionLow());
        int selHigh = Math.min(rowEnd, selectionHigh());
        if (selHigh > selLow) {
            int xStart = tx + displayWidth(lineText, selLow - rowStart) - this.hScroll;
            int xEnd = tx + displayWidth(lineText, selHigh - rowStart) - this.hScroll;
            graphics.fill(xStart, y - 1, xEnd, y + LINE_H - 2, COLOR_SELECTION);
        }
    }

    /** JSON 语法着色: 键/字符串/数字/字面量/括号/标点。 */
    private void drawHighlightedLine(GuiGraphics graphics, String line, int x, int y) {
        int x0 = textLeft();
        int x1 = x0 + textWidth();
        int len = line.length();
        int i = 0;
        int px = x;
        while (i < len) {
            char c = line.charAt(i);
            int start = i;
            int color;
            if (c == '"') {
                i++;
                while (i < len) {
                    char d = line.charAt(i);
                    if (d == '\\' && i + 1 < len) {
                        i += 2;
                        continue;
                    }
                    i++;
                    if (d == '"') {
                        break;
                    }
                }
                int j = i;
                while (j < len && line.charAt(j) == ' ') {
                    j++;
                }
                color = (j < len && line.charAt(j) == ':') ? COLOR_SYNTAX_KEY : COLOR_SYNTAX_STRING;
            } else if (c == '{' || c == '}' || c == '[' || c == ']') {
                color = COLOR_SYNTAX_BRACKET;
                i++;
            } else if (c == ':' || c == ',') {
                color = COLOR_SYNTAX_PUNCT;
                i++;
            } else if (c == '-' || (c >= '0' && c <= '9')) {
                i++;
                while (i < len && isNumberChar(line.charAt(i))) {
                    i++;
                }
                color = COLOR_SYNTAX_NUMBER;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                i++;
                while (i < len && ((line.charAt(i) >= 'a' && line.charAt(i) <= 'z')
                        || (line.charAt(i) >= 'A' && line.charAt(i) <= 'Z'))) {
                    i++;
                }
                String word = line.substring(start, i);
                color = "true".equals(word) || "false".equals(word) || "null".equals(word)
                        ? COLOR_SYNTAX_LITERAL : COLOR_SYNTAX_PUNCT;
            } else if (c == ' ') {
                i++;
                while (i < len && line.charAt(i) == ' ') {
                    i++;
                }
                px += this.font.width(line.substring(start, i));
                continue;
            } else {
                color = COLOR_SYNTAX_PUNCT;
                i++;
            }
            String segment = line.substring(start, i);
            int segW = this.font.width(segment);
            if (px + segW >= x0 && px <= x1) {
                graphics.drawString(this.font, segment, px, y, color, false);
            }
            px += segW;
            if (px > x1) {
                break;
            }
        }
    }

    private static boolean isNumberChar(char c) {
        return (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E';
    }

    private void drawCaret(GuiGraphics graphics, int tx, int ty, int caretOffset) {
        if ((System.currentTimeMillis() / 500) % 2 != 0) {
            return;
        }
        int[] pos = cursorPos();
        int row = pos[0] - this.scroll;
        if (row < 0 || row >= maxLines()) {
            return;
        }
        int x = tx + caretOffset - this.hScroll;
        int maxX = tx + textWidth() - 1;
        if (x > maxX) {
            x = maxX;
        }
        if (x < tx) {
            return;
        }
        int y = ty + row * LINE_H + 1;
        graphics.fill(x, y - 1, x + 1, y + LINE_H - 3, COLOR_CARET);
    }

    // ---------------------------------------------------------------- 滚动条

    private void renderScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
        int total = lines().length;
        int maxL = maxLines();
        if (total <= maxL) {
            return;
        }
        int x = scrollX();
        int ty = textTop();
        int th = textHeight();
        graphics.fill(x, ty, x + 6, ty + th, COLOR_SCROLL_TRACK);
        int thumbH = Math.max(14, (int) ((double) maxL / total * th));
        int span = total - maxL;
        int thumbY = span <= 0 ? ty : ty + (int) ((double) this.scroll / span * (th - thumbH));
        boolean hover = this.draggingScroll
                || (mouseX >= x - 4 && mouseX <= x + 10 && mouseY >= ty && mouseY <= ty + th);
        graphics.fill(x + 1, thumbY, x + 5, thumbY + thumbH,
                hover ? COLOR_SCROLL_THUMB_HOVER : COLOR_SCROLL_THUMB);
    }

    private boolean inScrollbar(double mouseX, double mouseY) {
        if (lines().length <= maxLines()) {
            return false;
        }
        int x = scrollX();
        return mouseX >= x - 4 && mouseX <= x + 10
                && mouseY >= textTop() && mouseY <= textTop() + textHeight();
    }

    /** 根据Y在滑轨上的位置换算 scroll。 */
    private int scrollFromThumb(double mouseY) {
        String[] lines = lines();
        int span = Math.max(0, lines.length - maxLines());
        if (span <= 0) {
            return 0;
        }
        double ratio = (mouseY - textTop()) / (double) textHeight();
        return (int) Math.round(Math.max(0, Math.min(ratio, 1.0)) * span);
    }

    // ---------------------------------------------------------------- 输入

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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inScrollbar(mouseX, mouseY)) {
            this.draggingScroll = true;
            this.followCaret = false;
            this.scroll = scrollFromThumb(mouseY);
            return true;
        }
        int tx = textLeft();
        int ty = textTop();
        int tw = textWidth();
        int th = textHeight();
        boolean inBox = mouseX >= tx && mouseX <= tx + tw && mouseY >= ty && mouseY <= ty + th;
        if (!inBox) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int visualRow = (int) ((mouseY - ty) / LINE_H);
        int lineIndex = this.scroll + visualRow;
        String[] logical = lines();
        if (lineIndex < 0 || lineIndex >= logical.length) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        String lineText = logical[lineIndex];
        int clickX = (int) (mouseX - tx) + this.hScroll;
        int column = columnAt(lineText, clickX);
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
            int tx = textLeft();
            int ty = textTop();
            String[] logical = lines();
            int visualRow = (int) ((mouseY - ty) / LINE_H);
            int lineIndex = Math.max(0, Math.min(logical.length - 1, this.scroll + Math.max(0, visualRow)));
            String lineText = logical[lineIndex];
            int clickX = (int) (mouseX - tx) + this.hScroll;
            int column = columnAt(lineText, clickX);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
        String[] lines = lines();
        if (this.hasShiftDown()) {
            int steps = (int) Math.round(delta * 20);
            int maxHS = 0;
            for (String line : lines) {
                maxHS = Math.max(maxHS, this.font.width(displayText(line)) - textWidth());
            }
            maxHS = Math.max(0, maxHS);
            this.hScroll = Math.max(0, Math.min(this.hScroll + steps, maxHS));
            return true;
        }
        this.followCaret = false;
        int maxL = maxLines();
        int steps = (int) Math.round(delta * 3);
        this.scroll = Math.max(0, Math.min(this.scroll - steps, Math.max(0, lines.length - maxL)));
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
                case GLFW.GLFW_KEY_S: {
                    if (this.modified) {
                        this.confirm();
                    }
                    return true;
                }
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
