local M = {}

function M.shoot(api)
    local level = api:getLevel()
    if level >= 100 then
        local amount = tostring(level)
        api:runCommand("give @s minecraft:diamond",amount)
        api:setExp(api:getExp() - 1 )
    end
end

return M