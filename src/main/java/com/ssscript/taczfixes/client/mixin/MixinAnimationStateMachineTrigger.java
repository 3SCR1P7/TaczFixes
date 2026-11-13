package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.attachment.AttachmentAnimationManager;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把 TaCZ 自身触发到枪械状态机的输入（shoot/reload/cancel_reload/blot/fire_select/inspect/
 * bayonet_* / idle / run / walk / draw / put_away）同时转发给配件状态机。
 *
 * <p>TaCZ 在 LocalPlayerShoot/Reload/Bolt/FireSelect/Inspect/Melee、TickAnimationEvent 等位置
 * 调用 {@code AnimationStateMachine#trigger}，因此从一个入口即可完整覆盖，无需逐个改动玩法类。
 */
@SuppressWarnings({"target", "mapping", "public-target", "unresolvable-target", "visibility", "constraints", "overwrite", "default-package", "raw-types"})
@Mixin(value = {AnimationStateMachine.class}, remap = false)
public abstract class MixinAnimationStateMachineTrigger {
    @Inject(method = {"trigger"}, at = {@At("HEAD")}, remap = false)
    private void taczfixes$forwardInputToAttachments(String input, CallbackInfo callback) {
        AnimationStateMachine<?> self = (AnimationStateMachine<?>) (Object) this;
        if (!(self.getContext() instanceof GunAnimationStateContext gunContext)) {
            return;
        }
        ItemStack gunItem = ((MixinGunAnimationStateContextAccessor) gunContext).taczfixes$getCurrentGunItem();
        AttachmentAnimationManager.onGunInput(gunItem, input);
    }
}
