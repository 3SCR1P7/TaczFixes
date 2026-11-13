-- 两脚架（grip_emx_synchronized0）状态机：趴下(匍匐)时展开，站立时收起。
-- 放置路径：assets/<namespace>/scripts/grip_emx_synchronized_state_machine.lua
-- display 中配置：
--   "taczfixes": {
--     "animation": "<namespace>:grip_emx_synchronized_animations",
--     "state_machine": "<namespace>:grip_emx_synchronized_state_machine",
--     "state_machine_param": { "crouch_also_deploy": false }
--   }
--
-- 动画含义：
--   collapsed_idle : 收起姿态（腿 -85°）
--   deployed_idle  : 展开姿态（腿 0°）
--   deploy         : 收起 -> 展开
--   retract        : 展开 -> 收起
-- 状态机是“并行状态机”，这里只返回一个初始状态，其余状态由 transition 切换。

local track_line_top = {value = 0}

local function increment(obj)
    obj.value = obj.value + 1
    return obj.value - 1
end

-- 主轨道行（与枪械动画一致：BASE_TRACK 放循环动画，MAIN_TRACK 放一次性动作）
local STATIC_TRACK_LINE = increment(track_line_top)
local BASE_TRACK = 0
local MAIN_TRACK = 1

local M = {
    INPUT_DEPLOY = "deploy",
    INPUT_RETRACT = "retract",
    INPUT_ANIM_END = "anim_end",

    STATIC_TRACK_LINE = STATIC_TRACK_LINE,
    BASE_TRACK = BASE_TRACK,
    MAIN_TRACK = MAIN_TRACK,

    -- 可选参数（display 的 state_machine_param 覆盖）：蹲下是否也算趴下
    crouch_also_deploy = false,
    -- 调试用：强制按趴下处理（true 时脚架会一直展开，用于验证动画是否生效）
    force_deploy = false,
}

local function main_track(context)
    local track = context:getTrack(M.STATIC_TRACK_LINE, M.MAIN_TRACK)
    if track == nil or track < 0 then
        return -1
    end
    return track
end

local function base_track(context)
    local track = context:getTrack(M.STATIC_TRACK_LINE, M.BASE_TRACK)
    if track == nil or track < 0 then
        return -1
    end
    return track
end

local function run_on_main(context, name, play_type, transition_time)
    local track = main_track(context)
    if track >= 0 then
        context:runAnimation(name, track, false, play_type, transition_time)
    end
end

local function run_on_base(context, name, play_type, transition_time)
    local track = base_track(context)
    if track >= 0 then
        context:runAnimation(name, track, false, play_type, transition_time)
    end
end

local function stop_main(context)
    local track = main_track(context)
    if track >= 0 then
        context:stopAnimation(track)
    end
end

local function stop_base(context)
    local track = base_track(context)
    if track >= 0 then
        context:stopAnimation(track)
    end
end

local function is_prone(context)
    if M.force_deploy then
        return true
    end
    if context:isProne() then
        return true
    end
    if M.crouch_also_deploy and context:isCrouching() then
        return true
    end
    return false
end

-- ------------------------- 收起（站立默认状态） -------------------------
local collapsed = {}

function collapsed.entry(this, context)
    stop_main(context)
    run_on_base(context, "collapsed_idle", LOOP, 0.05)
end

function collapsed.update(this, context)
    if is_prone(context) then
        context:trigger(this.INPUT_DEPLOY)
    end
end

function collapsed.transition(this, context, input)
    if input == this.INPUT_DEPLOY then
        return this.deploying
    end
end

-- ------------------------- 展开中（一次性动作，不被打断） -------------------------
local deploying = {}

function deploying.entry(this, context)
    stop_base(context)
    stop_main(context)
    run_on_main(context, "deploy", PLAY_ONCE_STOP, 0.05)
end

function deploying.update(this, context)
    local track = main_track(context)
    if track >= 0 and context:isStopped(track) then
        context:trigger(this.INPUT_ANIM_END)
    end
end

function deploying.transition(this, context, input)
    if input == this.INPUT_ANIM_END then
        return this.deployed
    end
end

-- ------------------------- 展开（趴下保持） -------------------------
local deployed = {}

function deployed.entry(this, context)
    stop_main(context)
    run_on_base(context, "deployed_idle", LOOP, 0.05)
end

function deployed.update(this, context)
    if not is_prone(context) then
        context:trigger(this.INPUT_RETRACT)
    end
end

function deployed.transition(this, context, input)
    if input == this.INPUT_RETRACT then
        return this.retracting
    end
end

-- ------------------------- 收起中（一次性动作，不被打断） -------------------------
local retracting = {}

function retracting.entry(this, context)
    stop_base(context)
    stop_main(context)
    run_on_main(context, "retract", PLAY_ONCE_STOP, 0.05)
end

function retracting.update(this, context)
    local track = main_track(context)
    if track >= 0 and context:isStopped(track) then
        context:trigger(this.INPUT_ANIM_END)
    end
end

function retracting.transition(this, context, input)
    if input == this.INPUT_ANIM_END then
        return this.collapsed
    end
end

M.collapsed = collapsed
M.deploying = deploying
M.deployed = deployed
M.retracting = retracting

-- 状态机初始化：分配轨道行并读取 display 的 state_machine_param
function M:initialize(context)
    context:ensureTrackLineSize(track_line_top.value)
    context:ensureTracksAmount(STATIC_TRACK_LINE, 2)

    local params = context:getStateMachineParams()
    if params ~= nil then
        if params.crouch_also_deploy ~= nil then
            self.crouch_also_deploy = params.crouch_also_deploy and true or false
        end
        if params.force_deploy ~= nil then
            self.force_deploy = params.force_deploy and true or false
        end
    end
end

-- 状态机退出（资源重载/切换世界时）
function M:exit(context)
end

-- 初始状态：站立收起
function M:states()
    return {self.collapsed}
end

return M
