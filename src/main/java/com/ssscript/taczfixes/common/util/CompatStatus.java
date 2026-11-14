package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.TaczFixesMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;

/**
 * 与 taczfixes 有联动的模组清单与运行状态查询, 供 /taczfixes compat 指令与 HUD 版本标签使用。
 */
public final class CompatStatus {
    public record Entry(String modId, String fallbackName) {
    }

    /** 有联动/兼容代码的可选模组(含 TaCZ 本体与 TaCZ 内嵌的 SimpleBedrockModel)。 */
    private static final List<Entry> ENTRIES = List.of(
            new Entry("tacz", "Timeless & Classics Zero"),
            new Entry("simplebedrockmodel", "SimpleBedrockModel"),
            new Entry("gunsmithlib", "GunsmithLib"),
            new Entry("taczexpands", "TACZExpands"),
            new Entry("projecte", "ProjectE"),
            new Entry("apricityui", "ApricityUI"),
            new Entry("cloth_config", "Cloth Config"),
            new Entry("create", "Create"),
            new Entry("parcool", "ParCool"),
            new Entry("lrtactical", "LesRaisins Tactical Equipements"),
            new Entry("irons_spellbooks", "Iron's Spells 'n Spellbooks"),
            new Entry("acceleratedrendering", "Accelerated Rendering"),
            new Entry("playeranimator", "Player Animator"),
            new Entry("touhou_little_maid", "Touhou Little Maid"),
            new Entry("yes_steve_model", "Yes Steve Model"),
            new Entry("sodium", "Sodium"));

    private static String versionLabel;
    private static Component versionLiteral;

    private CompatStatus() {
    }

    /** 形如 taczfixes-1.21.1-3.0.2 的版本标签。 */
    public static String versionLabel() {
        if (versionLabel == null) {
            String minecraftVersion = SharedConstants.getCurrentVersion().getName();
            String modVersion = ModList.get().getModContainerById(TaczFixesMod.MOD_ID)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("unknown");
            versionLabel = TaczFixesMod.MOD_ID + "-" + minecraftVersion + "-" + modVersion;
        }
        return versionLabel;
    }

    /** HUD 每帧复用同一个文本对象, 避免重复分配。 */
    public static Component versionLiteral() {
        if (versionLiteral == null) {
            versionLiteral = Component.literal(versionLabel());
        }
        return versionLiteral;
    }

    /** 已安装返回模组版本字符串, 未安装返回 null。 */
    public static String installedVersion(String modId) {
        ModContainer container = ModList.get().getModContainerById(modId).orElse(null);
        return container == null ? null : container.getModInfo().getVersion().toString();
    }

    private static String installedName(Entry entry) {
        ModContainer container = ModList.get().getModContainerById(entry.modId()).orElse(null);
        return container == null ? entry.fallbackName() : container.getModInfo().getDisplayName();
    }

    public static List<Component> feedbackLines() {
        List<Component> lines = new ArrayList<>(ENTRIES.size() + 1);
        lines.add(Component.translatable("command.taczfixes.compat.header", versionLabel())
                .withStyle(ChatFormatting.BOLD));
        for (Entry entry : ENTRIES) {
            Component name = Component.literal(installedName(entry)).withStyle(ChatFormatting.GOLD);
            String version = installedVersion(entry.modId());
            Component line = version == null
                    ? name.copy().append(": ").append(Component
                            .translatable("command.taczfixes.compat.not_installed")
                            .withStyle(ChatFormatting.RED))
                    : name.copy().append(": ").append(Component.literal(version)
                            .withStyle(ChatFormatting.GREEN));
            lines.add(line);
        }
        return lines;
    }
}
