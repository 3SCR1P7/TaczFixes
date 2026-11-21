package com.ssscript.taczfixes.common.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class TaczFixesMixinPlugin implements IMixinConfigPlugin {
    private static final boolean GD656PEEK_PRESENT;
    private static final boolean PARCOOL_PRESENT;
    private static final boolean LRTACTICAL_PRESENT;
    private static final boolean TOUHOU_LITTLE_MAID_PRESENT;
    private static final boolean YES_STEVE_MODEL_PRESENT;
    private static final boolean IRONS_SPELLBOOKS_PRESENT;
    private static final boolean EMBEDDIUM_PRESENT;
    private static final boolean CREATE_PRESENT;
    private static final boolean ACCELERATED_PRESENT;
    private static final boolean PLAYER_ANIMATOR_PRESENT;
    private static final boolean ARCANA_PRESENT;

    static {
        GD656PEEK_PRESENT = hasResource("org/mods/gd656peek/compat/tacz/TaczPeekHitboxHelper.class");
        PARCOOL_PRESENT = hasResource("com/alrex/parcool/client/hud/impl/StaminaHUDController.class");
        LRTACTICAL_PRESENT = hasResource("me/xjqsh/lrtactical/item/MeleeItem.class");
        TOUHOU_LITTLE_MAID_PRESENT = hasResource("com/github/tartaricacid/touhoulittlemaid/TouhouLittleMaid.class");
        YES_STEVE_MODEL_PRESENT = hasResource("com/elfmcys/yesstevemodel/YesSteveModel.class");
        IRONS_SPELLBOOKS_PRESENT = hasResource("io/redspace/ironsspellbooks/api/util/Utils.class");
        EMBEDDIUM_PRESENT = hasResource("me/jellysquid/mods/sodium/client/gl/shader/ShaderLoader.class");
        CREATE_PRESENT = hasResource("com/simibubi/create/content/contraptions/AbstractContraptionEntity.class");
        ACCELERATED_PRESENT = hasResource("com/github/argon4w/acceleratedrendering/core/buffers/accelerated/AcceleratedBufferSource.class");
        PLAYER_ANIMATOR_PRESENT = hasResource("dev/kosmx/playerAnim/api/layered/modifier/AdjustmentModifier.class");
        // Arcana 的兼容 mixin 直接把 group.taczexpands.* 类型写进了方法签名/调用点, 类不存在时不能应用。
        ARCANA_PRESENT = hasResource("group/taczexpands/common/accessor/IAccessorAttachmentData.class");
    }

    private static boolean hasResource(String path) {
        try {
            return TaczFixesMixinPlugin.class.getClassLoader().getResource(path) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if ("com.ssscript.taczfixes.common.mixin.MixinTaczPeekHeadshotLimit".equals(mixinClassName)) {
            return GD656PEEK_PRESENT;
        }
        // 客户端 peek 兼容 mixin 直接 @Mixin(gd656peek 的 PeekHandler), 同样要按模组存在与否过滤。
        if ("com.ssscript.taczfixes.client.mixin.compat.MixinPeekHandler".equals(mixinClassName)) {
            return GD656PEEK_PRESENT;
        }
        // Arcana 兼容 mixin 依赖 group.taczexpands.*, 未安装时直接跳过。
        if (mixinClassName.contains(".MixinArcana")) {
            return ARCANA_PRESENT;
        }
        if ("com.ssscript.taczfixes.common.mixin.MixinEntityKineticBulletCreateCollision".equals(mixinClassName)) {
            return CREATE_PRESENT;
        }
        if (mixinClassName.startsWith("com.ssscript.taczfixes.client.mixin.compat.create.")) {
            return CREATE_PRESENT;
        }
        if (mixinClassName.contains(".MixinParCool")) {
            return PARCOOL_PRESENT;
        }
        if (mixinClassName.contains(".MixinAccelerated")
                || mixinClassName.contains("MixinGlintSourceAccelerated")
                || mixinClassName.contains("MixinGunGlintVertexConsumerAccelerated")) {
            return ACCELERATED_PRESENT;
        }
        if (mixinClassName.contains(".MixinLr")) {
            return LRTACTICAL_PRESENT;
        }
        if (mixinClassName.contains(".MixinTml") || mixinClassName.contains(".MixinTouhouMaid")) {
            return TOUHOU_LITTLE_MAID_PRESENT;
        }
        if (mixinClassName.contains(".MixinYsm")) {
            return YES_STEVE_MODEL_PRESENT;
        }
        if (mixinClassName.contains(".MixinIrons")) {
            return IRONS_SPELLBOOKS_PRESENT;
        }
        if (mixinClassName.contains(".MixinEmbeddium")) {
            return EMBEDDIUM_PRESENT;
        }
        if ("com.ssscript.taczfixes.client.mixin.compat.MixinPlayerAnimatorAnimationManager".equals(mixinClassName)
                || "com.ssscript.taczfixes.client.mixin.compat.MixinPlayerAnimatorAdjustment".equals(mixinClassName)) {
            return PLAYER_ANIMATOR_PRESENT;
        }
        return true;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
