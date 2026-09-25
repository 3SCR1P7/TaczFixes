package com.ssscript.taczfixes.client.handler;

import com.ssscript.taczfixes.client.util.AimingStaminaClientState;
import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.ssscript.taczfixes.client.util.ScopeViewHelper;
import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 客户端: scope 瞄具开镜时的准星晃动(屏息逐渐平息, 低耐力增幅)。 */
public class AimSwayHandler {

    private static float phase;
    private static float breathFactor = 1f;
    private static float prevYaw;
    private static float prevPitch;
    private static float curYaw;
    private static float curPitch;

    /** 是否处于可屏息状态: 开镜中且当前瞄具为 scope 类型。 */
    public static boolean canHoldBreath(LocalPlayer player) {
        if (player == null || !player.isAlive()) return false;
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (operator == null || !operator.isAim()) return false;
        return isScopeActive(player);
    }

    /** 当前安装并激活的瞄具是否为 scope 类型(而非 sight; 组合瞄具按当前视图判定)。 */
    public static boolean isScopeActive(LocalPlayer player) {
        ItemStack gun = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) return false;
        ResourceLocation id = ScopeSwitchState.attachmentId(iGun, gun, AttachmentType.SCOPE);
        if (id == null || DefaultAssets.isEmptyAttachmentId(id)) return false;
        CompoundTag tag = ScopeSwitchState.attachmentTag(iGun, gun, AttachmentType.SCOPE);
        return TimelessAPI.getClientAttachmentIndex(id).map(index -> {
            if (ScopeViewHelper.isCombinedSight(index)) {
                return ScopeViewHelper.isScopeView(index, tag);
            }
            return index.isScope();
        }).orElse(false);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        prevYaw = curYaw;
        prevPitch = curPitch;

        LocalPlayer player = Minecraft.getInstance().player;
        IClientPlayerGunOperator operator = player == null ? null : IClientPlayerGunOperator.fromLocalPlayer(player);
        float aimProgress = operator == null ? 0f
                : Math.max(0f, Math.min(1f, operator.getClientAimingProgress(1.0f)));
        boolean aiming = operator != null && operator.isAim();
        // 只有开镜时才晃动(收镜过程中按开镜进度淡出); 未开镜即使安装了 scope 瞄具也不晃
        if (player == null || !Config.AIMING_STAMINA_ENABLED.get()
                || (!aiming && aimProgress <= 0.01f) || !isScopeActive(player)) {
            curYaw = 0f;
            curPitch = 0f;
            breathFactor = 1f;
            return;
        }

        boolean holding = HoldBreathKeyHandler.HOLD_BREATH_KEY.isDown() && canHoldBreath(player);
        com.ssscript.taczfixes.common.data.GunTaczFixesData.AimingStaminaConfig aimCfg =
                com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager.resolveAimingStamina(player.getMainHandItem());
        int calmMs = aimCfg.sway_hold_breath_calm;
        float rate = calmMs <= 0 ? 1f : 50f / calmMs;
        if (holding) {
            breathFactor = Math.max(0f, breathFactor - rate);
        } else {
            breathFactor = Math.min(1f, breathFactor + rate);
        }

        float threshold = aimCfg.min_stamina_to_aim.floatValue();
        float lowMultiplier = aimCfg.sway_low_stamina_multiplier.floatValue();
        float multiplier = 1f;
        if (threshold > 0f && AimingStaminaClientState.isKnown()) {
            float stamina = AimingStaminaClientState.getStamina();
            if (stamina < threshold) {
                multiplier = 1f + (threshold - Math.max(0f, stamina)) / threshold * (lowMultiplier - 1f);
            }
        }

        // 姿态倍率: 爬行 0.5, 潜行 0.75 (均可配置)
        float stanceMultiplier = 1f;
        if (operator != null && operator.isCrawl()) {
            stanceMultiplier = aimCfg.sway_crawl_multiplier.floatValue();
        } else if (player.isCrouching()) {
            stanceMultiplier = aimCfg.sway_sneak_multiplier.floatValue();
        }

        float baseSpeed = aimCfg.sway_speed.floatValue();
        float speed = baseSpeed * multiplier * stanceMultiplier;
        phase += speed;
        if (phase > 100000f) phase -= 100000f;
        float baseAmplitude = aimCfg.sway_amplitude.floatValue();
        // 屏息把基础晃动降低 100%(breathFactor 0); 低耐力附加的晃动仍保留, 屏息时按倍率放大
        float holdLowMultiplier = aimCfg.sway_hold_breath_low_multiplier.floatValue();
        float extraScale = 1f + (holdLowMultiplier - 1f) * (1f - breathFactor);
        float amplitude = baseAmplitude * ((multiplier - 1f) * extraScale + breathFactor)
                * aimProgress * stanceMultiplier;
        curYaw = (float) Math.sin(phase) * amplitude;
        curPitch = (float) Math.sin(phase * 1.37 + 1.2) * amplitude * 0.6f;
    }

    @SubscribeEvent
    public void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!(event.getCamera().getEntity() instanceof LocalPlayer)) return;
        if (curYaw == 0f && curPitch == 0f && prevYaw == 0f && prevPitch == 0f) return;
        float partial = (float) event.getPartialTick();
        event.setYaw(event.getYaw() + prevYaw + (curYaw - prevYaw) * partial);
        event.setPitch(event.getPitch() + prevPitch + (curPitch - prevPitch) * partial);
    }
}
