package com.ssscript.taczfixes.common.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ssscript.taczfixes.TaczFixesMod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;

/**
 * Custom loot function {@code taczfixes:barter_pick}: spawns one item based on the {@code "kind"}
 * field.
 *
 * <p>JSON usage example:</p>
 * <pre>{@code
 * {
 *   "function": "taczfixes:barter_pick",
 *   "kind": "gun"
 * }
 * }</pre>
 *
 * <p>Available kinds: {@code gun} / {@code ammo} / {@code enchant} / {@code attachment};
 * the pick logic lives in {@link NetheriteBarter#create(NetheriteBarter.Kind, LootContext)}.</p>
 */
public class BarterPickFunction implements LootItemFunction {
    public static final MapCodec<BarterPickFunction> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(Codec.STRING.fieldOf("kind").forGetter(function -> function.kind.id()))
                    .apply(instance, id -> new BarterPickFunction(NetheriteBarter.Kind.byId(id))));

    private final NetheriteBarter.Kind kind;

    public BarterPickFunction(NetheriteBarter.Kind kind) {
        this.kind = kind;
    }

    public NetheriteBarter.Kind getKind() {
        return this.kind;
    }

    @Override
    public ItemStack apply(ItemStack stack, LootContext context) {
        return NetheriteBarter.create(this.kind, context);
    }

    @Override
    public LootItemFunctionType<? extends LootItemFunction> getType() {
        return TaczFixesMod.BARTER_PICK_FUNCTION.get();
    }
}
