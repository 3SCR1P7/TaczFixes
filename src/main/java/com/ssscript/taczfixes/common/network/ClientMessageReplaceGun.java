package com.ssscript.taczfixes.common.network;

import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 服务端 -> 客户端: 立即将持枪的 data / display id 应用到本地物品, 无需换枪即可刷新渲染。 */
public class ClientMessageReplaceGun {

    private final String id;
    private final boolean display;

    public ClientMessageReplaceGun(String id, boolean display) {
        this.id = id;
        this.display = display;
    }

    public static void encode(ClientMessageReplaceGun msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.id == null ? "" : msg.id, 128);
        buf.writeBoolean(msg.display);
    }

    public static ClientMessageReplaceGun decode(FriendlyByteBuf buf) {
        return new ClientMessageReplaceGun(buf.readUtf(128), buf.readBoolean());
    }

    public static void handle(ClientMessageReplaceGun msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return;
            ResourceLocation id = ResourceLocation.tryParse(msg.id);
            if (id == null) return;
            int mainSlot = 36 + player.getInventory().selected;
            int offSlot = 40;
            replaceSlot(player, mainSlot, id, msg.display);
            if (offSlot != mainSlot) {
                replaceSlot(player, offSlot, id, msg.display);
            }
        });
        context.setPacketHandled(true);
    }

    /** 用新堆栈对象替换槽位: isSame(NBT GunId/DisplayId) 变化会被识别为换枪, 触发收枪/举枪与手臂动画重置。 */
    private static void replaceSlot(net.minecraft.client.player.LocalPlayer player, int slotIndex,
                                    ResourceLocation id, boolean display) {
        ItemStack stack = player.getInventory().getItem(slotIndex);
        if (stack == null || stack.isEmpty()) return;
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return;
        ItemStack updated = stack.copy();
        if (display) {
            gun.setGunDisplayId(updated, id);
        } else {
            gun.setGunId(updated, id);
        }
        player.getInventory().setItem(slotIndex, updated);
    }
}
