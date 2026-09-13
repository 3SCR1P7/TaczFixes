package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.GunPackIconLoader;
import com.ssscript.taczfixes.common.data.CustomFireModeManager;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.overlay.GunHudOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** 自定义开火模式 HUD 图标: 主手枪处于自定义模式且有 icon 配置时, 用 gunpack 图标替换 TACZ 默认贴图。 */
@Mixin(GunHudOverlay.class)
public class MixinGunHudOverlay {

    @ModifyArg(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;m_280163_(Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V",
            ordinal = 1), index = 0, remap = false)
    private ResourceLocation taczfixes$customFireModeIcon(ResourceLocation texture) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return texture;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IGun iGun)) return texture;
        String id = stack.getTag() != null ? stack.getTag().getString("TaczFixesCustomFireMode") : "";
        if (id.isEmpty()) return texture;
        ResourceLocation gunId = iGun.getGunId(stack);
        if (gunId == null) return texture;
        ResourceLocation dataId = TaczFixesDataManager.resolveDataId(gunId);
        var mode = CustomFireModeManager.mode(dataId, id);
        if (mode == null || mode.icon == null || mode.icon.isEmpty()) return texture;
        ResourceLocation icon = ResourceLocation.tryParse(mode.icon);
        if (icon == null) return texture;
        GunPackIconLoader.LoadedIcon loaded = GunPackIconLoader.load(icon);
        return loaded != null ? loaded.texture() : texture;
    }
}
