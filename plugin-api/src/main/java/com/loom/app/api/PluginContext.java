package com.loom.app.api;

import org.json.JSONObject;

/**
 * 内核交给 DEX 插件的唯一句柄。
 *
 * 服务、事件、存储都经这里；界面贡献点有三种形态：
 *  - 声明式（Lua 插件用 screen.lua，DEX 插件不用）
 *  - 原生 View（[viewScreen]，任意 Compose 版本可用）
 *  - Compose（[composeScreen]，与宿主 Compose BOM 版本锁定）
 */
public interface PluginContext {

    String pluginId();

    JSONObject config();

    void provide(String service, Service serviceImpl);

    Object call(String service, String method, JSONObject args) throws Exception;

    void emit(String event, JSONObject payload);

    void subscribe(String event, EventListener listener);

    /** 注册原生 View 界面贡献点。窗口标题与图标展示在运行台的标签栏。 */
    void viewScreen(String title, String icon, ScreenFactory factory);

    /** 注册 Compose 界面贡献点。版本锁定，plugin.json 必须声明 composeAbi。 */
    void composeScreen(String title, String icon, ComposeScreen screen);

    /** 登记任意可逆副作用，卸载时逆序执行。 */
    void effect(Runnable undo, String label);

    interface Service {
        Object call(String method, JSONObject args) throws Exception;
    }

    interface EventListener {
        void onEvent(String event, JSONObject payload);
    }
}
