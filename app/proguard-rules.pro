# LuaJ 通过反射装配库函数，保留其公开 API
-keep class org.luaj.** { *; }
-dontwarn org.luaj.**
# DEX 插件的对外契约
-keep interface com.loom.app.api.** { *; }
