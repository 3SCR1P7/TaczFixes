package com.ssscript.taczfixes.common.util;

import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaValue;

/**
 * 子弹逐 tick Lua 脚本使用的 api, 绑定当前这颗子弹。
 * 仅在 M.tick_bullet(api) 调用期间作为 api 传入, 其中的函数天然只在该函数中生效。
 */
public final class BulletTickAPI {

    private final EntityKineticBullet bullet;

    private static final java.lang.reflect.Field TICK_COUNT_FIELD = tickCountField();

    private static java.lang.reflect.Field tickCountField() {
        try {
            java.lang.reflect.Field field = net.minecraft.world.entity.Entity.class.getDeclaredField("f_19797_");
            field.setAccessible(true);
            return field;
        } catch (Throwable t) {
            return null;
        }
    }

    private static final java.lang.reflect.Field X_OLD_FIELD = oldField("f_19854_");
    private static final java.lang.reflect.Field Y_OLD_FIELD = oldField("f_19855_");
    private static final java.lang.reflect.Field Z_OLD_FIELD = oldField("f_19856_");

    private static java.lang.reflect.Field oldField(String name) {
        try {
            java.lang.reflect.Field field = net.minecraft.world.entity.Entity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable t) {
            return null;
        }
    }

    public BulletTickAPI(EntityKineticBullet bullet) {
        this.bullet = bullet;
    }

    /** lua: api:getTickSinceSpawn() 返回这颗子弹自生成以来已存在的 tick 数 */
    public int getTickSinceSpawn() {
        if (TICK_COUNT_FIELD == null) return 0;
        try {
            return TICK_COUNT_FIELD.getInt(bullet);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** lua: api:removeThisBullet() 立即移除这颗子弹(丢弃, 不再飞行/造成伤害)。 */
    public void removeThisBullet() {
        bullet.discard();
    }

    /** lua: api:getPlayerFacing() 开火者面向方向 {偏航, 仰角} */
    public LuaValue getPlayerFacing() {
        return ShooterLuaHelper.facing(shooter());
    }

    /** lua: api:getPlayerTarget() 开火者视线指向的方块坐标 {X, Y, Z} */
    public LuaValue getPlayerTarget() {
        return ShooterLuaHelper.target(shooter());
    }

    /** lua: api:getPlayerPosition() 开火者当前坐标 {X, Y, Z} */
    public LuaValue getPlayerPosition() {
        return ShooterLuaHelper.position(shooter());
    }

    /** lua: api:getInput() 开火者(本地玩家)当前按下的所有按键, 列表如 {"key.mouse.left","key.keyboard.1","key.keyboard.down"}。
     *  仅本地客户端玩家有效, 其他情况返回空表。 */
    public LuaValue getInput() {
        return ShooterLuaHelper.input(shooter());
    }

    private net.minecraft.world.entity.LivingEntity shooter() {
        if (bullet.getOwner() instanceof net.minecraft.world.entity.LivingEntity living) {
            return living;
        }
        return bullet.getOwner() instanceof net.minecraft.world.entity.player.Player player ? player : null;
    }

    /** lua: api:getBulletFacing() 返回当前子弹朝向 {X(偏航/度), Y(仰角/度, 正=向上)} */
    public LuaValue getBulletFacing() {
        return LuaValue.listOf(new LuaValue[]{
                LuaValue.valueOf(bullet.getYRot()),
                LuaValue.valueOf(bullet.getXRot())
        });
    }

    /** lua: api:getBulletMotion() 返回当前子弹动量 {X, Y, Z} */
    public LuaValue getBulletMotion() {
        Vec3 motion = bullet.getDeltaMovement();
        return LuaValue.listOf(new LuaValue[]{
                LuaValue.valueOf(motion.x),
                LuaValue.valueOf(motion.y),
                LuaValue.valueOf(motion.z)
        });
    }

    /** lua: api:setBulletMotion(list) 将子弹 X,Y,Z 动量分别设为 list 第 1,2,3 项 */
    public void setBulletMotion(LuaValue list) {
        if (list == null) return;
        double x = list.get(1).todouble();
        double y = list.get(2).todouble();
        double z = list.get(3).todouble();
        bullet.setDeltaMovement(x, y, z);
    }

    /** lua: api:getBulletCoordinates() 返回当前子弹坐标 {X, Y, Z} */
    public LuaValue getBulletCoordinates() {
        return LuaValue.listOf(new LuaValue[]{
                LuaValue.valueOf(bullet.getX()),
                LuaValue.valueOf(bullet.getY()),
                LuaValue.valueOf(bullet.getZ())
        });
    }

    /** lua: api:setBulletCoordinates(list) 将子弹 X,Y,Z 坐标设为 list 第 1,2,3 项。
     * 第二 tick 起生效(首 tick 跳过); 在 1 tick 内平滑过渡: 旧位置写入 xOld/yOld/zOld,
     * 渲染器对该帧做 old→new 插值, 曳光弹平滑移动而非瞬跳。 */
    public void setBulletCoordinates(LuaValue list) {
        if (firstSetSkip()) return;
        if (list == null) return;
        double x = list.get(1).todouble();
        double y = list.get(2).todouble();
        double z = list.get(3).todouble();
        double ox = bullet.getX();
        double oy = bullet.getY();
        double oz = bullet.getZ();
        bullet.setPos(x, y, z);
        try {
            if (X_OLD_FIELD != null) X_OLD_FIELD.setDouble(bullet, ox);
            if (Y_OLD_FIELD != null) Y_OLD_FIELD.setDouble(bullet, oy);
            if (Z_OLD_FIELD != null) Z_OLD_FIELD.setDouble(bullet, oz);
        } catch (Throwable ignored) {
        }
    }

    /** lua: api:setBulletFacing(list) 将子弹 X,Y 朝向分别设为 list 第 1,2 项, 并同步运动速度方向。
     * 与 getBulletFacing/TACZ 运动角体系一致: X 为偏航角(x=sin(yaw), z=cos(yaw)), Y 为仰角(正=向上)。 */
    public void setBulletFacing(LuaValue list) {
        if (firstSetSkip()) return;
        if (list == null) return;
        double yaw = list.get(1).todouble();
        double pitchUp = list.get(2).todouble();
        bullet.setYRot((float) yaw);
        bullet.setXRot((float) pitchUp);
        double speed = bullet.getDeltaMovement().length();
        float f = (float) (pitchUp * Mth.DEG_TO_RAD);
        float f1 = (float) (yaw * Mth.DEG_TO_RAD);
        float cosYaw = Mth.cos(f1);
        float cosPitch = Mth.cos(f);
        float sinYaw = Mth.sin(f1);
        float sinPitch = Mth.sin(f);
        bullet.setDeltaMovement(new Vec3(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch).scale(speed));
    }

    /** 跳过每颗子弹的首次 set(持久数据标记): 首 tick 位移保持原始方向, 第二 tick 起生效。 */
    private boolean firstSetSkip() {
        net.minecraft.nbt.CompoundTag pd = bullet.getPersistentData();
        if (pd.getBoolean("TaczFixesBulletTickFirstSet")) {
            return false;
        }
        pd.putBoolean("TaczFixesBulletTickFirstSet", true);
        return true;
    }

}
