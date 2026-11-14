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
 * 让猪灵把下界合金锭视为金锭: 会捡取、欣赏, 并在欣赏结束后用本模组的战利品表以物易物。
 * <ul>
 *     <li>{@code isLovedItem}: 下界合金锭也返回 true -> 触发捡取与欣赏行为。</li>
 *     <li>{@code canAdmire}: 原版只看 {@code ItemStack.isPiglinCurrency()}, 这里重定向该调用,
 *     让下界合金锭也能被玩家手持右键欣赏。</li>
 *     <li>{@code stopHoldingOffHandItem}: 成人猪灵副手是下界合金锭且应当以物易物时, 原版会因
 *     "不是猪灵货币"把锭塞回背包。这里在 HEAD 处拦截, 清空副手, 用本模组的战利品表掷骰并丢出,
 *     然后取消原方法。{@code shouldBarter = false}(例如被打断)时保持原版行为。</li>
 *     <li>{@code updateActivity}: 修复远处下界合金锭导致的叫声刷屏。原版 {@code StopAdmiringIfItemTooFarAway}
 *     会对 9 格之外但仍可见的喜爱物品每 tick 清除 {@code ADMIRING_ITEM}, 而核心行为
 *     {@code StartAdmiringItemIfSeen} 又每 tick 重新设置, 导致非核心活动在 ADMIRE_ITEM 与 IDLE 之间
 *     每 tick 翻转一次; {@code updateActivity} 在活动变化时会播放猪灵叫声, 于是叫声以每 tick 一次
 *     的频率无限重复。这里在活动计算前清掉"够不到的下界合金锭"的 {@code ADMIRING_ITEM},
 *     让活动保持稳定, 不再反复切换与播放叫声。</li>
 * </ul>
 *
 * <p>所有注入点都受兼容选项 {@link Config#PIGLIN_BARTER_ENABLED} 控制; 关闭时完全走原版逻辑
 * (下界合金锭不再被喜爱 / 欣赏 / 交易)。</p>
 */
@Mixin(PiglinAi.class)
public abstract class MixinPiglinAiNetheriteBarter {
    /** 原版欣赏行为可步行到物品的最大距离(GoToWantedItem/StopAdmiringIfItemTooFarAway 均为 9)。 */
    private static final double TACZFIXES_ADMIRE_WALK_RANGE = 9.0D;

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

    /**
     * 防止"远处(9 格外)仍可见的下界合金锭"让猪灵非核心活动每 tick 在 ADMIRE_ITEM / IDLE 之间翻转,
     * 从而每 tick 播放一次叫声。这里在活动选取前清掉够不到物品的 ADMIRING_ITEM,
     * 使活动保持稳定; 物品进入 9 格内后欣赏/捡取仍照原版逻辑执行。
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
