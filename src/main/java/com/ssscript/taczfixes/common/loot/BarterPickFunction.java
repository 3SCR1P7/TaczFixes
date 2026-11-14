package com.ssscript.taczfixes.common.loot;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSyntaxException;
import com.ssscript.taczfixes.TaczFixesMod;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;

/**
 * 自定义战利品函数 {@code taczfixes:barter_pick}: 按 {@code "kind"} 字段随机生成一件物品。
 *
 * <p>JSON 用法示例:</p>
 * <pre>{@code
 * {
 *   "function": "taczfixes:barter_pick",
 *   "kind": "gun"
 * }
 * }</pre>
 *
 * <p>可用的 kind: {@code gun} / {@code ammo} / {@code enchant} / {@code attachment},
 * 具体抽取逻辑见 {@link NetheriteBarter#create(NetheriteBarter.Kind, net.minecraft.util.RandomSource)}。</p>
 */
public class BarterPickFunction implements LootItemFunction {
    private final NetheriteBarter.Kind kind;

    public BarterPickFunction(NetheriteBarter.Kind kind) {
        this.kind = kind;
    }

    public NetheriteBarter.Kind getKind() {
        return this.kind;
    }

    @Override
    public ItemStack apply(ItemStack stack, LootContext context) {
        return NetheriteBarter.create(this.kind, context.getRandom());
    }

    @Override
    public LootItemFunctionType getType() {
        return TaczFixesMod.BARTER_PICK_FUNCTION.get();
    }

    /** 1.20.1 风格的 Gson 序列化器。 */
    public static class Serializer implements net.minecraft.world.level.storage.loot.Serializer<BarterPickFunction> {
        @Override
        public void serialize(JsonObject json, BarterPickFunction value, JsonSerializationContext context) {
            json.addProperty("kind", value.kind.id());
        }

        @Override
        public BarterPickFunction deserialize(JsonObject json, JsonDeserializationContext context) {
            String kindId = GsonHelper.getAsString(json, "kind", NetheriteBarter.Kind.GUN.id());
            NetheriteBarter.Kind kind;
            try {
                kind = NetheriteBarter.Kind.byId(kindId);
            } catch (IllegalArgumentException e) {
                throw new JsonSyntaxException("Unknown barter_pick kind: " + kindId, e);
            }
            return new BarterPickFunction(kind);
        }
    }
}
