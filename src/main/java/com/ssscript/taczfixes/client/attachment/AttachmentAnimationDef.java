package com.ssscript.taczfixes.client.attachment;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.Map;

/**
 * 配件动画定义。从 {@code assets/&lt;namespace&gt;/display/attachments/*.json} 的
 * {@code taczfixes} 块中解析。
 *
 * <pre>
 * "taczfixes": {
 *   "animation": "mypack:my_attachment_animations",
 *   "state_machine": "mypack:my_attachment_state_machine",
 *   "state_machine_param": { "key": "value" }
 * }
 * </pre>
 */
public final class AttachmentAnimationDef {
    private final ResourceLocation displayId;
    private final ResourceLocation animationId;
    private final ResourceLocation stateMachineId;
    @Nullable
    private final JsonElement stateMachineParam;

    private AttachmentAnimationDef(ResourceLocation displayId, ResourceLocation animationId,
                                   ResourceLocation stateMachineId, @Nullable JsonElement stateMachineParam) {
        this.displayId = displayId;
        this.animationId = animationId;
        this.stateMachineId = stateMachineId;
        this.stateMachineParam = stateMachineParam;
    }

    /**
     * 解析 display JSON 根对象的 {@code taczfixes} 块。
     *
     * @param displayId 该 display 文件对应的 id
     * @param root      display JSON 根对象
     * @return 解析结果；缺少 animation / state_machine 或格式错误时返回 null（静默回退）
     */
    @Nullable
    public static AttachmentAnimationDef parse(ResourceLocation displayId, JsonObject root) {
        if (root == null || !root.has("taczfixes")) {
            return null;
        }
        JsonElement taczfixes = root.get("taczfixes");
        if (taczfixes == null || !taczfixes.isJsonObject()) {
            return null;
        }
        JsonObject block = taczfixes.getAsJsonObject();
        ResourceLocation animation = readId(block, "animation");
        ResourceLocation stateMachine = readId(block, "state_machine");
        if (animation == null || stateMachine == null) {
            return null;
        }
        JsonElement param = block.get("state_machine_param");
        if (param != null && !param.isJsonObject()) {
            param = null;
        }
        return new AttachmentAnimationDef(displayId, animation, stateMachine, param);
    }

    @Nullable
    private static ResourceLocation readId(JsonObject block, String key) {
        String value = GsonHelper.getAsString(block, key, null);
        if (value == null || value.isBlank()) {
            return null;
        }
        return ResourceLocation.tryParse(value);
    }

    public ResourceLocation displayId() {
        return displayId;
    }

    public ResourceLocation animationId() {
        return animationId;
    }

    public ResourceLocation stateMachineId() {
        return stateMachineId;
    }

    /** 为每个状态机实例构建一份独立的 Lua 参数表。 */
    public LuaTable createParamTable() {
        LuaTable table = new LuaTable();
        if (stateMachineParam != null && stateMachineParam.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : stateMachineParam.getAsJsonObject().entrySet()) {
                table.set(entry.getKey(), toLua(entry.getValue()));
            }
        }
        return table;
    }

    private static LuaValue toLua(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return LuaValue.NIL;
        }
        if (element.isJsonObject()) {
            LuaTable table = new LuaTable();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                table.set(entry.getKey(), toLua(entry.getValue()));
            }
            return table;
        }
        if (element.isJsonArray()) {
            LuaTable table = new LuaTable();
            int index = 1;
            for (JsonElement child : element.getAsJsonArray()) {
                table.set(index++, toLua(child));
            }
            return table;
        }
        if (element.isJsonPrimitive()) {
            var primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return LuaValue.valueOf(primitive.getAsBoolean());
            }
            if (primitive.isNumber()) {
                String raw = primitive.getAsString();
                if (raw.indexOf('.') < 0 && raw.indexOf('e') < 0 && raw.indexOf('E') < 0) {
                    try {
                        return LuaValue.valueOf(Integer.parseInt(raw));
                    } catch (NumberFormatException ignored) {
                        return LuaValue.valueOf(primitive.getAsDouble());
                    }
                }
                return LuaValue.valueOf(primitive.getAsDouble());
            }
            return LuaValue.valueOf(primitive.getAsString());
        }
        return LuaValue.NIL;
    }
}
