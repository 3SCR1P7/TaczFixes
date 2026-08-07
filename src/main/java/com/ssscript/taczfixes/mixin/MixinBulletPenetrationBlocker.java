package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.Config;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityKineticBullet.class)
public class MixinBulletPenetrationBlocker {
    @Shadow(remap = false)
    private int pierce;

    @Inject(method = "onHitEntity", at = @At("RETURN"), remap = false)
    private void taczfixes$blockPenetration(TacHitResult result, Vec3 startVec, Vec3 endVec, CallbackInfo ci) {
        if (!Config.PENETRATION_BLOCKED_ENTITIES.get().contains(
                ForgeRegistries.ENTITY_TYPES.getKey(result.getEntity().getType()).toString())) {
            return;
        }
        this.pierce = 0;
    }
}
