package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.client.particle.MuzzleParticleManager;
import com.ssscript.taczfixes.client.render.DualMuzzleParticleContext;
import java.util.EnumMap;
import java.util.function.Function;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = {MuzzleParticleManager.class}, remap = false)
public abstract class MixinMuzzleParticleManager {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = "enqueueShoot", at = @At(value = "INVOKE", target = "Ljava/util/EnumMap;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private static Object dualWield$useOffhandKeyForOffhandShots(EnumMap pending, Object mainHandKey, Function mappingFunction) {
        Object handKey = DualMuzzleParticleContext.isOffhand() ? InteractionHand.OFF_HAND : mainHandKey;
        return pending.computeIfAbsent(handKey, mappingFunction);
    }
}
