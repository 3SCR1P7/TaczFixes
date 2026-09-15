package com.ssscript.taczfixes.common.mixin;

import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.item.ModernKineticGunItem;
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.util.DualReloadTimeController;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import com.ssscript.taczfixes.common.util.OffhandShooterManager;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.luaj.vm2.LuaValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {ModernKineticGunItem.class}, remap = false)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/common/mixin/MixinModernKineticGunItemReload.class */
public abstract class MixinModernKineticGunItemReload {

    @Unique
    private static final AtomicBoolean TACZFIXES$WARNED_MISSING_RESULT = new AtomicBoolean();

    @Inject(method = {"startReload"}, at = {@At("HEAD")})
    private void dualWield$beginSlowedReload(ShooterDataHolder dataHolder, ItemStack gunItem, LivingEntity shooter, CallbackInfoReturnable<Boolean> callback) {
        DualReloadTimeController.begin(dataHolder, gunItem, shooter);
    }

    @Inject(method = {"startReload"}, at = {@At("RETURN")})
    private void dualWield$discardRejectedReload(ShooterDataHolder dataHolder, ItemStack gunItem, LivingEntity shooter, CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ()) {
            DualReloadTimeController.end(dataHolder, gunItem);
        }
    }

    @Inject(method = {"tickReload"}, at = {@At("RETURN")})
    private void dualWield$convertReloadCountDown(ShooterDataHolder dataHolder, ItemStack gunItem, LivingEntity shooter, CallbackInfoReturnable<ReloadState> callback) {
        ReloadState reloadState = (ReloadState) callback.getReturnValue();
        if (reloadState == null) {
            DualReloadTimeController.end(dataHolder, gunItem);
        } else if (reloadState.getStateType().isReloading()) {
            reloadState.setCountDown(DualReloadTimeController.toRealCountDown(dataHolder, gunItem, reloadState.getCountDown()));
        } else {
            DualReloadTimeController.end(dataHolder, gunItem);
        }
    }

    @Redirect(method = {"lambda$startReload$10(Lcom/tacz/guns/item/ModernKineticGunScriptAPI;Lorg/luaj/vm2/LuaFunction;)Ljava/lang/Boolean;"}, at = @At(value = "INVOKE", target = "Lorg/luaj/vm2/LuaValue;checkboolean()Z"), require = ServerMessageOffhandActionResult.ACTION_RELOAD)
    private static boolean dualWield$acceptMissingOffhandReloadResult(LuaValue result) {
        ShooterDataHolder activeData = OffhandShooterManager.getActiveData();
        if (result.isnil() && OffhandShooterManager.isOffhandData(activeData)) {
            if (TACZFIXES$WARNED_MISSING_RESULT.compareAndSet(false, true)) {
                TaczFixesMod.LOGGER.warn("An offhand start_reload script returned nil; treating the missing result as true");
                return true;
            }
            return true;
        }
        return result.checkboolean();
    }
}
