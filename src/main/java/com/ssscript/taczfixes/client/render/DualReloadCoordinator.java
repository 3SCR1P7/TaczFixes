package com.ssscript.taczfixes.client.render;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.client.input.ReloadKey;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.config.client.KeyConfig;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import com.ssscript.taczfixes.TaczFixesMod;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.runtime.ObjectMethods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.item.Item;

@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = {Dist.CLIENT})
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/render/DualReloadCoordinator.class */
public final class DualReloadCoordinator {
    private static long acceptedMainReloadSequence;

    private DualReloadCoordinator() {
    }

    public static boolean interceptKeyboardReload(InputEvent.Key event) {
        if (event.getAction() != 1 || !ReloadKey.RELOAD_KEY.matches(event.getKey(), event.getScanCode())) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.screen != null || !DualWieldClient.isDualMode(player)) {
            return false;
        }
        if (OffhandDisplayManager.isPuttingAway()) {
            return true;
        }
        return handleManualReload(player);
    }

    public static boolean interceptControllerReload(boolean isPress) {
        if (!isPress) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.screen != null || !DualWieldClient.isDualMode(player)) {
            return false;
        }
        if (OffhandDisplayManager.isPuttingAway()) {
            return true;
        }
        return handleManualReload(player);
    }

    public static boolean shouldSuppressNativeAutoReload() {
        LocalPlayer player = Minecraft.getInstance().player;
        return DualWieldClient.isDualMode(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (DualWieldClient.isDualMode(player) && !OffhandDisplayManager.isPuttingAway() && minecraft.screen == null && player.tickCount % 5 == 0 && KeyConfig.AUTO_RELOAD != null && Boolean.TRUE.equals(KeyConfig.AUTO_RELOAD.get())) {
            handleAutoReload(player);
        }
    }

    private static boolean handleManualReload(LocalPlayer player) {
        InteractionHand interactionHand;
        boolean mainBusy = isMainReloading(player);
        boolean offhandBusy = isOffhandReloading();
        if (mainBusy || offhandBusy) {
            if (mainBusy != offhandBusy) {
                if (mainBusy) {
                    interactionHand = InteractionHand.OFF_HAND;
                } else {
                    interactionHand = InteractionHand.MAIN_HAND;
                }
                InteractionHand otherHand = interactionHand;
                return routeManualCandidate(player, inspect(player, otherHand));
            }
            return true;
        }
        ReloadCandidate main = inspect(player, InteractionHand.MAIN_HAND);
        ReloadCandidate offhand = inspect(player, InteractionHand.OFF_HAND);
        if (main == null || offhand == null) {
            return false;
        }
        if (main.loadedAmmo() == offhand.loadedAmmo()) {
            startBoth(player, main, offhand);
            return true;
        }
        if (main.empty()) {
            return routeManualCandidate(player, main.canRequest() ? main : offhand);
        }
        if (offhand.empty()) {
            return routeManualCandidate(player, offhand.canRequest() ? offhand : main);
        }
        ReloadCandidate selected = selectLowerFill(main, offhand);
        return routeManualCandidate(player, selected);
    }

    private static boolean routeManualCandidate(LocalPlayer player, ReloadCandidate candidate) {
        if (candidate == null) {
            return true;
        }
        startCandidate(player, candidate);
        return true;
    }

    private static void handleAutoReload(LocalPlayer player) {
        boolean mainReloading = isMainReloading(player);
        boolean offhandReloading = isOffhandReloading();
        ReloadCandidate main = inspect(player, InteractionHand.MAIN_HAND);
        ReloadCandidate offhand = inspect(player, InteractionHand.OFF_HAND);
        if (main == null || offhand == null) {
            return;
        }
        if (mainReloading || offhandReloading) {
            if (mainReloading != offhandReloading) {
                ReloadCandidate other = mainReloading ? offhand : main;
                if (other.empty()) {
                    startCandidate(player, other);
                    return;
                }
                return;
            }
            return;
        }
        if (main.empty() && offhand.empty()) {
            startBoth(player, main, offhand);
        } else if (main.empty()) {
            startCandidate(player, main);
        } else if (offhand.empty()) {
            startCandidate(player, offhand);
        }
    }

    private static boolean startBoth(LocalPlayer player, ReloadCandidate main, ReloadCandidate offhand) {
        boolean offhandStarted = startCandidate(player, offhand);
        boolean mainStarted = startCandidate(player, main);
        return offhandStarted || mainStarted;
    }

    private static ReloadCandidate selectLowerFill(ReloadCandidate main, ReloadCandidate offhand) {
        if (!main.canRequest() && offhand.canRequest()) {
            return offhand;
        }
        if (offhand.canRequest() || !main.canRequest()) {
            return offhand.fillRatio() < main.fillRatio() ? offhand : main;
        }
        return main;
    }

    private static boolean startCandidate(LocalPlayer player, ReloadCandidate candidate) {
        if (candidate == null || !candidate.canRequest()) {
            return false;
        }
        if (candidate.hand() == InteractionHand.OFF_HAND) {
            return DualWieldClient.requestOffhandReload(player);
        }
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        LocalPlayerDataHolder data = operator.getDataHolder();
        if (isMainReloading(player) || data.clientStateLock || System.currentTimeMillis() - data.clientShootTimestamp < 100) {
            return false;
        }
        long sequenceBeforeRequest = acceptedMainReloadSequence;
        operator.reload();
        if (acceptedMainReloadSequence != sequenceBeforeRequest) {
            return true;
        }
        return false;
    }

    public static void markMainReloadAccepted() {
        acceptedMainReloadSequence++;
        DualWieldClient.cancelFocusAimForAction(Minecraft.getInstance().player);
    }

    private static boolean isMainReloading(LocalPlayer player) {
        IGunOperator operator = IGunOperator.fromLivingEntity(player);
        if (operator.getSynReloadState().getStateType().isReloading()) {
            return true;
        }
        LocalPlayerDataHolder data = IClientPlayerGunOperator.fromLocalPlayer(player).getDataHolder();
        return data.clientStateLock && DualReloadAnimationManager.isHandActive(InteractionHand.MAIN_HAND);
    }

    private static boolean isOffhandReloading() {
        return OffhandDisplayManager.getClientState().isReloading();
    }

    private static ReloadCandidate inspect(LocalPlayer player, InteractionHand hand) {
        double d;
        ItemStack stack = player.getItemInHand(hand);
        Item abstractGunItemM_41720_ = stack.getItem();
        if (!(abstractGunItemM_41720_ instanceof AbstractGunItem)) {
            return null;
        }
        AbstractGunItem gun = (AbstractGunItem) abstractGunItemM_41720_;
        if (gun.useInventoryAmmo(stack)) {
            return null;
        }
        ClientGunIndex index = (ClientGunIndex) TimelessAPI.getClientGunIndex(gun.getGunId(stack)).orElse(null);
        GunData gunData = index == null ? null : index.getGunData();
        if (gunData == null) {
            return null;
        }
        int magazineCapacity = Math.max(AttachmentDataUtils.getAmmoCountWithAttachment(stack, gunData), 0);
        boolean chamberSupported = gunData.getBolt() != Bolt.OPEN_BOLT;
        int chamberAmmo = (chamberSupported && gun.hasBulletInBarrel(stack)) ? 1 : 0;
        int magazineAmmo = Math.max(gun.getCurrentAmmoCount(stack), 0);
        int loadedAmmo = magazineAmmo + chamberAmmo;
        if (magazineCapacity <= 0) {
            d = 1.0d;
        } else {
            d = magazineAmmo / magazineCapacity;
        }
        double fillRatio = d;
        boolean magazineNeedsAmmo = magazineAmmo < magazineCapacity;
        boolean hasReloadSupply = !IGunOperator.fromLivingEntity(player).needCheckAmmo() || gun.canReload(player, stack);
        boolean canRequest = magazineCapacity > 0 && magazineNeedsAmmo && hasReloadSupply;
        return new ReloadCandidate(hand, loadedAmmo, loadedAmmo <= 0, fillRatio, canRequest);
    }

    /* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/render/DualReloadCoordinator$ReloadCandidate.class */
    private record ReloadCandidate(InteractionHand hand, int loadedAmmo, boolean empty, double fillRatio, boolean canRequest) {

        private ReloadCandidate(InteractionHand hand, int loadedAmmo, boolean empty, double fillRatio, boolean canRequest) {
            this.hand = hand;
            this.loadedAmmo = loadedAmmo;
            this.empty = empty;
            this.fillRatio = fillRatio;
            this.canRequest = canRequest;
        }

        public InteractionHand hand() {
            return this.hand;
        }

        public int loadedAmmo() {
            return this.loadedAmmo;
        }

        public boolean empty() {
            return this.empty;
        }

        public double fillRatio() {
            return this.fillRatio;
        }

        public boolean canRequest() {
            return this.canRequest;
        }
    }
}
