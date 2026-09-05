local M = {}

function M.shoot(api)
    api:runCommand("scoreboard objectives add tacz_time dummy")
    local level = api:getLevel()
    if level >= 100 and api:getScoreboardValue("tacz_time") <= 10 then
        local amount = tostring(level)
        api:runCommand("give @s minecraft:diamond",amount)
        api:runCommand("scoreboard players add @s tacz_time 1")
        api:setExp(api:getExp() - 1 )
    end
end

return M
