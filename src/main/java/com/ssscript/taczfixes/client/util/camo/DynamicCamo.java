package com.ssscript.taczfixes.client.util.camo;

import com.ssscript.taczfixes.common.util.GunColorStorage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 动态迷彩运行时注册表:
 * <ul>
 *   <li>资源重载的 prepare 阶段把枪包扫描结果 {@link #stage(List)} 进来(此时还未重编译着色器);</li>
 *   <li>原版 shader 重载时 {@link CamoShaderBuilder} 用这些定义生成程序, 成功后 {@link #publish} 条目;</li>
 *   <li>渲染时按当前渲染物品 NBT 里的迷彩 id 查 index, 写进 CamoIndex uniform。</li>
 * </ul>
 * 全部只在客户端使用。
 */
public final class DynamicCamo {
    /** index 从 1 开始, 0 表示无迷彩。 */
    public record Entry(int index, ResourceLocation id, String nameKey, float strength, float emissive) {
    }

    private static volatile List<CamoDefinition> staged = List.of();
    private static volatile List<Entry> entries = List.of();
    private static volatile boolean shaderActive;

    private DynamicCamo() {
    }

    /** 资源重载 prepare 阶段调用(后台线程)。 */
    public static void stage(List<CamoDefinition> definitions) {
        staged = definitions == null ? List.of() : List.copyOf(definitions);
        if (staged.isEmpty()) {
            entries = List.of();
            shaderActive = false;
        }
    }

    public static List<CamoDefinition> staged() {
        return staged;
    }

    public static List<Entry> entries() {
        return entries;
    }

    public static boolean shaderActive() {
        return shaderActive;
    }

    /** 着色器重载完成后调用(主线程)。 */
    public static void publish(CamoShaderBuilder.Result result) {
        if (result == null) {
            entries = List.of();
            shaderActive = false;
            return;
        }
        entries = result.entries();
        shaderActive = true;
    }

    public static void clear() {
        entries = List.of();
        shaderActive = false;
    }

    /** 按 id 查找条目, 找不到返回 null。 */
    public static Entry byId(String camoId) {
        if (camoId == null || camoId.isEmpty()) {
            return null;
        }
        for (Entry entry : entries) {
            if (entry.id().toString().equals(camoId)) {
                return entry;
            }
        }
        return null;
    }

    public static Entry byIndex(int index) {
        for (Entry entry : entries) {
            if (entry.index() == index) {
                return entry;
            }
        }
        return null;
    }

    private static volatile ItemStack cachedStack;
    private static volatile Entry cachedEntry;

    /** 某物品当前应用的迷彩条目(没装/未知 id 返回 null)。按物品实例缓存, 渲染期每帧只查一次 NBT。 */
    public static Entry appliedOn(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !shaderActive) {
            return null;
        }
        if (stack == cachedStack) {
            return cachedEntry;
        }
        Entry entry = byId(GunColorStorage.getCamo(stack));
        cachedStack = stack;
        cachedEntry = entry;
        return entry;
    }

    /** 迷彩切换/清除后清掉按物品实例的缓存, 让界面里的预览当帧就生效。 */
    public static void invalidate() {
        cachedStack = null;
        cachedEntry = null;
    }
}
