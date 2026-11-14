package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.loot.NetheriteBarter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Makes piglins treat netherite ingots like gold ingots: pick them up, admire them, and barter
 * with the mod's own loot table once admiration ends.
 * <ul>
 *     <li>{@code isLovedItem}: netherite ingots also return true, triggering pickup/admire;</li>
 *     <li>{@code canAdmire}: vanilla only checks {@code ItemStack.isPiglinCurrency()}; the redirect
 *     lets netherite ingots be admired as well;</li>
 *     <li>{@code stopHoldingOffHandItem}: for an adult piglin holding a netherite ingot with
 *     {@code shouldBarter = true}, vanilla would put the ingot into its inventory because it is not
 *     piglin currency. The HEAD inject clears the offhand, throws items from the custom barter
 *     table and cancels the vanilla method. With {@code shouldBarter = false} vanilla behavior is
 *     kept unchanged.</li>
 * </ul>
 *
 * <p>All three hooks are gated by the compat option {@link Config#PIGLIN_BARTER_ENABLED}; when it is
 * disabled, vanilla piglin behavior is fully restored (netherite is neither loved, admired nor
 * bartered).</p>
 */
@Mixin(PiglinAi.class)
public abstract class MixinPiglinAiNetheriteBarter {
    @Inject(method = "isLovedItem", at = @At("HEAD"), cancellable = true)
    private static void taczfixes$treatNetheriteIngotAsLoved(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!Config.PIGLIN_BARTER_ENABLED.get()) {
            return;
        }
        if (stack.is(Items.NETHERITE_INGOT)) {
            cir.setReturnValue(true);
        }
    }

    @Redirect(method = "canAdmire",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;isPiglinCurrency()Z",
                    remap = false))
    private static boolean taczfixes$admireNetheriteIngot(ItemStack stack) {
        boolean original = stack.isPiglinCurrency();
        if (!Config.PIGLIN_BARTER_ENABLED.get()) {
            return original;
        }
        return original || stack.is(Items.NETHERITE_INGOT);
    }

    @Inject(method = "stopHoldingOffHandItem", at = @At("HEAD"), cancellable = true)
    private static void taczfixes$barterWithNetheriteIngot(Piglin piglin, boolean shouldBarter, CallbackInfo ci) {
        if (!Config.PIGLIN_BARTER_ENABLED.get()) {
            return;
        }
        if (!shouldBarter || !piglin.isAdult()) {
            return;
        }
        if (!piglin.getOffhandItem().is(Items.NETHERITE_INGOT)) {
            return;
        }
        piglin.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        NetheriteBarter.barterAndThrow(piglin);
        ci.cancel();
    }

    /** 原版欣赏行为可步行到物品的最大距离(GoToWantedItem/StopAdmiringIfItemTooFarAway 均为 9)。 */
    private static final double TACZFIXES_ADMIRE_WALK_RANGE = 9.0D;

    /**
     * 防止"远处(9 格外)仍可见的下界合金锭"让猪灵非核心活动每 tick 在 ADMIRE_ITEM / IDLE 之间翻转,
     * 从而每 tick 播放一次叫声。这里在活动选取前清掉够不到物品的 ADMIRING_ITEM, 使活动保持稳定;
     * 物品进入 9 格内后欣赏/捡取仍照原版逻辑执行。
     */
    @Inject(method = "updateActivity", at = @At("HEAD"))
    private static void taczfixes$stopFarNetheriteAdmireChurn(Piglin piglin, CallbackInfo ci) {
        if (!Config.PIGLIN_BARTER_ENABLED.get()) {
            return;
        }
        if (!piglin.getOffhandItem().isEmpty()) {
            return;
        }
        Brain<Piglin> brain = piglin.getBrain();
        Optional<ItemEntity> wanted = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
        if (wanted.isEmpty()) {
            return;
        }
        ItemEntity itemEntity = wanted.get();
        if (!itemEntity.getItem().is(Items.NETHERITE_INGOT)) {
            return;
        }
        if (itemEntity.closerThan(piglin, TACZFIXES_ADMIRE_WALK_RANGE)) {
            return;
        }
        brain.eraseMemory(MemoryModuleType.ADMIRING_ITEM);
        brain.eraseMemory(MemoryModuleType.TIME_TRYING_TO_REACH_ADMIRE_ITEM);
    }
}
