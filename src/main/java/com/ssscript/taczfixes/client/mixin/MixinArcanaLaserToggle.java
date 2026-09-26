package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.ssscript.taczfixes.common.util.DualWieldStackId;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TaCZ: Arcana 的激光指示器开关记录在单把枪械的 NBT 上, 双持时只会切换主手。
 * 副手激光渲染读取开关时改用主手枪械的状态, 使开关对双手同时生效。
 */
@Mixin(targets = "group/taczexpands/dist/wDqHZ7qY", remap = false)
public class MixinArcanaLaserToggle {

    @Unique
    private static Method taczfixes$laserFlagMethod;

    @Inject(method = "YroxkNPW", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$mirrorMainHandLaserToggle(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }
        ItemStack main = player.getMainHandItem();
        if (stack == main || main.isEmpty()) {
            return;
        }
        if (!DualWieldEligibility.isDualWielding(player)) {
            return;
        }
        if (!DualWieldStackId.matches(stack, player.getOffhandItem())) {
            return;
        }
        try {
            Method method = taczfixes$laserFlagMethod;
            if (method == null) {
                method = getClass().getMethod("YroxkNPW", ItemStack.class);
                taczfixes$laserFlagMethod = method;
            }
            Object result = method.invoke(this, main);
            if (result instanceof Boolean flag) {
                cir.setReturnValue(flag);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }
}
