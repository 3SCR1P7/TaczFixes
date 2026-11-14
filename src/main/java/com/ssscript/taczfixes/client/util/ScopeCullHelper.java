package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.client.client.DualRenderContext;
import com.ssscript.taczfixes.client.handler.AimSwayHandler;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** 筒状瞄具"镜内视野"判定: 供镜内剔除 stencil 门与备用瞄具剔除逻辑复用。 */
public final class ScopeCullHelper {

    public enum LensOwner { NONE, STANDARD, CUSTOM }

    /** 当前镜片归属: 自定义槽瞄具优先, 否则标准槽/内置瞄具。 */
    public static LensOwner activeLensOwner(ItemStack gun) {
        if (gun == null || gun.isEmpty()) return LensOwner.NONE;
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) return LensOwner.NONE;
        String slot = ScopeSwitchState.getActiveSlot(gun);
        if (slot != null && !slot.isEmpty()) {
            ItemStack item = com.ssscript.taczfixes.common.util.CustomSlotStorage.get(gun, slot);
            if (isScopeLike(item)) return LensOwner.CUSTOM;
        }
        ResourceLocation id = activeScopeId(iGun, gun);
        return id != null ? LensOwner.STANDARD : LensOwner.NONE;
    }

    /** TaCZ 新版同款镜内剔除门: 只画镜片外(EQUAL 0), 不修改遮罩(KEEP)。 */
    public static void applyLensGate() {
        com.tacz.guns.util.RenderHelper.enableItemEntityStencilTest();
        com.mojang.blaze3d.systems.RenderSystem.stencilFunc(org.lwjgl.opengl.GL11.GL_EQUAL, 0, 255);
        com.mojang.blaze3d.systems.RenderSystem.stencilOp(org.lwjgl.opengl.GL11.GL_KEEP,
                org.lwjgl.opengl.GL11.GL_KEEP, org.lwjgl.opengl.GL11.GL_KEEP);
    }

    private static boolean physicalOpticPass;

    /** 标准槽瞄具延后补画时置 true: 让 MixinBedrockAttachmentModel 走物理分支(不调用 renderScope)。 */
    private static boolean standbyOpticPass;

    /** standby 瞄具物理渲染: 只显示 scope_ocular, 隐藏 sight_ocular 与 division。 */
    public static boolean isStandbyOpticPass() {
        return standbyOpticPass;
    }

    public static void setStandbyOpticPass(boolean value) {
        standbyOpticPass = value;
    }

    public static boolean isPhysicalOpticPass() {
        return physicalOpticPass;
    }

    public static void setPhysicalOpticPass(boolean value) {
        physicalOpticPass = value;
    }

    public static int saveStencilWriteMask() {
        return org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_STENCIL_WRITEMASK);
    }

    public static void restoreStencilWriteMask(int mask) {
        if (mask >= 0) com.mojang.blaze3d.systems.RenderSystem.stencilMask(mask);
    }

    private static boolean isScopeLike(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        com.tacz.guns.api.item.IAttachment ia = com.tacz.guns.api.item.IAttachment.getIAttachmentOrNull(item);
        if (ia == null) return false;
        ResourceLocation id = ia.getAttachmentId(item);
        return id != null && TimelessAPI.getClientAttachmentIndex(id)
                .map(index -> index.isScope() || index.isSight()).orElse(false);
    }
    private ScopeCullHelper() {
    }

    /** 标准槽瞄具(含 builtin), 使用 TaCZ 的 IGun 接口解析, 避免直接读 NBT 失败。 */
    public static ItemStack standardScope(ItemStack gun) {
        if (gun == null || gun.isEmpty()) return ItemStack.EMPTY;
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) return ItemStack.EMPTY;
        ItemStack scope = iGun.getAttachment(gun, AttachmentType.SCOPE);
        if (scope != null && !scope.isEmpty()) return scope;
        ItemStack builtin = iGun.getBuiltinAttachment(gun, AttachmentType.SCOPE);
        return builtin == null ? ItemStack.EMPTY : builtin;
    }

    /** 标准槽/内置瞄具是否存在。 */
    public static boolean hasStandardOptic(ItemStack gun) {
        if (gun == null || gun.isEmpty()) return false;
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) return false;
        ResourceLocation id = iGun.getAttachmentId(gun, AttachmentType.SCOPE);
        if (id != null && !DefaultAssets.isEmptyAttachmentId(id)) {
            return TimelessAPI.getClientAttachmentIndex(id)
                    .map(index -> index.isScope() || index.isSight()).orElse(false);
        }
        ResourceLocation builtin = iGun.getBuiltInAttachmentId(gun, AttachmentType.SCOPE);
        if (builtin != null && !DefaultAssets.isEmptyAttachmentId(builtin)) {
            return TimelessAPI.getClientAttachmentIndex(builtin)
                    .map(index -> index.isScope() || index.isSight()).orElse(false);
        }
        return false;
    }

    /** 自定义槽中安装的瞄具槽位。 */
    public static java.util.List<String> customOpticSlots(ItemStack gun) {
        java.util.List<String> result = new java.util.ArrayList<>();
        if (gun == null || gun.isEmpty()) return result;
        for (java.util.Map.Entry<String, com.ssscript.taczfixes.common.data.CustomSlotManager.SlotEntry> entry
                : com.ssscript.taczfixes.common.data.CustomSlotManager.getEntries(gun).entrySet()) {
            if (!entry.getValue().source().isEmpty()) continue;
            if (isScopeLike(com.ssscript.taczfixes.common.util.CustomSlotStorage.get(gun, entry.getKey()))) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /** 当前 active 的自定义槽是否是瞄具。 */
    public static boolean isCustomActiveOptic(ItemStack gun) {
        if (gun == null || gun.isEmpty()) return false;
        String slot = ScopeSwitchState.getActiveSlot(gun);
        if (slot == null || slot.isEmpty()) return false;
        return isScopeLike(com.ssscript.taczfixes.common.util.CustomSlotStorage.get(gun, slot));
    }

    /** 枪上瞄具总数(标准槽/内置 + 自定义槽)。 */
    public static int opticCount(ItemStack gun) {
        return customOpticSlots(gun).size() + (hasStandardOptic(gun) ? 1 : 0);
    }

    /** 当前手持枪是否处于纯筒状瞄具的镜内视野(第一人称已开镜, 且活跃瞄具不是组合瞄具的 sight 视图)。 */
    public static boolean pureScopeLensViewActive() {
        ItemStack gun = heldGun();
        if (!aimingAtScope(gun)) return false;
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) return false;
        ResourceLocation id = activeScopeId(iGun, gun);
        if (id == null) return false;
        return TimelessAPI.getClientAttachmentIndex(id)
                .map(index -> index.isScope() && !index.isSight())
                .orElse(false);
    }

    /** 手持枪是否处于任意瞄具的镜内视野(用于剔除备用瞄具, 组合瞄具的 sight 视图不算)。 */
    public static boolean lensViewActive(ItemStack gun) {
        if (!aimingAtScope(gun)) return false;
        return AimSwayHandler.isScopeActive(Minecraft.getInstance().player);
    }


    private static ItemStack heldGun() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? ItemStack.EMPTY : player.getMainHandItem();
    }

    private static boolean aimingAtScope(ItemStack gun) {
        if (gun == null || gun.isEmpty()) return false;
        if (DualRenderContext.getPhase() == DualRenderContext.HandPhase.OFFHAND) return false;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !player.isAlive()) return false;
        if (player.getMainHandItem().getItem() != gun.getItem()) return false;
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        return operator != null && operator.getClientAimingProgress(mc.getFrameTime()) > 0.01f;
    }

    private static ResourceLocation activeScopeId(IGun iGun, ItemStack gun) {
        ResourceLocation id = ScopeSwitchState.activeCustomScopeId(gun);
        if (id == null) {
            id = ScopeSwitchState.attachmentId(iGun, gun, AttachmentType.SCOPE);
        }
        if (id == null || DefaultAssets.isEmptyAttachmentId(id)) {
            id = iGun.getBuiltInAttachmentId(gun, AttachmentType.SCOPE);
        }
        return id == null || DefaultAssets.isEmptyAttachmentId(id) ? null : id;
    }
}
