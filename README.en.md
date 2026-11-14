[简体中文](./README.md) · **English**

# Loom

**An empty loom.** A fresh install ships no business features at all: just the plugin runtime, the service bus, the project file system, event tracing, and the UI that ties them together.
What you can do depends on which plugins you install.

- Platform: Android (minSdk 26 / targetSdk 34)
- Stack: Kotlin 2.0.21 · Jetpack Compose (BOM 2024.09.03) · Material3 · LuaJ 3.0.1
- Build: Android Studio (Ladybug or newer) or Gradle 8.7 from the command line

---

## 1. Build

```bash
# Open this directory in Android Studio (Gradle and dependencies download automatically)
# Or from the command line:
./gradlew assembleDebug          # output: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # install straight to a connected device
```

Requirements: JDK 17, Android SDK 34, and network access to `google()` and `mavenCentral()`.

The first build pulls the Compose BOM and LuaJ (roughly 30–60 MB).

---

## 2. Directory layout

```
app/src/main/java/com/loom/app/
├── LoomApp.kt                 Application: kernel singleton + consecutive-crash detection
├── MainActivity.kt            The only Activity + root Composable (shell, SAF, theme)
├── AppState.kt                UI state (tabs / workbench / banner / theme)
├── (api now lives in its own :plugin-api module, see below)
├── kernel/                    Kernel: no business logic whatsoever
│   ├── Kernel.kt              Lifecycle, dependency resolution, activation, reversible uninstall, safe mode
│   ├── PluginManifest.kt      Typed view of plugin.json (Layer/Kind/Status)
│   ├── LoadedPlugin.kt        Instance + state machine + effect stack
│   ├── ServiceBus.kt          provide / inject / observe
│   ├── EventBus.kt            Events + causal chain tracing (ring buffer)
│   ├── ProjectFs.kt           Project directory, data, zip import/export
│   ├── Json.kt
│   └── lua/LuaHost.kt         Lua sandbox + ctx table + value conversion
├── ui/                        The "component layer" on top of tokens
│   ├── theme/Token.kt         Color / type / spacing / corner radius (single source of truth)
│   ├── theme/Theme.kt         LoomTheme + LocalLoomColors
│   ├── Icons.kt               24-grid line icon system (no bitmaps, no emoji)
│   ├── Components.kt          Buttons / badges / rows / forms / empty states / segments
│   ├── Sheets.kt              Bottom sheets / confirmations / text input
│   ├── Scaffold.kt            Bottom navigation / banner / back header
│   └── Fmt.kt                 Time, byte, and count formatting
├── feature/                   Five areas + workbench
│   ├── Workbench.kt
│   ├── ProjectScreen.kt
│   ├── PluginsScreen.kt
│   ├── GraphScreen.kt
│   ├── SettingsScreen.kt
│   ├── RunScreen.kt
│   ├── DeclarativeRenderer.kt Declarative UI rendering for UI plugins
│   └── Docs.kt                Plugin contract (readable in-app)
└── viz/                       Relationship graph and run chains
    └── Viz.kt                 Layout + android.graphics rendering (shared by Canvas and PNG export)
```

```
plugin-api/                   DEX plugin contract (plugin authors depend on this module only)
└── src/main/java/com/loom/app/api/
    ├── Plugin.java
    ├── PluginContext.java    Services / events / storage / two kinds of UI registration
    ├── ScreenFactory.java    Native View contribution point
    ├── ComposeScreen.kt      @Composable contribution point (ABI locked)
    └── LoomApi.java          Contract constants (COMPOSE_ABI and friends)
```

Sample plugins (**not bundled into the APK**) live in `examples/`, docs in `docs/`, design decisions in `DESIGN.md`.

---

## 3. First run

The app UI is in Chinese; the original label is given first, with an English gloss in brackets.

1. Open the app → the workbench is empty (that's the design, not a bug).
2. `创建项目` (Create project) → type a name.
3. Go to the `插件` (Plugins) tab → `导入插件包` (Import plugin package) → pick a zip.
   - No plugins on hand? Use the ones in `examples/`: run `bash examples/build-zips.sh` to build 4 zips.
   - Suggested order: `kv.logic.zip` → `counter.logic.zip` → `panel.ui.zip` → `note.ui.zip`.
4. Back to `项目` (Projects) → `运行项目` (Run project) → tap buttons on the workbench.
5. Open the `图谱` (Graph) tab and watch the graph grow; switch to `运行链条` (Run chains) to follow the causal event chain.
6. At the bottom of `项目` (Projects), tap `导出 .loomproj` (Export .loomproj) — import it on another device to restore everything.

If a plugin declares a dependency whose provider isn't installed, it parks in `PENDING` and lists what's missing — install the missing pieces on the `插件` (Plugins) tab and it activates automatically.

---

## 4. What a plugin looks like

```
my.plugin/
├── plugin.json          # metadata + dependencies + permissions
└── source/
    ├── main.lua         # logic plugin: provide / on / emit
    └── screen.lua       # UI plugin: returns a screen table
```

Logic plugin example:

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

UI plugin example:

```lua
local ctx = ...
return {
  title = "Counter panel", icon = "grid",
  root = { type = "column", children = {
    { type = "metric", label = "Current count", value = ctx.svc("count", "get") },
    { type = "button", text = "+1", emit = "count.inc" },
  }},
}
```

Full field list, node types, and sandbox boundaries are in `docs/PLUGIN-CONTRACT.md` (also available in-app under `设置 → 插件契约`, Settings → Plugin contract).

---

## 5. Sandbox and trust model (the honest version)

| Plugin type | Execution | Security posture |
|---|---|---|
| Lua plugin | Interpreted by LuaJ, whitelisted libraries (Base/Table/String/Math/Coroutine), no io/os/package/debug, no Java reflection | Restricted bridge, bounded risk; **known limitation**: no instruction-level timeout |
| DEX plugin | Loaded in-process via `DexClassLoader` | **Not a sandbox — runs with host privileges**; the user must explicitly trust its sha256 before loading |

DEX plugins can contribute two kinds of UI: native `View` (`ctx.viewScreen`, works with any Compose version) and `@Composable` (`ctx.composeScreen`, locked to the host's Compose BOM version — `plugin.json` must declare `composeAbi`). See `examples/dex-plugin/` for a sample.

The app never hints at any "sandbox isolation": the plugin detail screen states this conclusion outright.

---

## 6. What it doesn't include (deliberately out of scope)

- No plugin marketplace (this release is local import only).
- No swapping icon/name at runtime (Android doesn't allow it; only 3 preset `activity-alias` combinations, and switching restarts the app).
- No cross-process plugin execution.
- No bundled sample plugins or business features.

---

## 7. How far local verification goes

Loom was written on a machine with no Android SDK, but the compile chain was fully reproduced and verified:

```text
kotlinc 2.0.21 + compose plugin 2.0.21
  → dependencies: Compose 1.7.3 / Material3 1.3.0 / activity-compose 1.9.2 / LuaJ 3.0.1 / android.jar(34)
  → 32 source files → 135 classes, 0 errors
aapt2 compile + link → resources and AndroidManifest pass
```

Still unverified: Gradle dependency resolution, running on a real device, DEX loading, SAF import/export. If the first build hits a dependency version conflict, align with the BOM in `app/build.gradle.kts` first.
