package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.data.GunSmithTableEnergyConfig;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.block.AbstractGunSmithTableBlock;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.resource.index.CommonBlockIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

public final class GunSmithTableEnergy {
    public static final String TAG_KEY = "TaczFixesEnergy";

    private GunSmithTableEnergy() {
    }

    public static GunSmithTableEnergyConfig.Block tableConfig(ResourceLocation blockId) {
        if (blockId == null) {
            return null;
        }
        return TimelessAPI.getCommonBlockIndex(blockId)
                .map(CommonBlockIndex::getData)
                .filter(data -> data instanceof GunSmithTableEnergyConfig.BlockDataAccess)
                .map(data -> ((GunSmithTableEnergyConfig.BlockDataAccess) data).taczfixes$getEnergyConfig())
                .orElse(null);
    }

    public static GunSmithTableEnergyConfig.Recipe recipeConfig(ResourceLocation recipeId, Level level) {
        if (recipeId == null || level == null) {
            return null;
        }
        Recipe<?> recipe = level.getRecipeManager().byKey(recipeId).orElse(null);
        return recipeConfig(recipe);
    }

    public static GunSmithTableEnergyConfig.Recipe recipeConfig(Recipe<?> recipe) {
        return recipe instanceof GunSmithTableEnergyConfig.RecipeAccess access
                ? access.taczfixes$getEnergyConfig() : null;
    }

    public static int capacity(ResourceLocation blockId) {
        GunSmithTableEnergyConfig.Block config = tableConfig(blockId);
        if (config != null && config.power_max != null) {
            return Math.max(0, config.power_max);
        }
        return Math.max(0, Config.GUN_SMITH_TABLE_POWER_MAX.get());
    }

    public static int capacity(GunSmithTableBlockEntity table) {
        GunSmithTableBlockEntity root = root(table);
        return root == null ? 0 : capacity(root.getId());
    }

    public static int autoConsumption(ResourceLocation blockId, GunSmithTableRecipe recipe) {
        GunSmithTableEnergyConfig.Block tableConfig = tableConfig(blockId);
        int tableRate = tableConfig != null && tableConfig.consumption != null
                ? tableConfig.consumption : Config.GUN_SMITH_TABLE_AUTO_CONSUMPTION.get();
        GunSmithTableEnergyConfig.Recipe recipeConfig = recipeConfig(recipe);
        int recipeRate = recipeConfig != null && recipeConfig.consumption_auto != null
                ? recipeConfig.consumption_auto : 0;
        return Math.max(0, tableRate) + Math.max(0, recipeRate);
    }

    public static int manualConsumption(GunSmithTableEnergyConfig.Recipe recipeConfig) {
        return recipeConfig != null && recipeConfig.consumption_manually != null
                ? Math.max(0, recipeConfig.consumption_manually) : 0;
    }

    public static long craftDurationMillis(ResourceLocation blockId, GunSmithTableRecipe recipe) {
        GunSmithTableEnergyConfig.Block config = tableConfig(blockId);
        double base = config != null && config.time_base != null
                ? config.time_base : Config.AUTO_CRAFT_BASE_SECONDS.get();
        double perType = config != null && config.time_per_type != null
                ? config.time_per_type : Config.AUTO_CRAFT_SECONDS_PER_TYPE.get();
        double perItem = config != null && config.time_per_item != null
                ? config.time_per_item : Config.AUTO_CRAFT_SECONDS_PER_ITEM.get();
        int types = recipe.getInputs().size();
        int total = 0;
        for (GunSmithTableIngredient ingredient : recipe.getInputs()) {
            total += Math.max(0, ingredient.getCount());
        }
        double seconds = Math.max(0.0, base) + types * Math.max(0.0, perType)
                + total * Math.max(0.0, perItem);
        GunSmithTableEnergyConfig.Recipe recipeConfig = recipeConfig(recipe);
        if (recipeConfig != null && recipeConfig.crafting_time != null) {
            seconds = Math.max(0.0, seconds + recipeConfig.crafting_time);
        }
        return Math.max(1L, Math.round(seconds * 1000.0));
    }

    public static long cooldownMillis(ResourceLocation blockId) {
        GunSmithTableEnergyConfig.Block config = tableConfig(blockId);
        double seconds = config != null && config.cooldown != null
                ? config.cooldown : Config.AUTO_CRAFT_COOLDOWN_SECONDS.get();
        return Math.max(0L, Math.round(Math.max(0.0, seconds) * 1000.0));
    }

    public static int get(GunSmithTableBlockEntity table) {
        GunSmithTableBlockEntity root = root(table);
        if (root instanceof GunSmithTableEnergyAccess access) {
            return Math.max(0, Math.min(access.taczfixes$getEnergy(), capacity(root)));
        }
        return 0;
    }

    public static int get(Level level, BlockPos pos) {
        return get(table(level, pos));
    }

    public static void set(GunSmithTableBlockEntity table, int energy) {
        GunSmithTableBlockEntity root = root(table);
        if (root instanceof GunSmithTableEnergyAccess access) {
            access.taczfixes$setEnergy(Math.max(0, Math.min(energy, capacity(root))));
        }
    }

    public static int add(GunSmithTableBlockEntity table, int amount) {
        if (table == null || amount <= 0) {
            return 0;
        }
        int capacity = capacity(table);
        int current = get(table);
        int accepted = Math.min(amount, Math.max(0, capacity - current));
        if (accepted > 0) {
            set(table, current + accepted);
        }
        return accepted;
    }

    public static boolean consume(GunSmithTableBlockEntity table, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (table == null) {
            return false;
        }
        int current = get(table);
        if (current < amount) {
            return false;
        }
        set(table, current - amount);
        return true;
    }

    public static boolean hasManualPower(Level level, BlockPos pos, ResourceLocation recipeId) {
        GunSmithTableEnergyConfig.Recipe config = recipeConfig(recipeId, level);
        int cost = manualConsumption(config);
        return cost <= 0 || get(level, pos) >= cost;
    }

    public static boolean consumeManual(Level level, BlockPos pos, ResourceLocation recipeId) {
        GunSmithTableEnergyConfig.Recipe config = recipeConfig(recipeId, level);
        return consume(table(level, pos), manualConsumption(config));
    }

    public static ManualResult consumeManualIfPossible(Level level, BlockPos pos, ResourceLocation recipeId,
                                                       Player player) {
        GunSmithTableEnergyConfig.Recipe config = recipeConfig(recipeId, level);
        int cost = manualConsumption(config);
        if (cost <= 0) {
            return ManualResult.OK;
        }
        if (player == null || !hasManualMaterials(level, recipeId, player)) {
            return ManualResult.NO_MATERIALS;
        }
        if (get(level, pos) < cost) {
            return ManualResult.NO_POWER;
        }
        consume(table(level, pos), cost);
        return ManualResult.OK;
    }

    public enum ManualResult {
        OK,
        NO_MATERIALS,
        NO_POWER
    }

    private static boolean hasManualMaterials(Level level, ResourceLocation recipeId, Player player) {
        if (player.isCreative()) {
            return true;
        }
        Recipe<?> recipe = level.getRecipeManager().byKey(recipeId).orElse(null);
        if (!(recipe instanceof GunSmithTableRecipe tableRecipe)) {
            return false;
        }
        IItemHandler handler = player.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        if (handler == null) {
            return false;
        }
        for (GunSmithTableIngredient ingredient : tableRecipe.getInputs()) {
            int count = 0;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && ingredient.getIngredient().test(stack)) {
                    count += stack.getCount();
                }
            }
            if (count < ingredient.getCount()) {
                return false;
            }
        }
        return true;
    }

    public static GunSmithTableBlockEntity table(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        BlockEntity entity = level.getBlockEntity(rootPos(level, pos));
        return entity instanceof GunSmithTableBlockEntity table ? table : null;
    }

    private static GunSmithTableBlockEntity root(GunSmithTableBlockEntity table) {
        if (table == null || table.getLevel() == null) {
            return table;
        }
        BlockPos rootPos = rootPos(table.getLevel(), table.getBlockPos());
        if (rootPos.equals(table.getBlockPos())) {
            return table;
        }
        BlockEntity root = table.getLevel().getBlockEntity(rootPos);
        if (root instanceof GunSmithTableBlockEntity rootTable) {
            migrateEnergy(table, rootTable);
            return rootTable;
        }
        return table;
    }

    private static BlockPos rootPos(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof AbstractGunSmithTableBlock block
                ? block.getRootPos(pos, state) : pos;
    }

    private static void migrateEnergy(GunSmithTableBlockEntity source, GunSmithTableBlockEntity target) {
        if (!(source instanceof GunSmithTableEnergyAccess sourceAccess)
                || !(target instanceof GunSmithTableEnergyAccess targetAccess)) {
            return;
        }
        int sourceEnergy = sourceAccess.taczfixes$getEnergy();
        if (sourceEnergy <= 0) {
            return;
        }
        int capacity = capacity(target);
        int targetEnergy = Math.max(0, Math.min(targetAccess.taczfixes$getEnergy(), capacity));
        int accepted = Math.min(sourceEnergy, Math.max(0, capacity - targetEnergy));
        if (accepted <= 0) {
            return;
        }
        targetAccess.taczfixes$setEnergy(targetEnergy + accepted);
        sourceAccess.taczfixes$setEnergy(sourceEnergy - accepted);
    }
}
