package com.example.taczfixes.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

public class ParCoolHelper {
    private static Boolean parcoolLoaded = null;
    private static Method parkourabilityGet = null;
    private static Method getAction = null;
    private static Class<?> crawlClass = null;

    public static boolean isCrawling(LivingEntity entity) {
        if (!isParCoolLoaded()) return false;
        if (!(entity instanceof Player player)) return false;
        return checkCrawl(player);
    }

    private static boolean isParCoolLoaded() {
        if (parcoolLoaded == null) {
            parcoolLoaded = ModList.get().isLoaded("parcool");
        }
        return parcoolLoaded;
    }

    private static boolean checkCrawl(Player player) {
        try {
            if (parkourabilityGet == null) {
                Class<?> parkourabilityClass = Class.forName("com.alrex.parcool.common.capability.Parkourability");
                parkourabilityGet = parkourabilityClass.getMethod("get", Player.class);
                getAction = parkourabilityClass.getMethod("get", Class.class);
                crawlClass = Class.forName("com.alrex.parcool.common.action.impl.Crawl");
            }
            Object parkourability = parkourabilityGet.invoke(null, player);
            if (parkourability == null) return false;
            Object crawl = getAction.invoke(parkourability, crawlClass);
            if (crawl == null) return false;
            Method isDoing = crawl.getClass().getMethod("isDoing");
            return (boolean) isDoing.invoke(crawl);
        } catch (Exception e) {
            return false;
        }
    }
}
