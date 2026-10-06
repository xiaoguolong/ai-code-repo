-- 网关联会话：按前缀扫描并取回全部值（Week 19）。
-- 用 SCAN 而不是 KEYS：KEYS 在大 key 空间会阻塞 Redis 单线程，属于生产事故级用法。
-- 自检接口只做只读概况统计，故限制单次扫描上限，避免一次请求扫全库。
local result = {}
local cursor = '0'
local iterations = 0
repeat
  local reply = redis.call('SCAN', cursor, 'MATCH', ARGV[1], 'COUNT', 200)
  cursor = reply[1]
  local keys = reply[2]
  for i = 1, #keys do
    local value = redis.call('GET', keys[i])
    if value then
      result[#result + 1] = value
    end
    if #result >= tonumber(ARGV[2]) then
      return result
    end
  end
  iterations = iterations + 1
until cursor == '0' or iterations >= 50
return result
