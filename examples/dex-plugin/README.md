# DEX 插件示例（原生 View + Compose）

## 1. 打 dex

```bash
# 在本仓库里（plugin-api 是 Loom 的子模块）
./gradlew :examples:dex-plugin:assembleRelease   # 或把 build.gradle.kts 拷进你的 app 工程
# 产物在 build/intermediates/... 里，找到 classes.dex：
unzip -o build/outputs/aar/*.aar classes.jar -d tmp
d8 --release --min-api 26 --lib $ANDROID_HOME/platforms/android-34/android.jar \
   --output tmp tmp/classes.jar
```

## 2. 打包

```
hash.logic/
├── plugin.json          # 与上面同目录的 plugin.json
└── source/plugin.dex    # 上一步的 classes.dex
```

```bash
zip -r hash.logic.zip plugin.json source/
```

## 3. 导入

插件页 → 导入插件包 → 选 zip → 详情页会显示 sha256 → 「信任这个来源」→ 启用。
运行台里就会多出「摘要台」标签。

## 4. 两种界面形态

| 形态 | 注册方式 | 优缺点 |
|---|---|---|
| 原生 View | `ctx.viewScreen(title, icon, factory)` | 任意 Compose 版本可用；插件自己管生命周期 |
| Compose | `ctx.composeScreen(title, icon, screen)` | 更贴近宿主视觉；但 ABI 与宿主 Compose 编译器版本绑定，plugin.json 必须声明 `composeAbi` |

两者都可以，推荐先用 View 版本跑通，再决定要不要上 Compose。
