-- UI 插件：每次渲染都会重新执行，所以这里可以直接读服务。
local ctx = ...

return {
  title = "计数面板",
  icon = "grid",
  sub = "意图事件 → 逻辑层 → 服务状态",
  root = {
    type = "column",
    children = {
      { type = "eyebrow", text = "COUNTER / 计数" },
      { type = "metric", label = "当前计数", value = ctx.svc("count", "get"), unit = "STITCH" },
      { type = "space", h = 12 },
      {
        type = "row",
        children = {
          { type = "button", text = "+1 织一针", emit = "count.inc" },
          { type = "ghost", text = "归零", emit = "count.reset" },
        },
      },
      { type = "space", h = 8 },
      { type = "ghost", text = "把当前值写进项目存储", emit = "count.save" },
      { type = "divider" },
      { type = "kv", label = "已落盘 counter.last", value = ctx.store.get("counter.last") },
    },
  },
}
