package com.ssscript.taczfixes.client.render;

import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.functional.ShellRender;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.Vector3f;
import org.luaj.vm2.LuaTable;

public final class OffhandGunAnimationContext extends GunAnimationStateContext {
    private final ClientOffhandState state;
    private final GunDisplayInstance display;

    public OffhandGunAnimationContext(ClientOffhandState state, GunDisplayInstance display) {
        this.state = state;
        this.display = display;
    }

    public long getLastShootTimestamp() {
        return this.state.getLastShootTimestamp();
    }

    public void adjustClientShootInterval(long alpha) {
        this.state.adjustShootTimestamp(alpha);
    }

    public long getShootCoolDown() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0L;
        }
        return this.state.getShootCoolDown(player, player.getOffhandItem());
    }

    public long getShootInterval() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0L;
        }
        return this.state.getShootInterval(player, player.getOffhandItem());
    }

    public int getReloadStateType() {
        return this.state.getReloadStateType();
    }

    public float getAimingProgress() {
        return 0.0f;
    }

    public boolean isAiming() {
        return false;
    }

    public float getChargeProgress() {
        return this.state.getChargeProgress();
    }

    public boolean isCharging() {
        return this.state.isCharging();
    }

    public LuaTable getStateMachineParams() {
        LuaTable params = this.display.getStateMachineParam();
        return params == null ? new LuaTable() : params;
    }

    public void popShellFrom(int index) {
        ShellRender lodShell;
        if (this.display.getShellEjection() == null) {
            return;
        }
        BedrockGunModel model = this.display.getGunModel();
        ShellRender shell = model == null ? null : model.getShellRender(index);
        Vector3f velocity = this.display.getShellEjection().getRandomVelocity();
        if (shell != null) {
            shell.addShell(velocity);
        }
        Pair<BedrockGunModel, ResourceLocation> lod = this.display.getLodModel();
        if (lod != null && (lodShell = ((BedrockGunModel) lod.getLeft()).getShellRender(index)) != null) {
            lodShell.addShell(velocity);
        }
    }

    public void updateItem(ItemStack stack, float partialTick) {
        setPartialTicks(partialTick);
        setCurrentGunItem(stack);
    }
}
