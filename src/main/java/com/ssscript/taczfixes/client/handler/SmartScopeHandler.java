package com.ssscript.taczfixes.client.handler;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Optional;

/** 智能瞄具: 客户端瞄准时自动索敌开枪(纯客户端逻辑, 从旧附魔处理器拆出以保持服务端可注册)。 */
public class SmartScopeHandler {
    @SubscribeEvent
    public void onClientTickSmartScope(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (!GunEnchantmentHelper.isEnabled()) {
            return;
        }
        LocalPlayer player = mc.player;
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (operator == null || !operator.isAim()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        int level = GunEnchantmentHelper.getLevel(stack, TaczFixesMod.SMART_SCOPE_ENCHANTMENT.get());
        if (level <= 0) {
            return;
        }
        if (!(stack.getItem() instanceof IGun)) {
            return;
        }
        double distance = Config.ENCH_SMART_SCOPE_MAX_DISTANCE.get();
        // 只在准星附近有实体时才做射线/实体查询, 避免每 tick 全量查询。
        if (!(mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult)) {
            return;
        }
        Vec3 start = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = start.add(look.scale(distance));
        BlockHitResult blockHit = player.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        double maxDistSqr = blockHit.getType() == HitResult.Type.MISS
                ? distance * distance
                : start.distanceToSqr(blockHit.getLocation());
        if (maxDistSqr <= 0.0) {
            return;
        }
        AABB box = player.getBoundingBox().expandTowards(look.scale(distance)).inflate(1.0);
        List<Entity> entities = player.level().getEntities(player, box,
                e -> e.isAlive() && e.isPickable() && !e.isSpectator());
        double bestDistSqr = maxDistSqr;
        for (Entity e : entities) {
            Optional<Vec3> hit = e.getBoundingBox().clip(start, end);
            if (hit.isEmpty()) {
                continue;
            }
            double d = start.distanceToSqr(hit.get());
            if (d < bestDistSqr) {
                bestDistSqr = d;
            }
        }
        if (bestDistSqr < maxDistSqr) {
            operator.shoot();
        }
    }
}
