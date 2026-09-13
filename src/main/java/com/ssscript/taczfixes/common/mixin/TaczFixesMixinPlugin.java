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

    static {
        GD656PEEK_PRESENT = hasResource("org/mods/gd656peek/compat/tacz/TaczPeekHitboxHelper.class");
        PARCOOL_PRESENT = hasResource("com/alrex/parcool/client/hud/impl/StaminaHUDController.class");
        LRTACTICAL_PRESENT = hasResource("me/xjqsh/lrtactical/item/MeleeItem.class");
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
        if (mixinClassName.contains(".MixinParCool")) {
            return PARCOOL_PRESENT;
        }
        if (mixinClassName.contains(".MixinLr")) {
            return LRTACTICAL_PRESENT;
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
