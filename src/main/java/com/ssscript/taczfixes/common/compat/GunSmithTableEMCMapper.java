package com.ssscript.taczfixes.common.compat;

import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.nbt.AmmoItemDataAccessor;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.init.ModRecipe;
import moze_intel.projecte.api.mapper.collector.IMappingCollector;
import moze_intel.projecte.api.mapper.recipe.INSSFakeGroupManager;
import moze_intel.projecte.api.mapper.recipe.IRecipeTypeMapper;
import moze_intel.projecte.api.mapper.recipe.RecipeTypeMapper;
import moze_intel.projecte.api.nss.NSSItem;
import moze_intel.projecte.api.nss.NormalizedSimpleStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 让 ProjectE 的自动 EMC 计算识别 TaCZ 枪械工作台配方。 */
@RecipeTypeMapper(requiredMods = {"projecte", "tacz"})
public class GunSmithTableEMCMapper implements IRecipeTypeMapper {

    @Override
    public String getName() {
        return "GunSmithTableRecipe";
    }

    @Override
    public String getDescription() {
        return "Adds EMC conversions for TaCZ gun smith table recipes.";
    }

    @Override
    public boolean canHandle(RecipeType<?> recipeType) {
        return recipeType == ModRecipe.GUN_SMITH_TABLE_CRAFTING.get()
                || "tacz:gun_smith_table_crafting".equals(String.valueOf(recipeType));
    }

    @Override
    public boolean handleRecipe(IMappingCollector<NormalizedSimpleStack, Long> mapper, Recipe<?> recipe,
                                RegistryAccess registryAccess, INSSFakeGroupManager fakeGroupManager) {
        if (!(recipe instanceof GunSmithTableRecipe tableRecipe)) {
            return false;
        }
        ItemStack output = tableRecipe.getResultItem(registryAccess);
        if (output.isEmpty()) {
            return false;
        }
        List<GunSmithTableIngredient> inputs = tableRecipe.getInputs();
        if (inputs == null) {
            return false;
        }
        // 给基础物品一个占位 EMC, 让 ProjectE 的 NBTProcessor 能处理带 NBT 的实际枪械。
        mapper.setValueBefore(NSSItem.createItem(ModItems.MODERN_KINETIC_GUN.get()), 1L);
        Map<NormalizedSimpleStack, Integer> ingredients = new LinkedHashMap<>();
        for (GunSmithTableIngredient tableIngredient : inputs) {
            Ingredient ingredient = tableIngredient.getIngredient();
            if (ingredient == null) {
                return false;
            }
            ItemStack[] matches;
            try {
                matches = ingredient.getItems();
            } catch (RuntimeException e) {
                return false;
            }
            if (matches == null || matches.length == 0) {
                return false;
            }
            Set<NormalizedSimpleStack> variants = new LinkedHashSet<>();
            for (ItemStack match : matches) {
                if (match != null && !match.isEmpty()) {
                    variants.addAll(variantsOf(match));
                }
            }
            if (variants.isEmpty()) {
                return false;
            }
            int count = Math.max(1, tableIngredient.getCount());
            if (!addIngredient(mapper, ingredients, variants, count, fakeGroupManager)) {
                return false;
            }
        }
        NormalizedSimpleStack outputStack = fullNss(output);
        if (outputStack == null) {
            return false;
        }
        mapper.addConversion(output.getCount(), outputStack, ingredients);
        // 同时给去除易变 NBT 的规范化形式赋值, 让 partial_nbt 材料也能引用到 EMC。
        NormalizedSimpleStack canonicalOutput = canonicalNss(output);
        if (canonicalOutput != null && !canonicalOutput.equals(outputStack)) {
            mapper.addConversion(output.getCount(), canonicalOutput, ingredients);
        }
        return true;
    }

    private static boolean addIngredient(IMappingCollector<NormalizedSimpleStack, Long> mapper,
                                         Map<NormalizedSimpleStack, Integer> ingredients,
                                         Set<NormalizedSimpleStack> variants, int count,
                                         INSSFakeGroupManager fakeGroupManager) {
        if (variants.size() == 1) {
            ingredients.merge(variants.iterator().next(), count, Integer::sum);
            return true;
        }
        Tuple<NormalizedSimpleStack, Boolean> group = fakeGroupManager.getOrCreateFakeGroup(variants);
        if (group == null || group.getA() == null) {
            return false;
        }
        NormalizedSimpleStack dummy = group.getA();
        ingredients.merge(dummy, count, Integer::sum);
        if (group.getB()) {
            for (NormalizedSimpleStack variant : variants) {
                Map<NormalizedSimpleStack, Integer> single = new LinkedHashMap<>();
                single.put(variant, 1);
                mapper.addConversion(1, dummy, single);
            }
        }
        return true;
    }

    /** 同时包含完整 NBT 与仅 ID 的规范形式; 任一形式已有 EMC 就能参与配方。 */
    private static Set<NormalizedSimpleStack> variantsOf(ItemStack stack) {
        Set<NormalizedSimpleStack> variants = new LinkedHashSet<>();
        NormalizedSimpleStack full = fullNss(stack);
        if (full != null) {
            variants.add(full);
        }
        NormalizedSimpleStack canonical = canonicalNss(stack);
        if (canonical != null) {
            variants.add(canonical);
        }
        return variants;
    }

    private static NormalizedSimpleStack fullNss(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        try {
            return NSSItem.createItem(stack);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** 枪械/配件/弹药只保留 ID 字段, 滤掉弹药数、耐久、安装配件等易变 NBT。 */
    private static NormalizedSimpleStack canonicalNss(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Item item = stack.getItem();
        CompoundTag tag = new CompoundTag();
        try {
            if (item instanceof IGun gun) {
                ResourceLocation id = gun.getGunId(stack);
                if (id == null) return null;
                tag.putString(GunItemDataAccessor.GUN_ID_TAG, id.toString());
            } else if (item instanceof IAttachment attachment) {
                ResourceLocation id = attachment.getAttachmentId(stack);
                if (id == null) return null;
                tag.putString(AttachmentItemDataAccessor.ATTACHMENT_ID_TAG, id.toString());
            } else if (item instanceof IAmmo ammo) {
                ResourceLocation id = ammo.getAmmoId(stack);
                if (id == null) return null;
                tag.putString(AmmoItemDataAccessor.AMMO_ID_TAG, id.toString());
            } else {
                return null;
            }
            return NSSItem.createItem(item, tag);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
