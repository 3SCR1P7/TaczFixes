package com.ssscript.taczfixes.common.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AutoCraftSavedData extends SavedData {
    private static final String DATA_NAME = "taczfixes_auto_craft";
    private final Map<TableKey, Task> tasks = new HashMap<>();

    public static AutoCraftSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                AutoCraftSavedData::load,
                AutoCraftSavedData::new,
                DATA_NAME
        );
    }

    public static AutoCraftSavedData load(CompoundTag tag) {
        AutoCraftSavedData data = new AutoCraftSavedData();
        ListTag list = tag.getList("tasks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation dimensionId = ResourceLocation.tryParse(entry.getString("dimension"));
            ResourceLocation blockId = ResourceLocation.tryParse(entry.getString("block"));
            ResourceLocation recipeId = ResourceLocation.tryParse(entry.getString("recipe"));
            if (dimensionId == null || blockId == null || recipeId == null || !entry.hasUUID("owner")) {
                continue;
            }
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
            TableKey key = new TableKey(dimension, BlockPos.of(entry.getLong("pos")));
            Task task = new Task(entry.getUUID("owner"), blockId, recipeId);
            task.enough = entry.getBoolean("enough");
            task.craftStartMillis = entry.getLong("craft_start");
            task.craftDurationMillis = entry.getLong("craft_duration");
            task.cooldownUntilMillis = entry.getLong("cooldown_until");
            data.tasks.put(key, task);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<TableKey, Task> mapEntry : tasks.entrySet()) {
            TableKey key = mapEntry.getKey();
            Task task = mapEntry.getValue();
            CompoundTag entry = new CompoundTag();
            entry.putString("dimension", key.dimension().location().toString());
            entry.putLong("pos", key.tablePos().asLong());
            entry.putUUID("owner", task.owner);
            entry.putString("block", task.blockId.toString());
            entry.putString("recipe", task.recipeId.toString());
            entry.putBoolean("enough", task.enough);
            entry.putLong("craft_start", task.craftStartMillis);
            entry.putLong("craft_duration", task.craftDurationMillis);
            entry.putLong("cooldown_until", task.cooldownUntilMillis);
            list.add(entry);
        }
        tag.put("tasks", list);
        return tag;
    }

    public Map<TableKey, Task> tasks() {
        return tasks;
    }

    public void markDirty() {
        setDirty();
    }

    public record TableKey(ResourceKey<Level> dimension, BlockPos tablePos) {
    }

    public static final class Task {
        public final UUID owner;
        public final ResourceLocation blockId;
        public final ResourceLocation recipeId;
        public boolean enough;
        public long craftStartMillis;
        public long craftDurationMillis;
        public long cooldownUntilMillis;

        public Task(UUID owner, ResourceLocation blockId, ResourceLocation recipeId) {
            this.owner = owner;
            this.blockId = blockId;
            this.recipeId = recipeId;
        }
    }
}
