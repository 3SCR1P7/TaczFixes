package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.GunSmithTableEnergy;
import com.ssscript.taczfixes.common.util.GunSmithTableEnergyAccess;
import com.ssscript.taczfixes.common.util.GunSmithTablePosAccess;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GunSmithTableBlockEntity.class)
public abstract class MixinGunSmithTableBlockEntity implements GunSmithTableEnergyAccess {

    @Unique
    private int taczfixes$energy;

    @Inject(method = "createMenu", at = @At("RETURN"), remap = false)
    private void taczfixes$attachTablePos(int id, Inventory inventory, Player player,
                                          CallbackInfoReturnable<AbstractContainerMenu> cir) {
        AbstractContainerMenu menu = cir.getReturnValue();
        if (menu instanceof GunSmithTablePosAccess access) {
            access.taczfixes$setTablePos(((BlockEntity) (Object) this).getBlockPos());
        }
    }

    @Inject(method = "load", at = @At("RETURN"), remap = false)
    private void taczfixes$loadEnergy(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        this.taczfixes$energy = Math.max(0, tag.getInt(GunSmithTableEnergy.TAG_KEY));
    }

    @Inject(method = "saveAdditional", at = @At("RETURN"), remap = false)
    private void taczfixes$saveEnergy(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        tag.putInt(GunSmithTableEnergy.TAG_KEY, Math.max(0, this.taczfixes$energy));
    }

    @Override
    public int taczfixes$getEnergy() {
        return this.taczfixes$energy;
    }

    @Override
    public void taczfixes$setEnergy(int energy) {
        this.taczfixes$energy = Math.max(0, energy);
        this.taczfixes$markEnergyChanged();
    }

    @Override
    public void taczfixes$markEnergyChanged() {
        ((BlockEntity) (Object) this).setChanged();
    }
}
