package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.SpreadState;
import com.ssscript.taczfixes.common.data.InaccuracyParams;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.util.MultishotHelper;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.tacz.guns.item.ModernKineticGunScriptAPI", remap = false)
public class MixinShootOnceIncrement {
    @Inject(method = "shootOnce", at = @At("HEAD"), remap = false)
    private void taczfixes$onShootOnce(boolean needConsumeAmmo, CallbackInfo ci) {
        ModernKineticGunScriptAPI api = (ModernKineticGunScriptAPI) (Object) this;
        LivingEntity shooter = api.getShooter();
        ItemStack gunItem = api.getItemStack();
        if (shooter == null || gunItem == null) return;
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null) return;
        ResourceLocation gunId = gun.getGunId(gunItem);
        if (gunId == null) return;
        ResourceLocation dataId = TaczFixesDataManager.resolveDataId(gunId);
        InaccuracyType state = InaccuracyType.getInaccuracyType(shooter);
        InaccuracyParams params = TaczFixesDataManager.resolveInaccuracyParams(dataId, state, gunItem);
        SpreadState.onShot(dataId, params);
        MultishotHelper.onShotStart();
    }
}
