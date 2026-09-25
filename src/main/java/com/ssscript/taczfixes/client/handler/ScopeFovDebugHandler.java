package com.ssscript.taczfixes.client.handler;

import com.mojang.logging.LogUtils;
import com.ssscript.taczfixes.client.util.ScopeFovDebug;
import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.ssscript.taczfixes.client.util.ScopeViewHelper;
import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

/** 开镜时每 tick 记录: 是否启用镜内放大、镜外/镜内 FOV(两种 getFov 调用) 到 logs/taczfixes_scope_fov.log。 */
public class ScopeFovDebugHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String HEADER = "# tick aiming attachment zoomNumber scopeType combined scopeView "
            + "magnificationEnabled gateConfig fovWorld(世界层) fovLens(其它configured调用) fovOther(useFovSetting=false)";
    private static BufferedWriter writer;
    private static boolean headerPending;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (operator == null || !operator.isAim()) {
            return;
        }
        ItemStack gun = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            return;
        }
        ResourceLocation id = ScopeSwitchState.attachmentId(iGun, gun, AttachmentType.SCOPE);
        CompoundTag tag = ScopeSwitchState.attachmentTag(iGun, gun, AttachmentType.SCOPE);
        boolean hasScope = id != null && !DefaultAssets.isEmptyAttachmentId(id);
        ClientAttachmentIndex index = hasScope ? TimelessAPI.getClientAttachmentIndex(id).orElse(null) : null;
        boolean scopeType = index != null && index.isScope();
        boolean combined = index != null && ScopeViewHelper.isCombinedSight(index);
        boolean scopeView = index != null && ScopeViewHelper.isScopeView(index, tag);
        boolean disabled = index != null && (combined ? !scopeView : !scopeType);
        boolean magnification = index != null && !disabled;
        int zoomNumber = tag == null ? 0 : AttachmentItemDataAccessor.getZoomNumberFromTag(tag);
        String line = String.format(Locale.ROOT,
                "%d %.2f %s %d %s %s %s %s %s %.3f %.3f %.3f",
                player.tickCount,
                operator.getClientAimingProgress(1.0f),
                id == null ? "none" : id.toString(),
                zoomNumber,
                scopeType,
                combined,
                scopeView,
                magnification,
                Config.DISABLE_ARCANA_MAGNIFICATION_FOR_SIGHT.get(),
                ScopeFovDebug.getFovWorld(),
                ScopeFovDebug.getFovLens(),
                ScopeFovDebug.getFovOther());
        write(line);
    }

    private static void write(String line) {
        try {
            if (writer == null) {
                Path path = FMLPaths.GAMEDIR.get().resolve("logs").resolve("taczfixes_scope_fov.log");
                Files.createDirectories(path.getParent());
                writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                headerPending = true;
                LOGGER.info("[taczfixes] scope fov debug log: {}", path);
            }
            if (headerPending) {
                writer.write(HEADER);
                writer.newLine();
                headerPending = false;
            }
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (Throwable ignored) {
        }
    }
}
