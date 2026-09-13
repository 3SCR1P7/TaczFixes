package com.ssscript.taczfixes.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.mixin.MixinGunHudOverlayAccessor;
import com.ssscript.taczfixes.client.util.GunPackIconLoader;
import com.ssscript.taczfixes.common.data.CustomFireModeManager;
import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.gui.overlay.HeatBarOverlay;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAmmoIndex;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 自定义枪械 HUD 渲染。 */
public final class CustomHudRenderer {

    private static final java.text.DecimalFormat CURRENT_AMMO_FORMAT = new java.text.DecimalFormat("000");
    private static final java.text.DecimalFormat CURRENT_AMMO_FORMAT_PERCENT = new java.text.DecimalFormat("000%");
    private static final java.text.DecimalFormat INVENTORY_AMMO_FORMAT = new java.text.DecimalFormat("0000");
    private static final ResourceLocation FIRE_MODE_SEMI = new ResourceLocation("tacz", "textures/hud/fire_mode_semi.png");
    private static final ResourceLocation FIRE_MODE_AUTO = new ResourceLocation("tacz", "textures/hud/fire_mode_auto.png");
    private static final ResourceLocation FIRE_MODE_BURST = new ResourceLocation("tacz", "textures/hud/fire_mode_burst.png");

    private CustomHudRenderer() {
    }

    public static void render(GuiGraphics graphics, CustomHudDefinition def, LocalPlayer player,
                              ItemStack stack, IGun gun, int width, int height) {
        if (def == null) return;
        ResourceLocation gunId = gun.getGunId(stack);
        ClientGunIndex index = gunId == null ? null : TimelessAPI.getClientGunIndex(gunId).orElse(null);
        GunData gunData = index == null ? null : index.getGunData();
        if (gunData == null) return;
        GunDisplayInstance display = TimelessAPI.getGunDisplay(stack).orElse(null);

        boolean useInventoryAmmo = gun.useInventoryAmmo(stack);
        boolean overheatLocked = gunData.hasHeatData() && gun.isOverheatLocked(stack);
        boolean barrel = gun.hasBulletInBarrel(stack) && gunData.getBolt() != Bolt.OPEN_BOLT;
        // 复用 tacz 原生弹药计算(弹容缓存/背包弹量), 仅位置/尺寸/颜色/格式化代码由自定义 HUD 控制
        MixinGunHudOverlayAccessor.taczfixes$handleCacheCount(player, stack, gunData, gun, useInventoryAmmo);
        int maxAmmo = Math.max(1, MixinGunHudOverlayAccessor.taczfixes$getCacheMaxAmmoCount());
        int inventoryAmmo = MixinGunHudOverlayAccessor.taczfixes$getCacheInventoryAmmoCount();
        int currentAmmo = Math.min((useInventoryAmmo ? inventoryAmmo : gun.getCurrentAmmoCount(stack))
                + (barrel ? 1 : 0), 9999);
        // tacz 原生判定: 弹量低于弹容 25% 且低于 10 发, 或过热锁定时使用告警色
        boolean empty = overheatLocked || (currentAmmo < maxAmmo * 0.25 && currentAmmo < 10);

        Font font = Minecraft.getInstance().font;
        boolean percent = display != null
                && display.getAmmoCountStyle() == com.tacz.guns.client.resource.pojo.display.gun.AmmoCountStyle.PERCENT;
        String magValue = percent
                ? CURRENT_AMMO_FORMAT_PERCENT.format(currentAmmo / (float) maxAmmo)
                : CURRENT_AMMO_FORMAT.format(currentAmmo);
        Component magText = formatText(magValue,
                def.ammo_in_magazine == null ? null : def.ammo_in_magazine.format);
        float magWidth = font.width(magText) * 1.5f;

        renderGunHud(graphics, def.gun_hud, display, currentAmmo, overheatLocked, width, height,
                width - 117f, height - 44f);

        // tacz 原生分隔线: width-75 ~ width-74, height-43 ~ height-25, 白色
        if (def.gun_hud != null && !def.gun_hud.isHidden() && def.gun_hud.showDivider()) {
            graphics.fill(width - 75, height - 43, width - 74, height - 25, 0xFFFFFFFF);
        }

        if (def.ammo_in_magazine != null && !def.ammo_in_magazine.isHidden()) {
            int color = parseColor(empty ? def.ammo_in_magazine.color_empty : def.ammo_in_magazine.color, 0xFFFFFF);
            drawText(graphics, font, magText, def.ammo_in_magazine, 1.5f, color, width, height,
                    width - 70f, height - 43f);
        }

        if (def.ammo_in_inventory != null && !def.ammo_in_inventory.isHidden()) {
            String value = useInventoryAmmo ? ""
                    : (gunData.getReloadData().isInfinite() ? "\u221e" : INVENTORY_AMMO_FORMAT.format(inventoryAmmo));
            int color = parseColor(def.ammo_in_inventory.color, 0xFFFFFF);
            Component text = formatText(value, def.ammo_in_inventory.format);
            drawText(graphics, font, text, def.ammo_in_inventory, 0.8f, color, width, height,
                    width - 68f + magWidth, height - 43f);
        }

        renderFireMode(graphics, def.fire_mode, player, width, height,
                width - 68.5f + magWidth, height - 38f);

        if (def.bullet_type_text != null && !def.bullet_type_text.isHidden()) {
            ResourceLocation ammoId = gunData.getAmmoId();
            String key = TimelessAPI.getClientAmmoIndex(ammoId).map(ClientAmmoIndex::getName)
                    .orElse(ammoId == null ? "" : ammoId.toString());
            int color = parseColor(empty ? def.bullet_type_text.color_empty : def.bullet_type_text.color, 0xFFFFFF);
            MutableComponent text = Component.translatable(key);
            applyFormat(text, def.bullet_type_text.format);
            drawText(graphics, font, text, def.bullet_type_text, 1.5f, color, width, height);
        }

        if (def.bullet_type_icon != null && !def.bullet_type_icon.isHidden()) {
            ResourceLocation ammoId = gunData.getAmmoId();
            if (ammoId != null) {
                ItemStack ammoStack = AmmoItemBuilder.create().setId(ammoId).build();
                float s = def.bullet_type_icon.size();
                float w = 16 * s;
                float h = 16 * s;
                float[] p = anchor(def.bullet_type_icon, w, h, width, height);
                PoseStack pose = graphics.pose();
                pose.pushPose();
                pose.translate(p[0], p[1], 0);
                pose.scale(s, s, 1f);
                graphics.renderItem(ammoStack, 0, 0);
                pose.popPose();
            }
        }


        if (def.addition != null && !def.addition.isHidden() && def.addition.texture != null) {
            ResourceLocation tex = ResourceLocation.tryParse(def.addition.texture);
            GunPackIconLoader.LoadedIcon icon = tex == null ? null : GunPackIconLoader.load(tex);
            if (icon != null) {
                float s = def.addition.size();
                float w = icon.width() * s;
                float h = icon.height() * s;
                float[] p = anchor(def.addition, w, h, width, height);
                prepareBlit();
                graphics.blit(icon.texture(), (int) p[0], (int) p[1], 0, 0, (int) w, (int) h, (int) w, (int) h);
            }
        }

        if (def.crosshair != null && !def.crosshair.isHidden() && def.crosshair.texture != null) {
            ResourceLocation tex = ResourceLocation.tryParse(def.crosshair.texture);
            GunPackIconLoader.LoadedIcon icon = tex == null ? null : GunPackIconLoader.load(tex);
            if (icon != null) {
                int x = (width - icon.width()) / 2;
                int y = (height - icon.height()) / 2;
                prepareBlit();
                graphics.blit(icon.texture(), x, y, 0, 0, icon.width(), icon.height(),
                        icon.width(), icon.height());
            }
        }
    }

    /** tacz 原生每次 blit 前都会重设这些状态(文字绘制会关闭混合), 否则贴图抗锯齿像素会变不透明。 */
    private static void prepareBlit() {
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 开火模式图标。逻辑与 tacz 原生一致: 按当前模式选贴图, 仅位置/尺寸/图标可由 HUD 配置覆盖。 */
    private static void renderFireMode(GuiGraphics graphics, CustomHudDefinition.FireModeElement element,
                                       LocalPlayer player, int width, int height,
                                       float defaultX, float defaultY) {
        if (element == null || element.isHidden()) return;
        String mode = switch (IGun.getMainHandFireMode(player)) {
            case AUTO -> "auto";
            case BURST -> "burst";
            default -> "semi";
        };
        float s = element.size();
        GunPackIconLoader.LoadedIcon custom = null;
        if (element.icon != null) {
            String iconId = element.icon.get(mode);
            ResourceLocation iconLoc = iconId == null ? null : ResourceLocation.tryParse(iconId);
            custom = iconLoc == null ? null : GunPackIconLoader.load(iconLoc);
        }
        ResourceLocation tex = custom != null ? custom.texture() : switch (mode) {
            case "auto" -> FIRE_MODE_AUTO;
            case "burst" -> FIRE_MODE_BURST;
            default -> FIRE_MODE_SEMI;
        };
        float w = (custom != null ? custom.width() : 10) * s;
        float h = (custom != null ? custom.height() : 10) * s;
        float[] p = anchor(element, w, h, width, height, defaultX, defaultY);
        prepareBlit();
        graphics.blit(tex, (int) p[0], (int) p[1], 0, 0, (int) w, (int) h, (int) w, (int) h);
    }

    private static void renderGunHud(GuiGraphics graphics, CustomHudDefinition.HudElement element,
                                     GunDisplayInstance display, int currentAmmo, boolean overheatLocked,
                                     int width, int height, float defaultX, float defaultY) {
        if (element == null || element.isHidden() || display == null) return;
        ResourceLocation tex = display.getHUDTexture();
        boolean grey = false;
        if (currentAmmo <= 0 || overheatLocked) {
            ResourceLocation emptyTex = display.getHudEmptyTexture();
            if (emptyTex != null) {
                tex = emptyTex;
            } else {
                grey = true;
            }
        }
        if (tex == null) return;
        float s = element.size();
        float w = 39 * s;
        float h = 13 * s;
        float[] p = anchor(element, w, h, width, height, defaultX, defaultY);
        prepareBlit();
        if (grey) {
            RenderSystem.setShaderColor(1.0F, 0.3F, 0.3F, 1.0F);
        }
        graphics.blit(tex, (int) p[0], (int) p[1], 0, 0, (int) w, (int) h, (int) w, (int) h);
        if (grey) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static void drawText(GuiGraphics graphics, Font font, Component text,
                                 CustomHudDefinition.HudElement element, float baseScale, int color,
                                 int width, int height) {
        drawText(graphics, font, text, element, baseScale, color, width, height, Float.NaN, Float.NaN);
    }

    private static void drawText(GuiGraphics graphics, Font font, Component text,
                                 CustomHudDefinition.HudElement element, float baseScale, int color,
                                 int width, int height, float defaultX, float defaultY) {
        float s = baseScale * element.size();
        int textWidth = font.width(text);
        float w = textWidth * s;
        float h = font.lineHeight * s;
        float[] p = anchor(element, w, h, width, height, defaultX, defaultY);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(p[0], p[1], 0);
        pose.scale(s, s, 1f);
        graphics.drawString(font, text, 0, 0, color, false);
        pose.popPose();
    }

    /** location=(top/middle/buttom)_(right/middle/left) 或 default(tacz 默认位置),
     *  position 相对锚点, y 正方向为上。 */
    public static float[] anchor(CustomHudDefinition.HudElement element, float w, float h,
                                 int width, int height) {
        return anchor(element, w, h, width, height, Float.NaN, Float.NaN);
    }

    public static float[] anchor(CustomHudDefinition.HudElement element, float w, float h,
                                 int width, int height, float defaultX, float defaultY) {
        if ("default".equals(element.location()) && !Float.isNaN(defaultX)) {
            return new float[]{defaultX + element.posX(), defaultY - element.posY()};
        }
        String[] parts = element.location().split("_");
        String vertical = parts.length > 0 ? parts[0] : "buttom";
        String horizontal = parts.length > 1 ? parts[1] : "right";
        float x = switch (horizontal) {
            case "left" -> 0;
            case "middle" -> (width - w) / 2f;
            default -> width - w;
        };
        float y = switch (vertical) {
            case "top" -> 0;
            case "middle" -> (height - h) / 2f;
            default -> height - h;
        };
        x += element.posX();
        y -= element.posY();
        return new float[]{x, y};
    }

    private static Component formatText(String value, List<String> formats) {
        MutableComponent text = Component.literal(value == null ? "" : value);
        applyFormat(text, formats);
        return text;
    }

    private static void applyFormat(MutableComponent text, List<String> formats) {
        if (formats == null) return;
        for (String name : formats) {
            ChatFormatting format = switch (name == null ? "" : name.toLowerCase(Locale.ROOT)) {
                case "obfuscated" -> ChatFormatting.OBFUSCATED;
                case "bold" -> ChatFormatting.BOLD;
                case "strikethrough" -> ChatFormatting.STRIKETHROUGH;
                case "underlined" -> ChatFormatting.UNDERLINE;
                case "italic" -> ChatFormatting.ITALIC;
                default -> null;
            };
            if (format != null) {
                text.withStyle(format);
            }
        }
    }

    private static int parseColor(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        String hex = value.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        try {
            int rgb = Integer.parseInt(hex, 16);
            return 0xFF000000 | (rgb & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
