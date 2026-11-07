package com.ssscript.taczfixes.client.client;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.util.time.GunTime;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * F 键双手互换的显式动画调度器 (双持专用)。
 *
 * <p>完全沿用 TaCZ 自己的换枪动画模型:
 * <ul>
 *   <li>离开某只手的枪: {@code context.setPutAwayTime(...) -> trigger("put_away") -> exit() ->
 *       setExitingTime(duration + 50)}, 在 exiting 时间内继续渲染旧枪 (TaCZ
 *       {@code GunItemRendererWrapper.tryExit} / {@code AnimateGeoItemRenderer} 的语义);</li>
 *   <li>进入某只手的枪: 过渡结束后 {@code tryInit} / {@code trigger("draw")}, 只播一次掏枪。</li>
 * </ul>
 *
 * <p>主手侧由 TaCZ + SimpleBedrockModel 的 {@code FirstPersonRenderHandler} 自行处理 put_away/draw,
 * 这里只额外处理一种情况: 主手原本是物品 (没有枪需要收枪), 而换进来的主手是枪时, TaCZ 会在换手当帧
 * 直接开始 draw。为了让顺序变成 "副手旧枪 put_away -> 主手新枪 draw", 该主手枪的渲染会被抑制,
 * 由本调度器在过渡结束时补一次 draw。
 */
@OnlyIn(Dist.CLIENT)
final class OffhandHandSwap {
    private static boolean active;
    private static long endTimestamp = -1L;
    private static long epoch = -1L;
    private static ItemStack leavingOffhandGun = ItemStack.EMPTY;
    private static ItemStack enteringMainGun = ItemStack.EMPTY;
    private static boolean holdEnteringMainDraw;

    private OffhandHandSwap() {
    }

    static boolean isActive() {
        return active;
    }

    /** 过渡期间副手只允许渲染离开副手的旧枪, 新副手物品一律不渲染, 避免瞬现/闪烁。 */
    static ItemStack getOffhandRenderStack() {
        return active ? leavingOffhandGun : ItemStack.EMPTY;
    }

    /** 主手进入的枪是否需要被暂时隐藏, 等过渡结束后再播 draw。 */
    static boolean shouldSuppressMainRender(ItemStack stack) {
        return active && holdEnteringMainDraw && sameGunKind(stack, enteringMainGun);
    }

    /** 主手 draw 音效是否也要跟随被压住的 draw 一起延后。 */
    static boolean isHoldingMainDrawSound() {
        return active && holdEnteringMainDraw;
    }

    static boolean begin(LocalPlayer player, ItemStack previousMainStack, ItemStack previousOffhandStack,
                         ItemStack nextMainStack, ItemStack nextOffhandStack) {
        if (active) {
            return true;
        }
        ItemStack leavingMain = asGun(previousMainStack);
        ItemStack leavingOffhand = asGun(previousOffhandStack);
        ItemStack enteringMain = asGun(nextMainStack);
        long mainPutAwayMillis = OffhandDisplayManager.getGunPutAwayTimeMillis(leavingMain);
        long offhandPutAwayMillis = OffhandDisplayManager.getGunPutAwayTimeMillis(leavingOffhand);
        long duration = Math.max(mainPutAwayMillis, offhandPutAwayMillis);
        if (duration <= 0L) {
            // 没有可见的收枪动作: 直接按普通换枪结束, 只补 enter 侧 draw。
            OffhandDisplayManager.finishHandSwapOffhand(player, nextOffhandStack);
            OffhandDisplayManager.finishHandSwapMain(player, nextMainStack);
            return false;
        }
        active = true;
        epoch = GunTime.epoch();
        long now = GunTime.nowMillis();
        // 与 TaCZ tryExit 的 exiting 语义一致: 收枪片段结束后再保留 50ms 才切到掏枪,
        // 避免主手/副手两侧因 tick 边界差一帧而同时出现两把枪。
        long keepMillis = duration + 50L;
        endTimestamp = now > Long.MAX_VALUE - keepMillis ? Long.MAX_VALUE : now + keepMillis;
        leavingOffhandGun = copyOrEmpty(leavingOffhand);
        enteringMainGun = copyOrEmpty(enteringMain);
        // 主手没有旧枪可收, 但换进来的主手是枪: TaCZ 会立刻 draw, 需要压住到副手收枪结束。
        holdEnteringMainDraw = leavingMain.isEmpty() && !enteringMain.isEmpty();
        DualMuzzleFlashState.clearMuzzlePositions();
        OffhandDisplayManager.startHandSwapOffhandPutAway(player, leavingOffhand, offhandPutAwayMillis, duration);
        return true;
    }

    static void tick(LocalPlayer player) {
        if (!active) {
            return;
        }
        // GunTime epoch 变化 = 客户端动画时钟被重置 (进世界/重生等), 旧时间戳不再有效。
        if (GunTime.epoch() != epoch || GunTime.nowMillis() >= endTimestamp) {
            finish(player);
        }
    }

    private static void finish(LocalPlayer player) {
        ItemStack nextMain = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        ItemStack nextOffhand = player == null ? ItemStack.EMPTY : player.getOffhandItem();
        boolean releaseMainDraw = holdEnteringMainDraw;
        active = false;
        endTimestamp = -1L;
        epoch = -1L;
        leavingOffhandGun = ItemStack.EMPTY;
        enteringMainGun = ItemStack.EMPTY;
        holdEnteringMainDraw = false;
        OffhandDisplayManager.finishHandSwapOffhand(player, nextOffhand);
        if (releaseMainDraw) {
            OffhandDisplayManager.finishHandSwapMain(player, nextMain);
        }
    }

    static void reset() {
        active = false;
        endTimestamp = -1L;
        epoch = -1L;
        leavingOffhandGun = ItemStack.EMPTY;
        enteringMainGun = ItemStack.EMPTY;
        holdEnteringMainDraw = false;
    }

    private static ItemStack asGun(ItemStack stack) {
        return stack != null && !stack.isEmpty() && IGun.getIGunOrNull(stack) != null ? stack : ItemStack.EMPTY;
    }

    private static ItemStack copyOrEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    private static boolean sameGunKind(ItemStack first, ItemStack second) {
        if (first == null || second == null || first.isEmpty() || second.isEmpty()) {
            return false;
        }
        if (first == second) {
            return true;
        }
        IGun firstGun = IGun.getIGunOrNull(first);
        IGun secondGun = IGun.getIGunOrNull(second);
        return firstGun != null && secondGun != null && first.getItem() == second.getItem()
                && firstGun.getGunId(first).equals(secondGun.getGunId(second));
    }
}
