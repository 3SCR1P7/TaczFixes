package com.ssscript.taczfixes.common.loot;

import com.ssscript.taczfixes.TaczFixesMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonGunIndex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Bartering logic for piglins accepting netherite ingots.
 *
 * <p>Responsible for three things:</p>
 * <ol>
 *     <li>Rolling the custom loot table {@link #LOOT_TABLE_ID} with the same
 *     {@link LootContextParamSets#PIGLIN_BARTER} parameters vanilla piglin bartering uses;</li>
 *     <li>Backing the custom loot function {@code taczfixes:barter_pick}: picking a random
 *     default-pack gun / ammo / attachment or one of this mod's enchanted books;</li>
 *     <li>Throwing the rolled items the way vanilla piglins throw barter results.</li>
 * </ol>
 */
public final class NetheriteBarter {
    /** Namespace of the TaCZ default gun pack. */
    public static final String DEFAULT_PACK_NAMESPACE = "tacz";

    /** Custom barter loot table for netherite ingots. */
    public static final ResourceLocation LOOT_TABLE_ID =
            ResourceLocation.fromNamespaceAndPath(TaczFixesMod.MOD_ID, "gameplay/netherite_bartering");

    private static final ResourceKey<LootTable> LOOT_TABLE_KEY =
            ResourceKey.create(Registries.LOOT_TABLE, LOOT_TABLE_ID);

    /** RPG gun types: 1-3 ammo (highest merge priority). */
    private static final Set<String> RPG_GUN_TYPES = Set.of("rpg");
    /** Sniper gun types: 2-12 ammo. */
    private static final Set<String> SNIPER_GUN_TYPES = Set.of("sniper");
    /** MG / shotgun / rifle / DMR gun types: 4-20 ammo. */
    private static final Set<String> STANDARD_GUN_TYPES = Set.of("mg", "shotgun", "rifle", "dmr");

    private NetheriteBarter() {
    }

    /** Item categories selectable by the loot function, matching the JSON {@code "kind"} field. */
    public enum Kind {
        GUN("gun"),
        AMMO("ammo"),
        ENCHANT("enchant"),
        ATTACHMENT("attachment");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }

        /**
         * @throws IllegalArgumentException {@code id} is not a known kind
         */
        public static Kind byId(String id) {
            for (Kind kind : values()) {
                if (kind.id.equals(id)) {
                    return kind;
                }
            }
            throw new IllegalArgumentException("Unknown barter kind: " + id);
        }
    }

    /** Ammo count classes, ordered by merge priority (highest first). */
    private enum AmmoClass {
        RPG,
        SNIPER,
        STANDARD,
        OTHER
    }

    /**
     * Rolls the custom barter table once and throws the result like vanilla piglins do.
     */
    public static void barterAndThrow(Piglin piglin) {
        List<ItemStack> items = rollLootTable(piglin);
        if (items.isEmpty()) {
            items = List.of(new ItemStack(Items.IRON_INGOT));
        }
        throwItems(piglin, items);
    }

    /** Equivalent of {@code PiglinAi.getBarterResponseItems}, using this mod's loot table. */
    private static List<ItemStack> rollLootTable(Piglin piglin) {
        if (!(piglin.level() instanceof ServerLevel serverLevel)) {
            return List.of();
        }
        MinecraftServer server = serverLevel.getServer();
        if (server == null) {
            return List.of();
        }
        LootTable lootTable = server.reloadableRegistries().getLootTable(LOOT_TABLE_KEY);
        LootParams params = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.THIS_ENTITY, piglin)
                .create(LootContextParamSets.PIGLIN_BARTER);
        return lootTable.getRandomItems(params);
    }

    /**
     * Entry point of the custom loot function {@code taczfixes:barter_pick}.
     *
     * @param kind    category to pick from
     * @param context loot context (its random source keeps results reproducible)
     * @return one new item stack; falls back to an iron ingot when no candidates exist
     */
    public static ItemStack create(Kind kind, LootContext context) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(context, "context");
        RandomSource random = context.getRandom();
        return switch (kind) {
            case GUN -> randomGun(random);
            case AMMO -> randomAmmo(random);
            case ENCHANT -> randomEnchantedBook(context, random);
            case ATTACHMENT -> randomAttachment(random);
        };
    }

    /** Random default-pack gun with its gun id set. */
    private static ItemStack randomGun(RandomSource random) {
        List<ResourceLocation> ids = defaultGunIds();
        if (ids.isEmpty()) {
            return fallback();
        }
        ItemStack stack = new ItemStack(ModItems.MODERN_KINETIC_GUN.get());
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun != null) {
            gun.setGunId(stack, ids.get(random.nextInt(ids.size())));
        }
        return stack;
    }

    /** Random default-pack ammo, count depending on the matching gun type class. */
    private static ItemStack randomAmmo(RandomSource random) {
        List<ResourceLocation> ids = defaultAmmoIds();
        if (ids.isEmpty()) {
            return fallback();
        }
        ResourceLocation ammoId = ids.get(random.nextInt(ids.size()));
        AmmoClass ammoClass = buildAmmoClasses().getOrDefault(ammoId, AmmoClass.OTHER);
        int count = switch (ammoClass) {
            case RPG -> Mth.nextInt(random, 1, 3);
            case SNIPER -> Mth.nextInt(random, 2, 12);
            case STANDARD -> Mth.nextInt(random, 4, 20);
            case OTHER -> Mth.nextInt(random, 6, 32);
        };
        ItemStack stack = new ItemStack(ModItems.AMMO.get());
        stack.setCount(count);
        IAmmo ammo = IAmmo.getIAmmoOrNull(stack);
        if (ammo != null) {
            ammo.setAmmoId(stack, ammoId);
        }
        return stack;
    }

    /** Random enchanted book carrying one of this mod's enchantments at level 1..maxLevel. */
    private static ItemStack randomEnchantedBook(LootContext context, RandomSource random) {
        RegistryAccess registryAccess = context.getLevel().registryAccess();
        List<Holder.Reference<Enchantment>> enchantments = registryAccess
                .registryOrThrow(Registries.ENCHANTMENT)
                .holders()
                .filter(NetheriteBarter::isModEnchantment)
                .toList();
        if (enchantments.isEmpty()) {
            return fallback();
        }
        Holder.Reference<Enchantment> enchantment = enchantments.get(random.nextInt(enchantments.size()));
        int level = Mth.nextInt(random, 1, Math.max(1, enchantment.value().getMaxLevel()));
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.enchant(enchantment, level);
        return book;
    }

    /** Random non-hidden default-pack attachment with its attachment id set. */
    private static ItemStack randomAttachment(RandomSource random) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (Map.Entry<ResourceLocation, CommonAttachmentIndex> entry : TimelessAPI.getAllCommonAttachmentIndex()) {
            if (!isDefaultPack(entry.getKey())) {
                continue;
            }
            CommonAttachmentIndex index = entry.getValue();
            if (index != null && index.getPojo() != null && index.getPojo().isHidden()) {
                continue;
            }
            ids.add(entry.getKey());
        }
        if (ids.isEmpty()) {
            return fallback();
        }
        ids.sort(null);
        ItemStack stack = new ItemStack(ModItems.ATTACHMENT.get());
        IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
        if (attachment != null) {
            attachment.setAttachmentId(stack, ids.get(random.nextInt(ids.size())));
        }
        return stack;
    }

    /** All default-pack (namespace tacz) gun ids, sorted for stable rolls. */
    private static List<ResourceLocation> defaultGunIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (Map.Entry<ResourceLocation, CommonGunIndex> entry : TimelessAPI.getAllCommonGunIndex()) {
            if (isDefaultPack(entry.getKey()) && entry.getValue() != null) {
                ids.add(entry.getKey());
            }
        }
        ids.sort(null);
        return ids;
    }

    /** All default-pack (namespace tacz) ammo ids, sorted for stable rolls. */
    private static List<ResourceLocation> defaultAmmoIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (Map.Entry<ResourceLocation, com.tacz.guns.resource.index.CommonAmmoIndex> entry : TimelessAPI.getAllCommonAmmoIndex()) {
            if (isDefaultPack(entry.getKey())) {
                ids.add(entry.getKey());
            }
        }
        ids.sort(null);
        return ids;
    }

    /**
     * Ammo id -> count class, derived from the default pack's gun types; when several guns use
     * the same ammo the highest priority class wins (RPG > sniper > standard > other).
     */
    private static Map<ResourceLocation, AmmoClass> buildAmmoClasses() {
        Map<ResourceLocation, AmmoClass> classes = new HashMap<>();
        for (Map.Entry<ResourceLocation, CommonGunIndex> entry : TimelessAPI.getAllCommonGunIndex()) {
            if (!isDefaultPack(entry.getKey())) {
                continue;
            }
            CommonGunIndex index = entry.getValue();
            if (index == null || index.getGunData() == null) {
                continue;
            }
            ResourceLocation ammoId = index.getGunData().getAmmoId();
            if (ammoId == null) {
                continue;
            }
            AmmoClass ammoClass = ammoClassOf(index.getType());
            classes.merge(ammoId, ammoClass, (a, b) -> a.ordinal() <= b.ordinal() ? a : b);
        }
        return classes;
    }

    private static AmmoClass ammoClassOf(String gunType) {
        if (gunType == null) {
            return AmmoClass.OTHER;
        }
        String type = gunType.toLowerCase(Locale.ROOT);
        if (RPG_GUN_TYPES.contains(type)) {
            return AmmoClass.RPG;
        }
        if (SNIPER_GUN_TYPES.contains(type)) {
            return AmmoClass.SNIPER;
        }
        if (STANDARD_GUN_TYPES.contains(type)) {
            return AmmoClass.STANDARD;
        }
        return AmmoClass.OTHER;
    }

    /** True when {@code holder} belongs to this mod's enchantment namespace. */
    public static boolean isModEnchantment(Holder<Enchantment> holder) {
        return holder.unwrapKey()
                .map(key -> TaczFixesMod.MOD_ID.equals(key.location().getNamespace()))
                .orElse(false);
    }

    private static boolean isDefaultPack(ResourceLocation id) {
        return id != null && DEFAULT_PACK_NAMESPACE.equals(id.getNamespace());
    }

    /** Fallback when a candidate list is empty; should not happen under normal conditions. */
    private static ItemStack fallback() {
        return new ItemStack(Items.IRON_INGOT);
    }

    /**
     * Mirrors {@code PiglinAi.throwItems}: throw towards the nearest visible player, otherwise
     * towards a random nearby position. Implemented locally because the barter table is rolled
     * earlier than vanilla's logic and the vanilla helpers are private.
     */
    private static void throwItems(Piglin piglin, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        Optional<Player> nearestPlayer = piglin.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER);
        Vec3 target;
        if (nearestPlayer.isPresent()) {
            target = nearestPlayer.get().position();
        } else {
            Vec3 randomPos = LandRandomPos.getPos(piglin, 4, 2);
            target = randomPos != null ? randomPos : piglin.position();
        }
        piglin.swing(InteractionHand.OFF_HAND);
        for (ItemStack item : items) {
            BehaviorUtils.throwItem(piglin, item, target.add(0.0, 1.0, 0.0));
        }
    }
}
