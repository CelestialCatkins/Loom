# Loom

**一台空织机。** 安装后不含任何业务功能：只提供插件运行时、服务总线、项目文件系统、事件追踪，和装配这些能力的界面。
你能做的事，取决于你装了什么插件。

- 平台：Android（minSdk 26 / targetSdk 34）
- 技术栈：Kotlin 2.0.21 · Jetpack Compose (BOM 2024.09.03) · Material3 · LuaJ 3.0.1
- 构建：Android Studio（Ladybug 及以上）或命令行 Gradle 8.7

---

## 1. 构建

```bash
# 用 Android Studio 打开本目录即可（会自动下载 Gradle 与依赖）
# 或者命令行：
./gradlew assembleDebug          # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # 直接装到已连接设备
```

环境要求：JDK 17、Android SDK 34、可访问 `google()` 与 `mavenCentral()`。

第一次构建会拉取 Compose BOM 与 LuaJ（约 30–60 MB）。

---

## 2. 目录结构

```
app/src/main/java/com/loom/app/
├── LoomApp.kt                 Application：内核单例 + 连续崩溃判定
├── MainActivity.kt            唯一 Activity + 根 Composable（外壳、SAF、主题）
├── AppState.kt                界面状态（页签 / 运行台 / 提示条 / 主题）
├── (api 已独立为 :plugin-api 模块，见下)
├── kernel/                    内核：不含任何业务功能
│   ├── Kernel.kt              生命周期、依赖解析、激活、可逆卸载、安全模式
│   ├── PluginManifest.kt      plugin.json 强类型视图（Layer/Kind/Status）
│   ├── LoadedPlugin.kt        实例 + 状态机 + effect 栈
│   ├── ServiceBus.kt          provide / inject / observe
│   ├── EventBus.kt            事件 + 因果链追踪（环形缓冲）
│   ├── ProjectFs.kt           项目目录、数据、zip 导入导出
│   ├── Json.kt
│   └── lua/LuaHost.kt         Lua 沙箱 + ctx 表 + 值转换
├── ui/                        Token 之外的「构件层」
│   ├── theme/Token.kt         颜色 / 字号 / 间距 / 圆角（唯一来源）
│   ├── theme/Theme.kt         LoomTheme + LocalLoomColors
│   ├── Icons.kt               24 网格线稿图标系统（无位图、无 Emoji）
│   ├── Components.kt          按钮 / 徽章 / 行 / 表单 / 空状态 / 分段
│   ├── Sheets.kt              底部面板 / 二次确认 / 文本输入
│   ├── Scaffold.kt            底部导航 / 提示条 / 返回页头
│   └── Fmt.kt                 时间、字节、计数格式化
├── feature/                   五个区域 + 运行台
│   ├── Workbench.kt
│   ├── ProjectScreen.kt
│   ├── PluginsScreen.kt
│   ├── GraphScreen.kt
│   ├── SettingsScreen.kt
│   ├── RunScreen.kt
│   ├── DeclarativeRenderer.kt UI 插件的声明式界面渲染
│   └── Docs.kt                插件契约（应用内可查）
└── viz/                       关系图与运行链条
    └── Viz.kt                 布局 + android.graphics 渲染（Canvas 与 PNG 导出共用）
```

```
plugin-api/                   DEX 插件契约（插件作者只依赖这个模块）
└── src/main/java/com/loom/app/api/
    ├── Plugin.java
    ├── PluginContext.java    服务 / 事件 / 存储 / 两种界面注册
    ├── ScreenFactory.java    原生 View 贡献点
    ├── ComposeScreen.kt      @Composable 贡献点（ABI 锁定）
    └── LoomApi.java          契约常量（COMPOSE_ABI 等）
```

示例插件（**不打包进 APK**）在 `examples/`，文档在 `docs/`，设计决策在 `DESIGN.md`。

---

## 3. 第一次使用

1. 打开应用 → 工作台是空的（这是设计，不是 bug）。
2. `创建项目` → 输入名字。
3. 进 `插件` 页 → `导入插件包` → 选一个 zip。
   - 没有现成插件？用 `examples/` 里的：先 `bash examples/build-zips.sh` 打出 4 个 zip。
   - 建议顺序：`kv.logic.zip` → `counter.logic.zip` → `panel.ui.zip` → `note.ui.zip`。
4. 回 `项目` 页 → `运行项目` → 进运行台点按钮。
5. 去 `图谱` 页看关系图长出来；再切 `运行链条` 看事件因果链。
6. `项目` 页底部 `导出 .loomproj`，换台设备导入即可复原。

插件声明了依赖但提供者没装时，它会停在 `PENDING` 并列出缺什么 —— 回插件页把缺的装上就会自动激活。

---

## 4. 插件长什么样

```
my.plugin/
├── plugin.json          # 元数据 + 依赖 + 权限
└── source/
    ├── main.lua         # 逻辑插件：provide / on / emit
    └── screen.lua       # UI 插件：return 一棵界面表
```

逻辑插件示例：

```lua
local ctx = ...

ctx.provide("count", {
  inc = function()
    local n = ctx.state.get("n", 0) + 1
    ctx.state.set("n", n)
    ctx.emit("count.changed", { value = n })
    return n
  end,
  get = function() return ctx.state.get("n", 0) end,
})

ctx.on("count.inc", function() ctx.svc("count", "inc") end)
```

UI 插件示例：

```lua
local ctx = ...
return {
  title = "计数面板", icon = "grid",
  root = { type = "column", children = {
    { type = "metric", label = "当前计数", value = ctx.svc("count", "get") },
    { type = "button", text = "+1", emit = "count.inc" },
  }},
}
```

完整字段、节点类型、沙箱边界见 `docs/PLUGIN-CONTRACT.md`（应用内 `设置 → 插件契约` 也能看）。

---

## 5. 沙箱与信任模型（如实说明）

| 插件类型 | 执行方式 | 安全定位 |
|---|---|---|
| Lua 插件 | LuaJ 解释执行，库白名单（Base/Table/String/Math/Coroutine），无 io/os/package/debug，无 Java 反射 | 受限桥，风险可控；**已知限制**：无指令级超时 |
| DEX 插件 | `DexClassLoader` 同进程加载 | **非沙箱，等同宿主权限**；装载前需用户显式信任 sha256 |

DEX 插件能贡献两种界面：原生 `View`（`ctx.viewScreen`，任意 Compose 版本可用）与 `@Composable`（`ctx.composeScreen`，与宿主 Compose BOM 版本锁定，`plugin.json` 需声明 `composeAbi`）。示例见 `examples/dex-plugin/`。

应用内不暗示任何"沙箱隔离"：插件详情页会直接写出这段结论。

---

## 6. 不含什么（明确不做）

- 不做插件市场（本期只有本地导入）。
- 不做运行时任意替换图标/名称（Android 不允许；只做 3 套 `activity-alias` 预置组合，切换会重启应用）。
- 不做跨进程插件运行。
- 不内置任何示例插件或业务功能。

---

## 7. 本机验证到哪一步

Loom 是在没有 Android SDK 的机器上写的，但编译链被完整复现验证过：

```text
kotlinc 2.0.21 + compose plugin 2.0.21
  → 依赖：Compose 1.7.3 / Material3 1.3.0 / activity-compose 1.9.2 / LuaJ 3.0.1 / android.jar(34)
  → 全量源码 32 个文件 → 135 个 class，0 error
aapt2 compile + link → 资源与 AndroidManifest 通过
```

仍未验证：Gradle 依赖解析、真机运行、DEX 装载、SAF 导入导出。第一次构建若有依赖版本冲突，先按 `app/build.gradle.kts` 里的 BOM 对齐。
