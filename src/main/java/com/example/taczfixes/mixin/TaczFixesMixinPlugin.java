package com.example.taczfixes.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * 按运行环境决定是否应用部分 mixin：
 * MixinTaczPeekHeadshotLimit 需要 GD656Peek 模组，缺失时跳过，避免崩溃。
 */
public class TaczFixesMixinPlugin implements IMixinConfigPlugin {
    private static final boolean GD656PEEK_PRESENT;

    static {
        GD656PEEK_PRESENT = hasResource("org/mods/gd656peek/compat/tacz/TaczPeekHitboxHelper.class");
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
        if ("com.example.taczfixes.mixin.MixinTaczPeekHeadshotLimit".equals(mixinClassName)) {
            return GD656PEEK_PRESENT;
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