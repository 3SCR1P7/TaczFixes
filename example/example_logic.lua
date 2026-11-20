local M = {}

-- HTML HUD 自定义参数示例: 页面用 data-taczfixes-param-<key> 属性或
-- data-taczfixes-bind="<key>" / data-taczfixes-bind-width="<key>" 读取。
-- 参数按快捷栏槽位同步, 只影响主手枪械; api:clearHudParams() 可一次清空。
local function publish_hud(api)
    local max = api:getChargePowerMax()
    local energy = api:getChargePower()
    api:setHudParam("energy", energy)
    api:setHudParam("energy_percent", max > 0 and math.floor(energy * 100 / max) or 0)
    api:setHudParams({
        level = api:getLevel(),
        exp = api:getExp(),
        mode = api:getDualWieldState(),
    })
end

function M.shoot(api)
    publish_hud(api)
    api:runCommand("scoreboard objectives add tacz_time dummy")
    local level = api:getLevel()
    if level >= 100 and api:getScoreboardValue("tacz_time") <= 10 then
        local amount = tostring(level)
        api:runCommand("give @s minecraft:diamond",amount)
        api:runCommand("scoreboard players add @s tacz_time 1")
        api:setExp(api:getExp() - 1)
    end
end

function M.bullet_tick(api)
  local motion = api:getBulletMotion(api)
  api:setBulletMotion(motion[1] * 2, motion[2] * 2, motion[3] * 2)
end

return M
