package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.Config;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(Entity.class)
public class MixinEntitySprint {
    @Unique
    private static Field taczfixes$tiltKeyField;

    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void taczfixes$onSetSprinting(boolean sprinting, CallbackInfo ci) {
        if (!sprinting) return;

        Entity self = (Entity) (Object) this;
        if (!(self instanceof LocalPlayer player)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != player) return;

        if (Config.ADS_INTERRUPT_SPRINT.get() && IGun.mainHandHoldGun(player)
                && IClientPlayerGunOperator.fromLocalPlayer(player).isAim()) {
            ci.cancel();
        }
        if (taczfixes$isTiltHolding(player) && Config.PREVENT_SPRINT_REENGAGE_WHEN_TILT.get()) {
            ci.cancel();
        }
    }

    @Unique
    private static boolean taczfixes$isTiltHolding(LocalPlayer player) {
        if (!IGun.mainHandHoldGun(player)) return false;
        if (!taczfixes$isTiltKeyDown()) return false;
        ItemStack stack = player.getMainHandItem();
        GunDisplayInstance display = TimelessAPI.getGunDisplay(stack).orElse(null);
        if (display == null) return false;
        LuaAnimationStateMachine<GunAnimationStateContext> state = display.getAnimationStateMachine();
        if (state == null) return false;
        GunAnimationStateContext context = state.getContext();
        if (context == null) return false;
        return context.shouldSlide();
    }

    @Unique
    private static boolean taczfixes$isTiltKeyDown() {
        try {
            if (taczfixes$tiltKeyField == null) {
                if (!ModList.get().isLoaded("tacztweaks")) return false;
                Class<?> clazz = Class.forName("me.muksc.tacztweaks.client.input.TiltGunKey");
                taczfixes$tiltKeyField = clazz.getField("KEY");
            }
            Object key = taczfixes$tiltKeyField.get(null);
            return key instanceof KeyMapping mapping && mapping.isDown();
        } catch (Exception e) {
            return false;
        }
    }
}