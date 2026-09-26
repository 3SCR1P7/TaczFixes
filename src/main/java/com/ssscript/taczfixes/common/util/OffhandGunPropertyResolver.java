package com.ssscript.taczfixes.common.util;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.GunProperty;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.event.ChangeGunPropertyEvent;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.custom.RpmModifier;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

public final class OffhandGunPropertyResolver {
    private static final ThreadLocal<Deque<ShooterDataHolder>> ACTIVE_DATA = new ThreadLocal<>();

    private OffhandGunPropertyResolver() {
    }

    public static boolean isActive(ShooterDataHolder dataHolder) {
        Deque<ShooterDataHolder> stack = ACTIVE_DATA.get();
        return (dataHolder == null || stack == null || stack.peek() != dataHolder) ? false : true;
    }

    public static AttachmentCacheProperty rebuild(LivingEntity shooter, ItemStack stack, IGun gun, ShooterDataHolder dataHolder) {
        AttachmentCacheProperty cache = new AttachmentCacheProperty();
        AttachmentPropertyEvent event = new AttachmentPropertyEvent(stack, cache);
        ChangeGunPropertyEvent.internalOnAttachmentPropertyEvent(event);
        dataHolder.cacheProperty = cache;
        push(dataHolder);
        try {
            event.postEventToKubeJS(event);
            MinecraftForge.EVENT_BUS.post(event);
            GunProperties.allCacheModifiableByScript().forEach((id, property) -> {
                applyLuaProperty(shooter, stack, gun, dataHolder, cache, property);
            });
            pop();
            return cache;
        } catch (Throwable th) {
            pop();
            throw th;
        }
    }

    public static long getShootInterval(ItemStack stack, IGun gun, GunData gunData, FireMode fireMode, AttachmentCacheProperty cache) {
        Integer modifiedRoundsPerMinute;
        if (stack == null || stack.isEmpty() || gun == null || gunData == null || fireMode == null) {
            return -1L;
        }
        if (fireMode == FireMode.BURST) {
            if (gunData.getBurstData() == null) {
                return -1L;
            }
            return Math.max((long) (gunData.getBurstData().getMinInterval() * 1000.0d), 0L);
        }
        int roundsPerMinute = gunData.getRoundsPerMinute(fireMode);
        if (cache != null && (modifiedRoundsPerMinute = (Integer) cache.getCache(RpmModifier.ID)) != null) {
            roundsPerMinute = Mth.clamp(modifiedRoundsPerMinute.intValue(), 1, 1200);
        }
        if (gunData.hasHeatData()) {
            roundsPerMinute = Math.max(1, (int) (roundsPerMinute * gun.lerpRPM(stack)));
        }
        return 60000 / Math.max(1, roundsPerMinute);
    }

    public static <T> void applyLuaProperty(LivingEntity shooter, ItemStack stack, IGun gun, ShooterDataHolder dataHolder, AttachmentCacheProperty cache, GunProperty<T> property) {
        Object objModifyProperty = gun.modifyProperty(dataHolder, stack, shooter, "modify_cached_property", property.name(), property.type(), cache.getCache(property));
        if (objModifyProperty != null) {
            cache.setCache(property, (T) objModifyProperty);
        }
    }

    private static void push(ShooterDataHolder dataHolder) {
        Deque<ShooterDataHolder> stack = ACTIVE_DATA.get();
        if (stack == null) {
            stack = new ArrayDeque();
            ACTIVE_DATA.set(stack);
        }
        stack.push(dataHolder);
    }

    private static void pop() {
        Deque<ShooterDataHolder> stack = ACTIVE_DATA.get();
        if (stack != null && !stack.isEmpty()) {
            stack.pop();
        }
        if (stack == null || stack.isEmpty()) {
            ACTIVE_DATA.remove();
        }
    }
}
