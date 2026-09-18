local M = {}

function M.shoot(api)
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
