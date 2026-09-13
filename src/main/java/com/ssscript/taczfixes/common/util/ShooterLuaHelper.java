package com.ssscript.taczfixes.common.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.luaj.vm2.LuaValue;

/** 玩家(开火者)信息的 Lua 函数公共实现: tick_bullet 与其他枪械脚本函数共用。 */
public final class ShooterLuaHelper {

    private ShooterLuaHelper() {
    }

    /** lua: getPlayerFacing() 玩家面向方向 {偏航, 仰角}, 与子弹 get/setBulletFacing 运动角体系一致
     * (玩家视觉 yaw 与运动角差负号, 此处取负统一)。 */
    public static LuaValue facing(LivingEntity shooter) {
        if (shooter == null) {
            return LuaValue.listOf(new LuaValue[]{LuaValue.valueOf(0), LuaValue.valueOf(0)});
        }
        return LuaValue.listOf(new LuaValue[]{
                LuaValue.valueOf(-shooter.getYRot()),
                LuaValue.valueOf(-shooter.getXRot())
        });
    }

    /** lua: getPlayerTarget() 玩家视线指向的方块坐标 {X, Y, Z}(未命中返回 {0,0,0})。 */
    public static LuaValue target(LivingEntity shooter) {
        if (shooter == null || shooter.level() == null) {
            return LuaValue.listOf(new LuaValue[]{LuaValue.valueOf(0), LuaValue.valueOf(0), LuaValue.valueOf(0)});
        }
        float distance;
        net.minecraft.world.level.Level level = shooter.level();
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && serverLevel.getServer() != null) {
            distance = serverLevel.getServer().getPlayerList().getViewDistance() * 16.0F;
        } else {
            distance = 512.0F;
            try {
                int renderChunks = net.minecraft.client.Minecraft.getInstance().options.renderDistance().get();
                distance = Math.max(1, renderChunks) * 16.0F;
            } catch (Throwable ignored) {
            }
        }
        HitResult hit = shooter.pick(distance, 1.0F / 20.0F, false);
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            net.minecraft.core.BlockPos pos = blockHit.getBlockPos();
            return LuaValue.listOf(new LuaValue[]{
                    LuaValue.valueOf(pos.getX()),
                    LuaValue.valueOf(pos.getY()),
                    LuaValue.valueOf(pos.getZ())
            });
        }
        return LuaValue.listOf(new LuaValue[]{LuaValue.valueOf(0), LuaValue.valueOf(0), LuaValue.valueOf(0)});
    }

    /** lua: getPlayerPosition() 玩家当前坐标 {X, Y, Z}。 */
    public static LuaValue position(LivingEntity shooter) {
        if (shooter == null) {
            return LuaValue.listOf(new LuaValue[]{LuaValue.valueOf(0), LuaValue.valueOf(0), LuaValue.valueOf(0)});
        }
        return LuaValue.listOf(new LuaValue[]{
                LuaValue.valueOf(shooter.getX()),
                LuaValue.valueOf(shooter.getY()),
                LuaValue.valueOf(shooter.getZ())
        });
    }

    /** lua: getInput() 开火者当前按下的所有按键, 返回列表如
     *  {"key.mouse.left","key.keyboard.1","key.keyboard.down"}。
     *  本地客户端玩家实时读取按键; 服务端及其他玩家使用客户端的同步状态, 未同步过返回空表 {}。 */
    public static LuaValue input(LivingEntity shooter) {
        if (shooter == null) {
            return LuaValue.listOf(new LuaValue[0]);
        }
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == shooter) {
                return toLuaList(pollPressedKeys());
            }
        } catch (Throwable ignored) {
        }
        java.util.Set<String> synced = PlayerInputState.get(shooter.getUUID());
        if (synced == null || synced.isEmpty()) {
            return LuaValue.listOf(new LuaValue[0]);
        }
        LuaValue[] arr = new LuaValue[synced.size()];
        int i = 0;
        for (String key : synced) {
            arr[i++] = LuaValue.valueOf(key);
        }
        return LuaValue.listOf(arr);
    }

    /** 实时枚举本地玩家当前按下的按键(含前缀), 失败(如服务端/无窗口)返回 null。 */
    public static java.util.List<String> pollPressedKeys() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            long handle = mc.getWindow().getWindow();
            java.util.ArrayList<String> list = new java.util.ArrayList<>(16);
            for (int button = 0; button < 8; button++) {
                if (org.lwjgl.glfw.GLFW.glfwGetMouseButton(handle, button) != org.lwjgl.glfw.GLFW.GLFW_PRESS) {
                    continue;
                }
                list.add("key.mouse." + mouseName(button));
            }
            String[] names = keyNames();
            for (int key = 32; key <= 348; key++) {
                if (org.lwjgl.glfw.GLFW.glfwGetKey(handle, key) != org.lwjgl.glfw.GLFW.GLFW_PRESS) {
                    continue;
                }
                String name = names[key];
                if (name == null) {
                    continue;
                }
                list.add("key.keyboard." + name);
            }
            return list;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static LuaValue toLuaList(java.util.List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return LuaValue.listOf(new LuaValue[0]);
        }
        LuaValue[] arr = new LuaValue[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            arr[i] = LuaValue.valueOf(keys.get(i));
        }
        return LuaValue.listOf(arr);
    }

    /** 按键 0-7 依次为 左键/右键/中键/侧键1-2/侧键3-5(按 GLFW 线序编号)。 */
    private static String mouseName(int button) {
        return switch (button) {
            case 0 -> "left";
            case 1 -> "right";
            case 2 -> "middle";
            default -> String.valueOf(button + 1);
        };
    }

    /** 惰性构建 GLFW 键位名表(纯字面量, 不含 LWJGL/客户端类常量, 服务端加载此类安全)。 */
    private static volatile String[] KEY_NAMES;

    private static String[] keyNames() {
        String[] names = KEY_NAMES;
        if (names == null) {
            names = new String[512];
            for (int i = 0; i < 10; i++) {
                names[48 + i] = String.valueOf((char) (48 + i));
            }
            for (int i = 0; i < 26; i++) {
                names[65 + i] = String.valueOf((char) (97 + i));
            }
            names[32] = "space";
            names[39] = "apostrophe";
            names[44] = "comma";
            names[45] = "minus";
            names[46] = "period";
            names[47] = "slash";
            names[59] = "semicolon";
            names[61] = "equal";
            names[91] = "left.bracket";
            names[92] = "backslash";
            names[93] = "right.bracket";
            names[96] = "grave.accent";
            names[256] = "escape";
            names[257] = "enter";
            names[258] = "tab";
            names[259] = "backspace";
            names[260] = "insert";
            names[261] = "delete";
            names[262] = "right";
            names[263] = "left";
            names[264] = "down";
            names[265] = "up";
            names[266] = "page.up";
            names[267] = "page.down";
            names[268] = "home";
            names[269] = "end";
            names[270] = "caps.lock";
            names[271] = "scroll.lock";
            names[272] = "num.lock";
            names[273] = "print.screen";
            names[274] = "pause";
            for (int i = 0; i < 12; i++) {
                names[290 + i] = "f" + (i + 1);
            }
            for (int i = 0; i < 10; i++) {
                names[320 + i] = "keypad." + i;
            }
            names[330] = "keypad.decimal";
            names[331] = "keypad.divide";
            names[332] = "keypad.multiply";
            names[333] = "keypad.subtract";
            names[334] = "keypad.add";
            names[335] = "keypad.enter";
            names[336] = "keypad.equal";
            names[340] = "left.shift";
            names[341] = "left.control";
            names[342] = "left.alt";
            names[343] = "left.super";
            names[344] = "right.shift";
            names[345] = "right.control";
            names[346] = "right.alt";
            names[347] = "right.super";
            names[348] = "menu";
            KEY_NAMES = names;
        }
        return names;
    }
}
