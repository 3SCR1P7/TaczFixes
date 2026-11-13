-- 配件动画状态机示例
-- 放置路径：assets/<namespace>/scripts/<name>.lua
-- display JSON 中对应写法："taczfixes": { "state_machine": "<namespace>:<name>", ... }
--
-- 说明：
-- * 每个配件槽位/视角都会创建独立的脚本实例，这里的模块级变量互不共享。
-- * 动画名（idle / aim / shoot / reload）必须与配件动画文件中的 animations 键一致。
-- * 骨骼名（示例动画里的 grip）必须替换成你自己 geo 模型里的骨骼名。

local track_line_top = {value = 0}

local function increment(obj)
    obj.value = obj.value + 1
    return obj.value - 1
end

-- 主轨道行
local STATIC_TRACK_LINE = increment(track_line_top)
-- 基础状态（idle / aim 循环）与动作（shoot / reload）放在同一条轨道行的两条轨道上
local BASE_TRACK = 0
local MAIN_TRACK = 1

local M = {
    -- 对外输入
    INPUT_IDLE = "idle",
    INPUT_AIM = "aim",
    INPUT_AIM_STOP = "aim_stop",
    INPUT_SHOOT = "shoot",
    INPUT_RELOAD = "reload",
    -- 脚本内部输入：动作动画播放完毕
    INPUT_ANIM_END = "anim_end",

    STATIC_TRACK_LINE = STATIC_TRACK_LINE,
    BASE_TRACK = BASE_TRACK,
    MAIN_TRACK = MAIN_TRACK,

    -- 可由 display JSON 的 state_machine_param 覆盖
    enable_aim = true,
}

-- 取得主轨道行上的动作轨道；若尚未分配则返回 -1（调用方需要跳过）
local function main_track(context)
    local track = context:getTrack(M.STATIC_TRACK_LINE, M.MAIN_TRACK)
    if track == nil or track < 0 then
        return -1
    end
    return track
end

-- 在动作轨道播放动画
local function run_on_main(context, name, play_type, transition_time)
    local track = main_track(context)
    if track >= 0 then
        context:runAnimation(name, track, false, play_type, transition_time)
    end
end

-- 停止动作轨道
local function stop_main(context)
    local track = main_track(context)
    if track >= 0 then
        context:stopAnimation(track)
    end
end

-- ------------------------- idle -------------------------
local idle = {}

function idle.entry(this, context)
    context:runAnimation("idle", context:getTrack(this.STATIC_TRACK_LINE, this.BASE_TRACK), false, LOOP, 0)
    stop_main(context)
end

function idle.update(this, context)
    -- idle 时不需要主动推进，循环动画由动画系统自己播放
end

function idle.transition(this, context, input)
    if input == this.INPUT_SHOOT then
        return this.shoot
    elseif input == this.INPUT_RELOAD then
        return this.reload
    elseif this.enable_aim and input == this.INPUT_AIM then
        return this.aim
    end
end

-- ------------------------- aim -------------------------
local aim = {}

function aim.entry(this, context)
    context:runAnimation("aim", context:getTrack(this.STATIC_TRACK_LINE, this.BASE_TRACK), false, LOOP, 0.05)
end

function aim.update(this, context)
    -- 兜底：脚本外部的开镜状态已经结束时回到 idle
    if not context:isAiming() then
        context:trigger(this.INPUT_AIM_STOP)
    end
end

function aim.transition(this, context, input)
    if input == this.INPUT_AIM_STOP then
        return this.idle
    elseif input == this.INPUT_SHOOT then
        return this.shoot
    elseif input == this.INPUT_RELOAD then
        return this.reload
    end
end

-- ------------------------- shoot -------------------------
local shoot = {}

function shoot.entry(this, context)
    run_on_main(context, "shoot", PLAY_ONCE_STOP, 0.02)
end

function shoot.update(this, context)
    local track = main_track(context)
    if track >= 0 and context:isStopped(track) then
        context:trigger(this.INPUT_ANIM_END)
    end
end

function shoot.transition(this, context, input)
    if input == this.INPUT_SHOOT then
        -- 连发时重新进入本状态，重播射击动画
        return this.shoot
    elseif input == this.INPUT_ANIM_END then
        if this.enable_aim and context:isAiming() then
            return this.aim
        end
        return this.idle
    elseif input == this.INPUT_RELOAD then
        return this.reload
    end
end

-- ------------------------- reload -------------------------
local reload = {}

function reload.entry(this, context)
    run_on_main(context, "reload", PLAY_ONCE_STOP, 0.05)
end

function reload.update(this, context)
    local track = main_track(context)
    if track >= 0 and context:isStopped(track) then
        context:trigger(this.INPUT_ANIM_END)
    end
end

function reload.transition(this, context, input)
    if input == this.INPUT_RELOAD then
        return this.reload
    elseif input == this.INPUT_SHOOT then
        return this.shoot
    elseif input == this.INPUT_ANIM_END then
        if this.enable_aim and context:isAiming() then
            return this.aim
        end
        return this.idle
    end
end

M.idle = idle
M.aim = aim
M.shoot = shoot
M.reload = reload

-- 状态机初始化：分配轨道，并读取 display JSON 的 state_machine_param
function M:initialize(context)
    context:ensureTrackLineSize(track_line_top.value)
    context:ensureTracksAmount(STATIC_TRACK_LINE, 2)

    local params = context:getStateMachineParams()
    if params ~= nil and params.enable_aim ~= nil then
        self.enable_aim = params.enable_aim and true or false
    end
end

-- 状态机退出：销毁实例/资源重载时调用
function M:exit(context)
end

-- 初始状态列表。状态机是并行状态机：这里只放起始状态，其它状态由 transition 切换进入。
function M:states()
    return {self.idle}
end

return M
