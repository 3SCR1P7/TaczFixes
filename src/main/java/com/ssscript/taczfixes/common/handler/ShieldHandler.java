package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.util.GunShieldHelper;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.GunDamageSourcePart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ShieldHandler {
    private static final Logger LOGGER = LogManager.getLogger("taczfixes");

    /** 子弹格挡: gsm(High)取消事件后，按 resistance 修整格挡量计数，并结算泄漏伤害。 */
    @SubscribeEvent
    public void onBulletBlock(EntityHurtByGunEvent.Pre event) {
        if (!event.isCanceled()) return;
        if (!(event.getHurtEntity() instanceof LivingEntity victim)) return;
        if (victim.level().isClientSide()) return;
        ItemStack weapon = victim.getMainHandItem();
        GunTaczFixesData.ShieldConfig cfg = GunShieldHelper.resolveShieldConfig(weapon);
        if (cfg == null) {
            LOGGER.info("taczfixes[debug]: bullet blocked, but no shield cfg for weapon {}", weapon);
            return;
        }
        float amount = event.getBaseAmount();
        LOGGER.info("taczfixes[debug]: bullet blocked weapon={} amount={} resistance={}",
                weapon, amount, cfg.resistance);
        GunShieldHelper.onBlocked(victim, weapon, cfg, amount * cfg.resistance);
        applyLeak(victim, event.getDamageSource(GunDamageSourcePart.NON_ARMOR_PIERCING), amount, cfg.resistance);
        playBlockSound(victim, cfg.resistance);
    }

    /** 原版伤害格挡: 通过 setBlockedDamage 按 resistance 修整格挡量，剩余部分由原版流程继续结算。 */
    @SubscribeEvent
    public void onShieldBlock(ShieldBlockEvent event) {
        LivingEntity user = event.getEntity();
        if (user.level().isClientSide()) return;
        ItemStack weapon = user.getMainHandItem();
        GunTaczFixesData.ShieldConfig cfg = GunShieldHelper.resolveShieldConfig(weapon);
        if (cfg == null) return;
        float amount = event.getBlockedDamage();
        double blocked = amount * cfg.resistance;
        event.setBlockedDamage((float) blocked);
        GunShieldHelper.onBlocked(user, weapon, cfg, blocked);
        playBlockSound(user, cfg.resistance);
    }

    /** resistance 在 (0,1) 时原生播声点可能不发声, 主动补播一次格挡音效(1.0 时保持原生行为不重复)。 */
    private static void playBlockSound(LivingEntity user, double resistance) {
        if (resistance <= 0.0 || resistance >= 1.0) return;
        user.level().playSound(null, user, net.minecraft.sounds.SoundEvents.SHIELD_BLOCK,
                user.getSoundSource(), 1.0F, 1.0F);
    }

    private static void applyLeak(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source,
                                  float amount, double resistance) {
        float leak = (float) (amount * (1.0 - resistance));
        if (leak <= 0) return;
        GunShieldHelper.setSuppressed(victim, true);
        try {
            victim.hurt(source, leak);
        } finally {
            GunShieldHelper.setSuppressed(victim, false);
        }
    }
}
