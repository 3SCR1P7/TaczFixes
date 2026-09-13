package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.ssscript.taczfixes.common.data.CustomFireModeManager;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModernKineticGunScriptAPI.class)
public class MixinModernKineticGunScriptAPI {

    @Shadow
    private ItemStack itemStack;

    @Shadow
    private LivingEntity shooter;

    @Unique
    public String tfGetCustomAttachment(String slotId) {
        ResourceLocation id = CustomSlotStorage.getAttachmentId(itemStack, slotId);
        return id == null ? "tacz:empty" : id.toString();
    }

    /** lua: gun:getLevel() 获取枪械当前经验等级。 */
    @Unique
    public int getLevel() {
        if (itemStack == null || itemStack.isEmpty()) return 0;
        return itemStack.getOrCreateTag().getInt("GunLevel");
    }

    /** lua: gun:getExp() 获取枪械当前经验值。 */
    @Unique
    public int getExp() {
        if (itemStack == null || itemStack.isEmpty()) return 0;
        return itemStack.getOrCreateTag().getInt("GunLevelExp");
    }

    /** lua: gun:setExp(int) 设置枪械经验值并同步重算等级。 */
    @Unique
    public void setExp(int exp) {
        if (itemStack == null || itemStack.isEmpty()) return;
        if (exp < 0) exp = 0;
        CompoundTag tag = itemStack.getOrCreateTag();
        tag.putInt("GunLevelExp", exp);
        IGun gun = IGun.getIGunOrNull(itemStack);
        if (gun != null) {
            tag.putInt("GunLevel", gun.getLevel(exp));
        }
    }

    /** lua: api:runCommand("cmd ...") 以开火者身份在服务端执行命令, 命令整体为一个字符串, 参数以空格拼接。
     *  如 api:runCommand("kill @s") / api:runCommand("summon minecraft:lightning_bolt -34 68 -121")。
     *  仅服务端执行, 成功返回 true, 未执行或失败返回 false。 */
    @Unique
    public boolean runCommand(String command) {
        if (command == null) return false;
        String s = command.trim();
        if (s.isEmpty()) return false;
        if (shooter == null) return false;
        if (shooter.level() != null && shooter.level().isClientSide) return false;
        MinecraftServer server = shooter.getServer();
        if (server == null) return false;
        CommandSourceStack source = shooter.createCommandSourceStack().withSuppressedOutput().withPermission(4);
        try {
            return server.getCommands().performPrefixedCommand(source, s) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 射击流程内激活当前自定义模式, 使 GunData 的 adjust/burst 查询按该 id 精确取用。
     *  激活/清空由外层 LivingEntityShoot 统一管理; 此处仅为 Lua 直调 shootOnce 兜底。 */
    @Inject(method = "shootOnce(Z)V", at = @At("HEAD"), remap = false)
    private void taczfixes$activateCustomModeHead(boolean needConsumeAmmo, CallbackInfo ci) {
        CustomFireModeManager.activateFor(itemStack);
    }

    /** lua: api:getScoreboardValue("aaaa") 获取开火者玩家在名称为 "aaaa"(或任意名称)的计分板上的值。
     *  计分板不存在或开火者无该值返回 0; 仅服务端生效。 */
    @Unique
    public int getScoreboardValue(String objectiveName) {
        if (objectiveName == null || objectiveName.isEmpty()) return 0;
        if (shooter == null) return 0;
        if (shooter.level() != null && shooter.level().isClientSide) return 0;
        MinecraftServer server = shooter.getServer();
        if (server == null) return 0;
        net.minecraft.world.scores.Objective objective =
                server.getScoreboard().getObjective(objectiveName);
        if (objective == null) return 0;
        net.minecraft.world.scores.Score score =
                server.getScoreboard().getOrCreatePlayerScore(shooter.getScoreboardName(), objective);
        return score == null ? 0 : score.getScore();
    }

    /** lua: api:getPlayerFacing() 开火者面向方向 {偏航, 俯仰} (MC 角度, 俯仰正值朝下)。 */
    @Unique
    public org.luaj.vm2.LuaValue getPlayerFacing() {
        return com.ssscript.taczfixes.common.util.ShooterLuaHelper.facing(shooter);
    }

    /** lua: api:getPlayerTarget() 开火者视线指向的方块坐标 {X, Y, Z}(未命中返回 {0,0,0})。 */
    @Unique
    public org.luaj.vm2.LuaValue getPlayerTarget() {
        return com.ssscript.taczfixes.common.util.ShooterLuaHelper.target(shooter);
    }

    /** lua: api:getPlayerPosition() 开火者当前坐标 {X, Y, Z}。 */
    @Unique
    public org.luaj.vm2.LuaValue getPlayerPosition() {
        return com.ssscript.taczfixes.common.util.ShooterLuaHelper.position(shooter);
    }

    /** lua: api:getInput() 开火者(本地玩家)当前按下的所有按键, 列表如 {"key.mouse.left","key.keyboard.1","key.keyboard.down"}。
     *  仅本地客户端玩家有效, 其他情况返回空表。 */
    @Unique
    public org.luaj.vm2.LuaValue getInput() {
        return com.ssscript.taczfixes.common.util.ShooterLuaHelper.input(shooter);
    }

    /** lua: api:replaceData("tacz:ak47_data") 将此枪械的 data 替换为指定 id 的枪械数据, 成功返回 true。 */
    @Unique
    public boolean replaceData(String dataId) {
        return replaceGunId(dataId, false);
    }

    /** lua: api:replaceDisplay("tacz:minigun_display") 将此枪械的 display 替换为指定 id, 成功返回 true。 */
    @Unique
    public boolean replaceDisplay(String displayId) {
        return replaceGunId(displayId, true);
    }

    @Unique
    private boolean replaceGunId(String idStr, boolean display) {
        if (idStr == null || idStr.isEmpty()) return false;
        if (itemStack == null || itemStack.isEmpty()) return false;
        if (shooter == null) return false;
        if (shooter.level() != null && shooter.level().isClientSide) return false;
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(idStr.trim());
        if (id == null) return false;
        IGun gun = IGun.getIGunOrNull(itemStack);
        if (gun == null) return false;
        if (!display) {
            // gun data 为共通(服务端)资源, 校验存在性; display 是客户端资源, 服务端无索引, 不校验
            com.tacz.guns.resource.ICommonResourceProvider provider = com.tacz.guns.resource.CommonAssetsManager.get();
            if (provider == null || provider.getGunIndex(id) == null) return false;
        }
        net.minecraft.resources.ResourceLocation current = display ? gun.getGunDisplayId(itemStack) : gun.getGunId(itemStack);
        boolean same = id.equals(current);
        if (display) {
            gun.setGunDisplayId(itemStack, id);
        } else {
            gun.setGunId(itemStack, id);
        }
        if (!same && shooter instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            // 直推客户端手持物品同步 NBT, 保证下一帧立即使用新的 data/display 渲染
            com.ssscript.taczfixes.common.network.NetworkHandler.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new com.ssscript.taczfixes.common.network.ClientMessageReplaceGun(id.toString(), display));
            serverPlayer.inventoryMenu.broadcastChanges();
        }
        return true;
    }
}