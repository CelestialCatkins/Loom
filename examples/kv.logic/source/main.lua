-- 能力插件：只管存取，不做业务判断。
local ctx = ...

ctx.provide("kv", {
  put = function(args)
    ctx.store.set(tostring(args.k), args.v)
    return true
  end,
  get = function(args)
    return ctx.store.get(tostring(args.k))
  end,
})
