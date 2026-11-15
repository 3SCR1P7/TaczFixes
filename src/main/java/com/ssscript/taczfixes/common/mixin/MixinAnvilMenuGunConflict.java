package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import com.tacz.guns.api.item.IGun;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 枪械在铁砧上的附魔兼容:
 * <ul>
 *   <li>允许枪械接受配置白名单内的附魔;</li>
 *   <li>经验等级不足(费用超过枪械等级 + 39)时不允许取下结果。</li>
 * </ul>
 * 附魔最大等级/铁砧费用本身由 {@code data/taczfixes/enchantment/*.json} 驱动, 这里不再参与计算。
 */
@Mixin(AnvilMenu.class)
public class MixinAnvilMenuGunConflict {
    @Unique
    private boolean taczfixes$gunCreative;

    @Inject(method = "createResult", at = @At("HEAD"))
    private void taczfixes$markGunAnvil(CallbackInfo ci) {
        ItemStack target = ((AnvilMenu) (Object) this).getSlot(0).getItem();
        GunEnchantmentHelper.setGunEnchanting(!target.isEmpty() && target.getItem() instanceof IGun);
    }

    @Redirect(method = "createResult",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;supportsEnchantment(Lnet/minecraft/core/Holder;)Z"))
    private static boolean taczfixes$supportsGunEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        if (GunEnchantmentHelper.isEnabled() && stack.getItem() instanceof IGun) {
            return GunEnchantmentHelper.isEnchantAllowed(enchantment.unwrapKey().orElse(null));
        }
        return stack.supportsEnchantment(enchantment);
    }

    @Redirect(method = "createResult",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/world/entity/player/Abilities;instabuild:Z",
                    opcode = Opcodes.GETFIELD))
    private boolean taczfixes$redirectInstabuild(Abilities abilities) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        ItemStack target = menu.getSlot(0).getItem();
        if (!(target.getItem() instanceof IGun)) {
            return abilities.instabuild;
        }
        this.taczfixes$gunCreative = abilities.instabuild;
        if (abilities.instabuild) {
            return true;
        }
        int gunLevel = GunEnchantmentHelper.getGunLevel(target);
        return menu.getCost() <= gunLevel + 39;
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void taczfixes$applyGunAnvilCostLimit(CallbackInfo ci) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        ItemStack target = menu.getSlot(0).getItem();
        ItemStack sacrifice = menu.getSlot(1).getItem();
        ItemStack result = menu.getSlot(2).getItem();
        if (result.isEmpty() || sacrifice.isEmpty() || !(target.getItem() instanceof IGun)) {
            this.taczfixes$gunCreative = false;
            GunEnchantmentHelper.setGunEnchanting(false);
            return;
        }
        if (!this.taczfixes$gunCreative
                && menu.getCost() > GunEnchantmentHelper.getGunLevel(target) + 39) {
            menu.getSlot(2).set(ItemStack.EMPTY);
        }
        this.taczfixes$gunCreative = false;
        GunEnchantmentHelper.setGunEnchanting(false);
    }
}
