package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.tacz.guns.api.item.IGun;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainerMutable;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 奥术铁砧给枪械注入法术时, 创建可施法但不进入法术列表、且容量为配置数量的容器。 */
@Mixin(targets = "io.redspace.ironsspellbooks.gui.arcane_anvil.ArcaneAnvilMenu", remap = false)
public class MixinIronsAnvilContainer {

    @Redirect(method = "m_6640_",
            at = @At(value = "INVOKE",
                    target = "Lio/redspace/ironsspellbooks/api/spells/ISpellContainer;getOrCreate(Lnet/minecraft/world/item/ItemStack;)Lio/redspace/ironsspellbooks/api/spells/ISpellContainer;"),
            remap = false)
    private ISpellContainer taczfixes$gunContainer(ItemStack stack) {
        if (stack == null || stack.isEmpty() || IGun.getIGunOrNull(stack) == null) {
            return ISpellContainer.getOrCreate(stack);
        }
        GunTaczFixesData.ImbuementConfig settings = TaczFixesDataManager.resolveImbuement(stack);
        if (!Boolean.TRUE.equals(settings.enable)) {
            return ISpellContainer.getOrCreate(stack);
        }
        int count = Math.max(1, settings.count == null ? 1 : settings.count);
        ISpellContainer existing = ISpellContainer.get(stack);
        if (existing == null || existing.isEmpty()) {
            return ISpellContainer.create(count, false, true);
        }
        if (existing.getMaxSpellCount() >= count) {
            return existing;
        }
        ISpellContainerMutable grown = existing.mutableCopy();
        grown.setMaxSpellCount(count);
        return grown.toImmutable();
    }
}
