-- 逻辑插件：订阅意图事件 → 改自己的状态 → 发状态事件。
local ctx = ...

ctx.provide("count", {
  inc = function()
    local n = ctx.state.get("n", 0) + 1
    ctx.state.set("n", n)
    ctx.emit("count.changed", { value = n })
    return n
  end,
  reset = function()
    ctx.state.set("n", 0)
    ctx.emit("count.changed", { value = 0 })
    return 0
  end,
  get = function()
    return ctx.state.get("n", 0)
  end,
  save = function()
    ctx.svc("kv", "put", { k = "counter.last", v = ctx.state.get("n", 0) })
    return true
  end,
})

-- 意图事件 → 服务调用
ctx.on("count.inc", function() ctx.svc("count", "inc") end)
ctx.on("count.reset", function() ctx.svc("count", "reset") end)
ctx.on("count.save", function() ctx.svc("count", "save") end)
