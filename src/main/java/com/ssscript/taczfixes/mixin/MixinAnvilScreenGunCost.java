package com.ssscript.taczfixes.mixin;

import com.tacz.guns.api.item.IGun;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AnvilScreen.class)
public class MixinAnvilScreenGunCost {

    @Redirect(method = "renderLabels",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/world/entity/player/Abilities;instabuild:Z",
                    opcode = Opcodes.GETFIELD))
    private boolean taczfixes$redirectInstabuild(Abilities abilities) {
        AnvilScreen screen = (AnvilScreen) (Object) this;
        AnvilMenu menu = screen.getMenu();
        ItemStack target = menu.getSlot(0).getItem();
        if (!(target.getItem() instanceof IGun)) {
            return abilities.instabuild;
        }
        if (abilities.instabuild) {
            return true;
        }
        int gunLevel = target.getOrCreateTag().getInt("GunLevel");
        return menu.getCost() <= gunLevel + 39;
    }
}