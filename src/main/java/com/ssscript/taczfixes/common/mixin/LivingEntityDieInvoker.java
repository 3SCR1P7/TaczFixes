package com.ssscript.taczfixes.common.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 暴露 LivingEntity#die, 供"直接让血量归零并触发正常死亡流程"使用。 */
@Mixin(LivingEntity.class)
public interface LivingEntityDieInvoker {
    @Invoker("die")
    void taczfixes$die(DamageSource source);
}
