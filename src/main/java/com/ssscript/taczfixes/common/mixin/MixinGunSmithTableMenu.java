package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.handler.GunSmithTableAutoCraftHandler;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.common.network.ServerMessageManualCraftPower;
import com.ssscript.taczfixes.common.util.GunSmithTableEnergy;
import com.ssscript.taczfixes.common.util.GunSmithTablePosAccess;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GunSmithTableMenu.class)
public abstract class MixinGunSmithTableMenu implements GunSmithTablePosAccess {

    @Unique
    @Nullable
    private BlockPos taczfixes$tablePos;

    @Override
    public void taczfixes$setTablePos(BlockPos pos) {
        this.taczfixes$tablePos = pos;
    }

    @Override
    @Nullable
    public BlockPos taczfixes$getTablePos() {
        return this.taczfixes$tablePos;
    }

    @Inject(method = "doCraft", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$consumeManualEnergy(ResourceLocation recipeId, Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer) || this.taczfixes$tablePos == null) {
            return;
        }
        GunSmithTableEnergy.ManualResult result = GunSmithTableEnergy.consumeManualIfPossible(
                player.level(), this.taczfixes$tablePos, recipeId, player);
        if (result == GunSmithTableEnergy.ManualResult.NO_POWER) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new ServerMessageManualCraftPower(((GunSmithTableMenu) (Object) this).containerId));
            ci.cancel();
        } else if (result == GunSmithTableEnergy.ManualResult.NO_MATERIALS) {
            ci.cancel();
        }
    }

    @Inject(method = "doCraft", at = @At("RETURN"), remap = false)
    private void taczfixes$refreshEnergy(ResourceLocation recipeId, Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            GunSmithTableAutoCraftHandler.query(serverPlayer, ((GunSmithTableMenu) (Object) this).containerId);
        }
    }
}
