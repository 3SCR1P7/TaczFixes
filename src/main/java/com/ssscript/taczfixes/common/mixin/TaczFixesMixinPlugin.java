package com.ssscript.taczfixes.common.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class TaczFixesMixinPlugin implements IMixinConfigPlugin {
    private static final boolean PARCOOL_PRESENT;
    private static final boolean LRTACTICAL_PRESENT;
    private static final boolean IRONS_SPELLBOOKS_PRESENT;
    private static final boolean EMBEDDIUM_PRESENT;
    private static final boolean SODIUM_PRESENT;
    private static final boolean CREATE_PRESENT;
    private static final boolean ACCELERATED_PRESENT;

    static {
        PARCOOL_PRESENT = hasResource("com/alrex/parcool/common/attachment/common/Parkourability.class");
        LRTACTICAL_PRESENT = hasResource("me/xjqsh/lrtactical/item/MeleeItem.class");
        IRONS_SPELLBOOKS_PRESENT = hasResource("io/redspace/ironsspellbooks/api/util/Utils.class");
        EMBEDDIUM_PRESENT = hasResource("me/jellysquid/mods/sodium/client/gl/shader/ShaderLoader.class");
        SODIUM_PRESENT = hasResource("net/caffeinemc/mods/sodium/client/gl/shader/ShaderLoader.class")
                || EMBEDDIUM_PRESENT;
        CREATE_PRESENT = hasResource("com/simibubi/create/content/contraptions/AbstractContraptionEntity.class");
        ACCELERATED_PRESENT = hasResource("com/github/argon4w/acceleratedrendering/core/buffers/accelerated/AcceleratedBufferSource.class");
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
        if ("com.ssscript.taczfixes.common.mixin.MixinEntityKineticBulletCreateCollision".equals(mixinClassName)) {
            return CREATE_PRESENT;
        }
        if (mixinClassName.startsWith("com.ssscript.taczfixes.client.mixin.compat.create.")) {
            return CREATE_PRESENT;
        }
        if (mixinClassName.contains(".MixinParCool")) {
            return PARCOOL_PRESENT;
        }
        if (mixinClassName.contains(".MixinAccelerated")) {
            return ACCELERATED_PRESENT;
        }
        if (mixinClassName.contains(".MixinLr")) {
            return LRTACTICAL_PRESENT;
        }
        if (mixinClassName.contains(".MixinIrons")) {
            return IRONS_SPELLBOOKS_PRESENT;
        }
        if (mixinClassName.contains(".MixinEmbeddium")) {
            return EMBEDDIUM_PRESENT;
        }
        if (mixinClassName.startsWith("com.ssscript.taczfixes.client.mixin.compat.sodium.")) {
            return SODIUM_PRESENT;
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
