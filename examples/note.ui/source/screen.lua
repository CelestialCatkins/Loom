return {
  title = "织机说明",
  icon = "note",
  sub = "宿主只提供装配能力",
  root = {
    type = "column",
    children = {
      { type = "eyebrow", text = "MANIFESTO / 说明" },
      { type = "headline", text = "这里没有功能，只有织机。" },
      { type = "text", text = "Loom 安装后不含任何业务功能。你看到的每一个按钮、每一条数据，都来自你导入的插件。" },
      { type = "space", h = 16 },
      { type = "divider" },
      { type = "eyebrow", text = "三层" },
      { type = "text", text = "UI 层只发意图事件；逻辑层订阅后执行并暴露服务；能力层提供原子能力。" },
      { type = "space", h = 12 },
      { type = "text", text = "插件之间不直接引用，只经服务总线与事件总线通信。" },
      { type = "space", h = 12 },
      { type = "note", text = "停用任一插件，依赖它的插件会立刻转入 PENDING，而不是静默失败。" },
    },
  },
}
