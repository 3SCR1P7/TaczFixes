package com.ssscript.taczfixes.common.loot;

import com.ssscript.taczfixes.TaczFixesMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
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
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 猪灵用下界合金锭以物易物的核心逻辑。
 *
 * <p>负责三件事:</p>
 * <ol>
 *     <li>掷出自定义战利品表 {@link #LOOT_TABLE_ID}(与猪灵金锭以物易物使用同样的
 *     {@link LootContextParamSets#PIGLIN_BARTER} 参数);</li>
 *     <li>为战利品函数 {@code taczfixes:barter_pick} 随机挑选默认枪包里的枪 / 弹药 / 配件,
 *     以及本模组的附魔书;</li>
 *     <li>把掷出的物品按原版猪灵的投掷方式丢到最近的玩家或附近随机位置。</li>
 * </ol>
 *
 * <p>本类不包含任何 mixin 代码, 方便移植到其它加载器 / 版本; 只有注册与注入点放在对应加载器侧。</p>
 */
public final class NetheriteBarter {
    /** 默认枪包及 TaCZ 内置资源使用的命名空间。 */
    public static final String DEFAULT_PACK_NAMESPACE = "tacz";

    /** 下界合金锭对应的自定义以物易物战利品表。 */
    public static final ResourceLocation LOOT_TABLE_ID =
            ResourceLocation.fromNamespaceAndPath(TaczFixesMod.MOD_ID, "gameplay/netherite_bartering");

    /** 火箭筒类型: 弹药给 1-3 发。 */
    private static final Set<String> RPG_GUN_TYPES = Set.of("rpg");
    /** 狙击枪类型: 弹药给 2-12 发。 */
    private static final Set<String> SNIPER_GUN_TYPES = Set.of("sniper");
    /** 机枪 / 霰弹枪 / 步枪 / 精确射手步枪类型: 弹药给 4-20 发。 */
    private static final Set<String> STANDARD_GUN_TYPES = Set.of("mg", "shotgun", "rifle", "dmr");

    private NetheriteBarter() {
    }

    /** 战利品函数可挑选的物品类别, 对应 JSON 里的 {@code "kind"} 字段。 */
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
         * @throws IllegalArgumentException {@code id} 不是已知类别
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

    /** 弹药数量档位; 声明顺序即合并优先级(靠前者优先)。 */
    private enum AmmoClass {
        RPG,
        SNIPER,
        STANDARD,
        OTHER
    }

    /**
     * 作为以物易物的回应: 掷一次 {@link #LOOT_TABLE_ID}, 然后按原版猪灵的方式把物品丢给玩家。
     *
     */
    public static void barterAndThrow(Piglin piglin) {
        List<ItemStack> items = rollLootTable(piglin);
        if (items.isEmpty()) {
            items = List.of(new ItemStack(Items.IRON_INGOT));
        }
        throwItems(piglin, items);
    }

    /** 与 {@code PiglinAi.getBarterResponseItems} 等价, 只是战利品表换成本模组的。 */
    private static List<ItemStack> rollLootTable(Piglin piglin) {
        if (!(piglin.level() instanceof ServerLevel serverLevel)) {
            return List.of();
        }
        MinecraftServer server = serverLevel.getServer();
        if (server == null) {
            return List.of();
        }
        LootTable lootTable = server.getLootData().getLootTable(LOOT_TABLE_ID);
        LootParams params = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.THIS_ENTITY, piglin)
                .create(LootContextParamSets.PIGLIN_BARTER);
        return lootTable.getRandomItems(params);
    }

    /**
     * 战利品函数 {@code taczfixes:barter_pick} 的抽取入口。
     *
     * @param kind   要抽取的类别
     * @param random 随机源(使用战利品上下文自带的随机源, 保证可复现)
     * @return 一件新物品; 候选列表为空时返回铁锭兜底
     */
    public static ItemStack create(Kind kind, RandomSource random) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(random, "random");
        return switch (kind) {
            case GUN -> randomGun(random);
            case AMMO -> randomAmmo(random);
            case ENCHANT -> randomEnchantedBook(random);
            case ATTACHMENT -> randomAttachment(random);
        };
    }

    /** 随机默认枪包枪械, 已写入枪械 id。 */
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

    /** 随机默认枪包弹药, 数量按配套枪械类型分档。 */
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

    /** 随机本模组附魔书, 等级 1..最大等级。 */
    private static ItemStack randomEnchantedBook(RandomSource random) {
        List<Enchantment> enchantments = modEnchantments();
        if (enchantments.isEmpty()) {
            return fallback();
        }
        Enchantment enchantment = enchantments.get(random.nextInt(enchantments.size()));
        int level = Mth.nextInt(random, 1, Math.max(1, enchantment.getMaxLevel()));
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, level));
    }

    /** 随机默认枪包中未隐藏的配件, 已写入配件 id。 */
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

    /** 默认枪包(命名空间 tacz)的全部枪械 id。 */
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

    /** 默认枪包(命名空间 tacz)的全部弹药 id。 */
    private static List<ResourceLocation> defaultAmmoIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (var entry : TimelessAPI.getAllCommonAmmoIndex()) {
            if (isDefaultPack(entry.getKey())) {
                ids.add(entry.getKey());
            }
        }
        ids.sort(null);
        return ids;
    }

    /**
     * 弹药 id -> 数量档位。由默认枪包的枪械类型反推; 同一弹药被多把枪使用时取最高档
     * (rpg > 狙击 > 机枪/霰弹/步枪/精确射手 > 其它)。
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

    /** 本模组命名空间下的全部附魔(数量由注册表决定, 目前 25 个)。 */
    private static List<Enchantment> modEnchantments() {
        List<Enchantment> enchantments = new ArrayList<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<Enchantment>, Enchantment> entry
                : ForgeRegistries.ENCHANTMENTS.getEntries()) {
            if (TaczFixesMod.MOD_ID.equals(entry.getKey().location().getNamespace())) {
                enchantments.add(entry.getValue());
            }
        }
        return enchantments;
    }

    private static boolean isDefaultPack(ResourceLocation id) {
        return id != null && DEFAULT_PACK_NAMESPACE.equals(id.getNamespace());
    }

    /** 候选列表为空时的兜底, 正常情况下不会发生。 */
    private static ItemStack fallback() {
        return new ItemStack(Items.IRON_INGOT);
    }

    /**
     * 与 {@code PiglinAi.throwItems} 行为一致: 优先丢向最近可见的玩家, 否则丢向附近随机位置。
     * 因为战利品表掷骰比原版猪灵逻辑更早介入, 这里复制一份投掷逻辑, 避免依赖后续版本可能变动的私有方法。
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
