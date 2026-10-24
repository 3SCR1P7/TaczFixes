package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.GunSmithTablePosAccess;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GunSmithTableBlockEntity.class)
public abstract class MixinGunSmithTableBlockEntity {

    @Inject(method = "m_7208_", at = @At("RETURN"), remap = false)
    private void taczfixes$attachTablePos(int id, Inventory inventory, Player player,
                                          CallbackInfoReturnable<AbstractContainerMenu> cir) {
        AbstractContainerMenu menu = cir.getReturnValue();
        if (menu instanceof GunSmithTablePosAccess access) {
            access.taczfixes$setTablePos(((BlockEntity) (Object) this).getBlockPos());
        }
    }
}
