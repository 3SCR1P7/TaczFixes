package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.data.AutoCraftSavedData;
import com.ssscript.taczfixes.common.data.AutoCraftSavedData.TableKey;
import com.ssscript.taczfixes.common.data.AutoCraftSavedData.Task;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.common.network.ServerMessageAutoCraftState;
import com.ssscript.taczfixes.common.util.GunSmithTableEnergy;
import com.ssscript.taczfixes.common.util.GunSmithTablePosAccess;
import com.tacz.guns.block.AbstractGunSmithTableBlock;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID)
public final class GunSmithTableAutoCraftHandler {

    private static final long CRAFT_TICK_MILLIS = 50L;
    private static Map<TableKey, Task> TASKS = new ConcurrentHashMap<>();
    private static AutoCraftSavedData savedData;
    private static MinecraftServer savedServer;

    private GunSmithTableAutoCraftHandler() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        loadData(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (savedData != null) {
            savedData.markDirty();
        }
        TASKS = new ConcurrentHashMap<>();
        savedData = null;
        savedServer = null;
    }

    private static AutoCraftSavedData loadData(MinecraftServer server) {
        if (savedData == null || savedServer != server) {
            savedData = AutoCraftSavedData.get(server);
            savedServer = server;
            TASKS = savedData.tasks();
        }
        return savedData;
    }

    public static void setEnabled(ServerPlayer player, int containerId, ResourceLocation recipeId, boolean enabled) {
        GunSmithTableMenu menu = currentMenu(player, containerId);
        if (menu == null) return;
        BlockPos tablePos = tablePos(menu);
        if (tablePos == null) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ServerLevel level = player.serverLevel();
        AutoCraftSavedData data = loadData(server);
        TableKey key = new TableKey(level.dimension(), tablePos);
        Task existing = TASKS.get(key);
        if (existing != null && !existing.owner.equals(player.getUUID())) {
            sendState(player, menu, false, null, false, 0.0F, false, false);
            return;
        }
        if (!enabled) {
            if (TASKS.remove(key) != null) {
                data.markDirty();
            }
            sendState(player, menu, false, null, false, 0.0F, false, false);
            return;
        }
        if (!Config.AUTO_CRAFT_ENABLED.get()) {
            if (TASKS.remove(key) != null) {
                data.markDirty();
            }
            sendState(player, menu, false, null, false, 0.0F, false, false);
            return;
        }
        GunSmithTableBlockEntity table = level.getBlockEntity(tablePos) instanceof GunSmithTableBlockEntity be
                ? be : null;
        GunSmithTableRecipe recipe = resolveRecipe(level, recipeId);
        if (table == null || recipe == null || !menu.canSelectRecipe(recipeId, level.getRecipeManager())) {
            if (TASKS.remove(key) != null) {
                data.markDirty();
            }
            sendState(player, menu, false, null, false, 0.0F, false, false);
            return;
        }
        Task task = new Task(player.getUUID(), table.getId(), recipeId);
        if (canCraft(level, tablePos, recipe)) {
            startCraft(task, recipe);
        }
        TASKS.put(key, task);
        data.markDirty();
        sendState(player, menu, true, recipeId, task.enough, progress(task), coolingDown(task), task.powerPaused);
    }

    public static void query(ServerPlayer player, int containerId) {
        GunSmithTableMenu menu = currentMenu(player, containerId);
        if (menu == null) return;
        BlockPos tablePos = tablePos(menu);
        if (tablePos == null) {
            sendState(player, menu, false, null, false, 0.0F, false, false);
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ServerLevel level = player.serverLevel();
        AutoCraftSavedData data = loadData(server);
        TableKey key = new TableKey(level.dimension(), tablePos);
        Task task = TASKS.get(key);
        if (task != null && !task.owner.equals(player.getUUID())) {
            sendState(player, menu, false, null, false, 0.0F, false, false);
            return;
        }
        if (task == null || !Config.AUTO_CRAFT_ENABLED.get()) {
            if (TASKS.remove(key) != null) {
                data.markDirty();
            }
            sendState(player, menu, false, null, false, 0.0F, false, false);
            return;
        }
        GunSmithTableBlockEntity table = level.getBlockEntity(tablePos) instanceof GunSmithTableBlockEntity be
                ? be : null;
        GunSmithTableRecipe recipe = resolveRecipe(level, task.recipeId);
        if (table == null || recipe == null || !Objects.equals(table.getId(), task.blockId)) {
            if (TASKS.remove(key) != null) {
                data.markDirty();
            }
            sendState(player, menu, false, null, false, 0.0F, false, false);
            return;
        }
        if (coolingDown(task)) {
            sendState(player, menu, true, task.recipeId, task.enough, 0.0F, true, false);
            return;
        }
        if (task.cooldownUntilMillis != 0L) {
            task.cooldownUntilMillis = 0L;
            data.markDirty();
        }
        boolean changed = false;
        if (task.craftElapsedMillis == 0L && task.craftDurationMillis == 0L) {
            if (canCraft(level, tablePos, recipe)) {
                startCraft(task, recipe);
                changed = true;
            } else if (task.enough) {
                task.enough = false;
                changed = true;
            }
        }
        if (changed) {
            data.markDirty();
        }
        sendState(player, menu, true, task.recipeId, task.enough, progress(task),
                coolingDown(task), task.powerPaused);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TASKS.isEmpty()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        AutoCraftSavedData data = loadData(server);

        if (!Config.AUTO_CRAFT_ENABLED.get()) {
            for (Map.Entry<TableKey, Task> entry : TASKS.entrySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getValue().owner);
                if (player != null) {
                    sendState(player, viewingMenu(player, entry.getKey()), false, null, false, 0.0F, false, false);
                }
            }
            TASKS.clear();
            data.markDirty();
            return;
        }

        Set<String> craftedThisTick = new HashSet<>();
        Iterator<Map.Entry<TableKey, Task>> iterator = TASKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<TableKey, Task> entry = iterator.next();
            TableKey key = entry.getKey();
            Task task = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(task.owner);
            if (player == null) {
                continue;
            }
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null || !level.isLoaded(key.tablePos())) {
                continue;
            }
            GunSmithTableMenu viewingMenu = viewingMenu(player, key);
            GunSmithTableBlockEntity table = level.getBlockEntity(key.tablePos()) instanceof GunSmithTableBlockEntity be
                    ? be : null;
            if (table == null || !Objects.equals(table.getId(), task.blockId)) {
                iterator.remove();
                data.markDirty();
                sendState(player, viewingMenu, false, null, false, 0.0F, false, false);
                continue;
            }
            GunSmithTableRecipe recipe = resolveRecipe(level, task.recipeId);
            if (recipe == null || (viewingMenu != null
                    && !viewingMenu.canSelectRecipe(task.recipeId, level.getRecipeManager()))) {
                iterator.remove();
                data.markDirty();
                sendState(player, viewingMenu, false, null, false, 0.0F, false, false);
                continue;
            }

            long now = System.currentTimeMillis();
            String positionKey = key.dimension().location() + "@" + key.tablePos().asLong();
            if (coolingDown(task)) {
                task.powerPaused = false;
            } else if (task.cooldownUntilMillis != 0L) {
                task.cooldownUntilMillis = 0L;
                boolean enough = canCraft(level, key.tablePos(), recipe);
                task.enough = enough;
                if (enough) {
                    startCraft(task, recipe);
                }
            } else if (task.craftDurationMillis > 0L) {
                if (!canCraft(level, key.tablePos(), recipe)) {
                    task.craftElapsedMillis = 0L;
                    task.craftDurationMillis = 0L;
                    task.cooldownUntilMillis = 0L;
                    task.enough = false;
                    task.powerPaused = false;
                } else {
                    int powerRate = GunSmithTableEnergy.autoConsumption(task.blockId, recipe);
                    if (powerRate > 0 && !GunSmithTableEnergy.consume(table, powerRate)) {
                        task.enough = true;
                        task.powerPaused = true;
                    } else {
                        task.powerPaused = false;
                        task.enough = true;
                        task.craftElapsedMillis += CRAFT_TICK_MILLIS;
                        if (task.craftElapsedMillis >= task.craftDurationMillis) {
                            if (!craftedThisTick.contains(positionKey)) {
                                boolean crafted = craftOnce(level, key.tablePos(), recipe);
                                if (crafted) {
                                    craftedThisTick.add(positionKey);
                                }
                                task.craftElapsedMillis = 0L;
                                task.craftDurationMillis = 0L;
                                boolean enough = canCraft(level, key.tablePos(), recipe);
                                task.enough = enough;
                                if (crafted) {
                                    long cooldown = cooldownMillis(task.blockId);
                                    if (cooldown > 0L) {
                                        task.cooldownUntilMillis = now + cooldown;
                                    } else if (enough) {
                                        startCraft(task, recipe);
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                boolean enough = canCraft(level, key.tablePos(), recipe);
                task.enough = enough;
                task.powerPaused = false;
                if (enough) {
                    startCraft(task, recipe);
                }
            }

            sendState(player, viewingMenu, true, task.recipeId, task.enough, progress(task),
                    coolingDown(task), task.powerPaused);
            data.markDirty();
        }
    }

    private static void startCraft(Task task, GunSmithTableRecipe recipe) {
        task.craftElapsedMillis = 0L;
        task.craftDurationMillis = GunSmithTableEnergy.craftDurationMillis(task.blockId, recipe);
        task.cooldownUntilMillis = 0L;
        task.enough = true;
        task.powerPaused = false;
    }

    private static boolean coolingDown(Task task) {
        return task.cooldownUntilMillis > System.currentTimeMillis();
    }

    private static long cooldownMillis(ResourceLocation blockId) {
        return GunSmithTableEnergy.cooldownMillis(blockId);
    }

    private static float progress(Task task) {
        if (coolingDown(task) || task.craftDurationMillis <= 0L) {
            return 0.0F;
        }
        float value = (float) task.craftElapsedMillis / (float) task.craftDurationMillis;
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static GunSmithTableMenu currentMenu(ServerPlayer player, int containerId) {
        return player.containerMenu instanceof GunSmithTableMenu menu && menu.containerId == containerId
                ? menu : null;
    }

    private static BlockPos tablePos(GunSmithTableMenu menu) {
        return menu instanceof GunSmithTablePosAccess access ? access.taczfixes$getTablePos() : null;
    }

    private static GunSmithTableMenu viewingMenu(ServerPlayer player, TableKey key) {
        if (!player.level().dimension().equals(key.dimension())) {
            return null;
        }
        if (!(player.containerMenu instanceof GunSmithTableMenu menu)) {
            return null;
        }
        return key.tablePos().equals(tablePos(menu)) ? menu : null;
    }

    private static void sendState(ServerPlayer player, GunSmithTableMenu menu, boolean enabled,
                                  ResourceLocation recipeId, boolean enough, float progress,
                                  boolean coolingDown, boolean powerPaused) {
        if (menu == null || player == null) {
            return;
        }
        int energy = 0;
        int capacity = 0;
        BlockPos pos = tablePos(menu);
        if (pos != null && player.level() instanceof ServerLevel level) {
            GunSmithTableBlockEntity table = GunSmithTableEnergy.table(level, pos);
            if (table != null) {
                energy = GunSmithTableEnergy.get(table);
                capacity = GunSmithTableEnergy.capacity(table);
            }
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ServerMessageAutoCraftState(menu.containerId, enabled, recipeId, enough, progress,
                        coolingDown, powerPaused, energy, capacity));
    }

    private static GunSmithTableRecipe resolveRecipe(ServerLevel level, ResourceLocation recipeId) {
        Recipe<?> recipe = level.getRecipeManager().byKey(recipeId).orElse(null);
        return recipe instanceof GunSmithTableRecipe tableRecipe ? tableRecipe : null;
    }

    private static boolean canCraft(ServerLevel level, BlockPos tablePos, GunSmithTableRecipe recipe) {
        return buildPlan(level, tablePos, recipe) != null;
    }

    private static boolean craftOnce(ServerLevel level, BlockPos tablePos, GunSmithTableRecipe recipe) {
        Plan plan = buildPlan(level, tablePos, recipe);
        if (plan == null) {
            return false;
        }
        ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
        if (result.isEmpty()) {
            return false;
        }

        ContainerRef outputContainer = null;
        List<ContainerRef> touched = new ArrayList<>();
        for (Map.Entry<SlotRef, Integer> entry : plan.extraction.entrySet()) {
            SlotRef slot = entry.getKey();
            slot.container.handler().extractItem(slot.slot, entry.getValue(), false);
            if (outputContainer == null) {
                outputContainer = slot.container;
            }
            if (!touched.contains(slot.container)) {
                touched.add(slot.container);
            }
        }

        if (outputContainer == null) {
            outputContainer = plan.containers.get(0);
        }
        ItemStack remaining = insert(outputContainer.handler(), result);
        if (!remaining.isEmpty()) {
            for (ContainerRef container : plan.containers) {
                if (container == outputContainer) {
                    continue;
                }
                if (!touched.contains(container)) {
                    touched.add(container);
                }
                remaining = insert(container.handler(), remaining);
                if (remaining.isEmpty()) {
                    break;
                }
            }
        }
        if (!remaining.isEmpty()) {
            ItemEntity entity = new ItemEntity(level,
                    tablePos.getX() + 0.5D, tablePos.getY() + 1.0D, tablePos.getZ() + 0.5D,
                    remaining);
            level.addFreshEntity(entity);
        }

        for (ContainerRef container : touched) {
            markChanged(level, container.entity());
        }
        return true;
    }

    private static Plan buildPlan(ServerLevel level, BlockPos tablePos, GunSmithTableRecipe recipe) {
        List<ContainerRef> containers = adjacentContainers(level, tablePos);
        if (containers.isEmpty()) {
            return null;
        }

        List<SlotRef> slots = new ArrayList<>();
        for (ContainerRef container : containers) {
            for (int slot = 0; slot < container.handler().getSlots(); slot++) {
                ItemStack stack = container.handler().getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    slots.add(new SlotRef(container, slot, stack, stack.getCount()));
                }
            }
        }
        if (slots.isEmpty()) {
            return null;
        }

        Map<SlotRef, Integer> extraction = new LinkedHashMap<>();
        for (GunSmithTableIngredient ingredient : recipe.getInputs()) {
            int remaining = ingredient.getCount();
            for (SlotRef slot : slots) {
                if (remaining <= 0) {
                    break;
                }
                if (slot.available <= 0 || !ingredient.getIngredient().test(slot.stack)) {
                    continue;
                }
                int take = Math.min(remaining, slot.available);
                slot.available -= take;
                extraction.merge(slot, take, Integer::sum);
                remaining -= take;
            }
            if (remaining > 0) {
                return null;
            }
        }
        return new Plan(containers, extraction);
    }

    private static List<ContainerRef> adjacentContainers(ServerLevel level, BlockPos tablePos) {
        Set<BlockPos> tableParts = new HashSet<>();
        tableParts.add(tablePos);
        for (BlockPos candidate : BlockPos.betweenClosed(
                tablePos.offset(-1, -1, -1), tablePos.offset(1, 1, 1))) {
            BlockState state = level.getBlockState(candidate);
            if (state.getBlock() instanceof AbstractGunSmithTableBlock block
                    && block.getRootPos(candidate, state).equals(tablePos)) {
                tableParts.add(candidate.immutable());
            }
        }

        Set<BlockPos> neighborPositions = new HashSet<>();
        for (BlockPos part : tableParts) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = part.relative(direction);
                if (!tableParts.contains(neighbor)) {
                    neighborPositions.add(neighbor);
                }
            }
        }

        List<ContainerRef> result = new ArrayList<>();
        Set<BlockEntity> seenEntities = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<IItemHandler> seenHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BlockPos neighborPos : neighborPositions) {
            BlockEntity entity = level.getBlockEntity(neighborPos);
            if (entity == null || !seenEntities.add(entity)) {
                continue;
            }
            IItemHandler handler = entity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
            if (handler == null) {
                for (Direction direction : Direction.values()) {
                    handler = entity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction).orElse(null);
                    if (handler != null) {
                        break;
                    }
                }
            }
            if (handler == null || !seenHandlers.add(handler)) {
                continue;
            }
            result.add(new ContainerRef(entity, handler));
        }
        return result;
    }

    private static ItemStack insert(IItemHandler handler, ItemStack stack) {
        ItemStack remaining = stack;
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = handler.insertItem(slot, remaining, false);
        }
        return remaining;
    }

    private static void markChanged(ServerLevel level, BlockEntity entity) {
        entity.setChanged();
        BlockState state = level.getBlockState(entity.getBlockPos());
        level.sendBlockUpdated(entity.getBlockPos(), state, state, 3);
    }

    private record ContainerRef(BlockEntity entity, IItemHandler handler) {
    }

    private record Plan(List<ContainerRef> containers, Map<SlotRef, Integer> extraction) {
    }

    private static final class SlotRef {
        private final ContainerRef container;
        private final int slot;
        private final ItemStack stack;
        private int available;

        private SlotRef(ContainerRef container, int slot, ItemStack stack, int available) {
            this.container = container;
            this.slot = slot;
            this.stack = stack;
            this.available = available;
        }
    }
}
