package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.AnimationListenerSupplier;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.client.render.DualFocusAimState;
import com.ssscript.taczfixes.client.render.DualInspectAnimationFilter;
import com.ssscript.taczfixes.client.render.DualManualActionAnimationFilter;
import com.ssscript.taczfixes.client.render.DualReloadSoundFilter;
import com.ssscript.taczfixes.client.render.MainhandMovementAnimation;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {AnimationController.class}, remap = false)
public abstract class MixinAnimationController {

    @Shadow
    @Final
    private AnimationListenerSupplier listenerSupplier;

    @Unique
    private int dualWield$currentRunTrack = -1;

    @Unique
    private ObjectAnimation.PlayType dualWield$currentRunPlayType;

    @Inject(method = {"run"}, at = {@At("HEAD")}, cancellable = true)
    private void dualWield$captureRunTrack(int track, String animationName, ObjectAnimation.PlayType playType, float transitionTimeSeconds, CallbackInfo callback) {
        this.dualWield$currentRunTrack = track;
        this.dualWield$currentRunPlayType = playType;
        if (DualFocusAimState.handleMainAimAnimationRun((AnimationController) (Object) this, this.listenerSupplier, track, animationName)) {
            this.dualWield$currentRunTrack = -1;
            this.dualWield$currentRunPlayType = null;
            callback.cancel();
        }
    }

    @Inject(method = {"run"}, at = {@At("RETURN")})
    private void dualWield$clearRunTrack(int track, String animationName, ObjectAnimation.PlayType playType, float transitionTimeSeconds, CallbackInfo callback) {
        this.dualWield$currentRunTrack = -1;
        this.dualWield$currentRunPlayType = null;
    }

    @Redirect(method = {"run"}, at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object dualWield$filterInspectPrototype(Map<?, ?> prototypes, Object key) {
        ObjectAnimation objectAnimation;
        String str;
        ObjectAnimation resolved;
        Object value = prototypes.get(key);
        if (value instanceof ObjectAnimation) {
            ObjectAnimation prototype = (ObjectAnimation) value;
            objectAnimation = prototype;
        } else {
            objectAnimation = null;
        }
        ObjectAnimation original = objectAnimation;
        if (key instanceof String) {
            String name = (String) key;
            str = name;
        } else {
            str = original == null ? "" : original.name;
        }
        String animationName = str;
        ObjectAnimation resolved2 = original;
        try {
            resolved2 = MainhandMovementAnimation.resolvePrototype((AnimationController) (Object) this, animationName, original, this.listenerSupplier);
        } catch (RuntimeException exception) {
            TaczFixesMod.LOGGER.error("Failed to resolve dual-wield movement animation {}", animationName, exception);
        }
        if (resolved2 == null) {
            return value;
        }
        ObjectAnimation manualActionBase = resolved2;
        try {
            resolved = DualManualActionAnimationFilter.filterPrototype(resolved2, this.listenerSupplier, this.dualWield$currentRunTrack, this.dualWield$currentRunPlayType);
        } catch (RuntimeException exception2) {
            TaczFixesMod.LOGGER.error("Failed to prepare offhand manual-action arm {}", animationName, exception2);
            resolved = manualActionBase;
        }
        ObjectAnimation reloadFiltered = resolved;
        try {
            reloadFiltered = DualReloadSoundFilter.filterPrototype((AnimationController) (Object) this, this.dualWield$currentRunTrack, animationName, resolved);
        } catch (RuntimeException exception3) {
            TaczFixesMod.LOGGER.error("Failed to create dual-wield reload sound track {}", animationName, exception3);
        }
        if (reloadFiltered != resolved) {
            return reloadFiltered;
        }
        try {
            return DualInspectAnimationFilter.filterPrototype((AnimationController) (Object) this, this.dualWield$currentRunTrack, this.dualWield$currentRunPlayType, animationName, resolved, this.listenerSupplier);
        } catch (RuntimeException exception4) {
            TaczFixesMod.LOGGER.error("Failed to filter dual-wield inspect animation {}", animationName, exception4);
            return resolved;
        }
    }
}
