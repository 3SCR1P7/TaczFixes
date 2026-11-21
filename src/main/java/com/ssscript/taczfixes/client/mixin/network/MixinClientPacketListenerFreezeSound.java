package com.ssscript.taczfixes.client.mixin.network;

import com.ssscript.taczfixes.client.util.FreezeClientState;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 常凪冻结: 丢弃被冻结实体发出的声音(环境音/受伤音等实体音效包)。 */
@Mixin(ClientPacketListener.class)
public class MixinClientPacketListenerFreezeSound {
    @Inject(method = "handleSoundEntityEvent(Lnet/minecraft/network/protocol/game/ClientboundSoundEntityPacket;)V",
            at = @At("HEAD"), cancellable = true)
    private void taczfixes$muteFrozenEntitySound(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        if (FreezeClientState.isEntityFrozen(packet.getId())) {
            ci.cancel();
        }
    }
}
