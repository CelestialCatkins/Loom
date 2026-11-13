# Loom 插件契约 v1

> 适用范围：Loom 宿主 1.0.x。契约变更必须升版本号，并在插件页显示"契约版本不一致"。
> 插件是**独立文件**：一个 zip 包，解压后目录里直接有 `plugin.json`。

---

## 1. 目录结构

```
my.plugin/
├── plugin.json
└── source/
    ├── main.lua        # 逻辑插件：注册服务 / 订阅事件
    ├── screen.lua      # UI 插件：返回一棵界面表
    └── plugin.dex      # DEX 插件：编译产物（可选）
```

宿主的项目目录（不可自行改动，插件只能经 ctx 写入）：

```
projects/{id}/
├── project.json
├── plugins/ui/{plugin-id}/
├── plugins/logic/{plugin-id}/
├── data/                  # ctx.store 落在 data/store.json
└── exports/
```

**层级只看目录**：`layer=ui` 的插件必须装在 `plugins/ui/` 下，否则内核直接判 ERROR（不猜、不自动修）。

---

## 2. plugin.json

| 字段 | 必填 | 说明 |
|---|---|---|
| `id` | ✅ | 唯一标识，不含 `/`、`..`；建议 `名字.层`，如 `counter.logic` |
| `name` | ✅ | 显示名 |
| `version` | ✅ | 语义化版本 |
| `layer` | ✅ | `ui` 或 `logic` |
| `kind` | 逻辑层必填 | `business` 或 `capability` |
| `inject` | ✅ | 依赖的服务名数组（空数组也要写） |
| `provides` | ✅ | 提供的服务名数组 |
| `permissions` | ✅ | 能力声明：`storage` / `network` / `camera` / `location` / `sensors` / `clipboard` |
| `entry` | ✅ | 脚本入口 `source/main.lua`，或 DEX `source/plugin.dex` |
| `screen` | UI 层必填 | `source/screen.lua` |
| `mainClass` | DEX 必填 | 实现 `com.loom.app.api.Plugin` 的类名 |

装载时的判定顺序：

1. `plugin.json` 解析失败 → `ERROR`
2. 目录层级 ≠ `layer` → `ERROR`
3. 逻辑层插件 `inject` 了 UI 层提供的服务 → `ERROR`（架构硬约束）
4. `inject` 里任一服务没有提供者 → `PENDING`（列出缺失项）
5. 以上都过 → 执行入口 → `ACTIVE`；入口抛异常 → `ERROR`（详情页给原因）

新插件默认启用；用户在插件页停用后，id 会进 `project.json` 的 `disabled`。

---

## 3. Lua 运行环境

- 解释器：LuaJ 3.0.1（Lua 5.2 语义），纯 Java，无 native 依赖。
- 可用库：`Base`、`Table`、`String`、`Math`、`Coroutine`。
- 移除：`dofile`、`loadfile`、`load`、`require`、`package`；`print` 被替换为写日志。
- 没有 `io`、`os`、`debug`，也没有 `luajava` 绑定 —— 脚本无法触达 Android SDK 或反射。
- **已知限制**：没有指令级超时。死循环脚本会卡住主线程，请自行控制循环次数。

---

## 4. ctx 表（main.lua）

```lua
local ctx = ...
```

| 成员 | 签名 | 说明 |
|---|---|---|
| `ctx.id` / `ctx.name` / `ctx.layer` | string | 只读元信息 |
| `ctx.log(msg)` | `any → nil` | 写 logcat（tag `LoomLua`） |
| `ctx.emit(event, payload)` | `(string, table) → traceId` | 发事件，进入运行链条 |
| `ctx.on(event, fn)` | `(string, function) → nil` | 订阅事件；`fn(payload)` |
| `ctx.provide(name, table)` | `(string, table) → nil` | 提供服务；表里每个键是一个方法，方法签名 `fn(args)` |
| `ctx.call(service, method, args)` | `→ nil` | 调服务（丢弃返回值） |
| `ctx.svc(service, method, args)` | `→ value` | 调服务并取返回值（值或表） |
| `ctx.state` | table | 插件私有持久状态：`get(k, default)` / `set(k, v)` / `all()` |
| `ctx.store` | table | 项目级存储（`data/store.json`）：`get(k)` / `set(k, v)` |
| `ctx.config` | table | 项目 `config` 快照 |

注意：`ctx` 的方法都是**闭包**，用点号调用（`ctx.emit(...)`），不要写成冒号（`ctx:emit(...)`）。

### 4.1 逻辑插件骨架

```lua
local ctx = ...

ctx.provide("count", {
  inc = function()
    local n = ctx.state.get("n", 0) + 1
    ctx.state.set("n", n)
    ctx.emit("count.changed", { value = n })
    return n
  end,
  get = function()
    return ctx.state.get("n", 0)
  end,
})

ctx.on("count.inc", function(payload)
  ctx.svc("count", "inc")
end)
```

### 4.2 可逆卸载

宿主把每一次 `ctx.provide` / `ctx.on` 都包成一个 effect。卸载或停用时：

1. 逆序撤销全部 effect（退订事件、撤销服务）；
2. 清理项目内 `data/state-{id}.json`；
3. 该插件的依赖方重新解析依赖 → 转 `PENDING`。

插件不需要写清理代码；但**你自己在 Lua 里起的副作用（例如缓存）不会被自动撤销**。

---

## 5. screen.lua（UI 插件）

每次运行台重绘都会重新执行一次这个 chunk，因此这里可以直接读服务、读 store —— 界面永远显示当下状态。

```lua
local ctx = ...
return {
  title = "计数面板",        -- 运行台标签名
  icon = "grid",             -- 图标名（见宿主图标系统）
  sub = "意图事件 → 逻辑层 → 服务状态",
  root = { type = "column", gap = 12, children = {
    { type = "eyebrow", text = "COUNTER / 计数" },
    { type = "metric", label = "当前计数", value = ctx.svc("count", "get"), unit = "STITCH" },
    { type = "row", children = {
      { type = "button", text = "+1", emit = "count.inc" },
      { type = "ghost", text = "归零", emit = "count.reset" },
    }},
    { type = "divider" },
    { type = "note", text = "说明文字" },
  }},
}
```

### 5.1 节点类型

| type | 字段 | 说明 |
|---|---|---|
| `column` / `row` | `children`（数组）、`gap`（数字，可选） | 布局 |
| `eyebrow` | `text` | 等宽小标，铜色 |
| `headline` | `text` | 衬线大标题 |
| `text` | `text`、`mono`/`muted`（bool） | 正文 |
| `metric` | `label`、`value`、`unit` | 大号等宽数字 |
| `button` | `text`、`emit` | 主按钮，只发意图事件 |
| `ghost` | `text`、`emit` | 次按钮 |
| `divider` | — | 1px 发线 |
| `space` | `h`（数字，dp） | 间距 |
| `note` | `text` | 带铜色左线的提示块 |
| `tag` | `text` | 标签 |
| `kv` | `label`、`value` | 键值行（等宽值） |

按钮只发事件，不直接改状态 —— 逻辑层订阅后执行。这样运行链条里永远能看到"意图 → 执行"的两段。

---

## 6. DEX 插件

```java
public class MyPlugin implements com.loom.app.api.Plugin {
  @Override public void activate(PluginContext ctx) throws Exception {
    ctx.provide("hash", (method, args) -> sha256(args.optString("text")));
    ctx.subscribe("app.start", (event, payload) -> ctx.emit("app.started", null));
  }
}
```

- 加载：`DexClassLoader`，`parent` 是宿主 ClassLoader，因此插件能引用 `com.loom.app.api.*`。
- 信任：装载前必须校验 sha256 已被用户显式信任（插件详情页的"信任这个来源"）。
- 诚实声明：**同进程、非沙箱、等同宿主权限**。宿主不做进程隔离，也不做权限降级。
- UI 贡献点有两种注册方式，见 §6.1。

### 6.1 DEX 插件的界面贡献点（两种）

**A. 原生 View（推荐，无版本耦合）**

```java
ctx.viewScreen("摘要台", "code", context -> {
    TextView t = new TextView(context);
    t.setText("插件用 View 画的界面");
    return t;
});
```

宿主用 `AndroidView` 把它嵌进 Compose。View 的生命周期由插件自己负责（`deactivate()` 里收尾）。

**B. Compose（版本锁定）**

```kotlin
class MyScreen : ComposeScreen {
    @Composable override fun Content() {
        Text("插件用 Compose 画的界面")
    }
}
ctx.composeScreen("摘要台", "code", MyScreen())
```

`@Composable` 的调用 ABI 与 Compose 编译器版本绑定，所以：

1. `plugin.json` 必须声明 `"composeAbi": "2024.09.03"`，与宿主 Compose BOM 一致（常量见 `LoomApi.COMPOSE_ABI`）；
2. 宿主在装载时会校验：`composeAbi` 不一致 → `ERROR`；`Content(Composer,int)` 找不到 → `ERROR`。

契约不保证跨 Compose 版本的二进制兼容 —— 这是 Compose 的既有约束，不是 Loom 的取舍。
需要长期稳定就用 A。

---

## 7. 导入与排查

| 现象 | 原因 | 处理 |
|---|---|---|
| 插件出现在列表但 `PENDING` | `inject` 里的服务没有提供者 | 把提供者装上；详情页会列出缺什么 |
| `ERROR: 目录层级与 layer 不一致` | zip 里的 layer 与放置目录不符 | 重装；让宿主按 manifest 决定目录 |
| `ERROR: 服务名已被占用` | 同名服务被另一个插件先注册 | 改服务名，或先停用另一个 |
| `ERROR: 架构违规` | 逻辑层注入了 UI 层服务 | 把服务下沉到逻辑层 |
| `ERROR: DEX 来源未受信` | sha256 不在白名单 | 插件详情页点"信任这个来源" |
| 运行台没有标签 | 没有 ACTIVE 的 UI 插件 | 检查 `screen` 字段与 `screen.lua`，或 DEX 插件是否注册了界面 |
| `ERROR: Compose ABI 不匹配` | 插件 Compose 版本与宿主不同 | 用同版本 Compose 重编，或改用 `viewScreen` |
