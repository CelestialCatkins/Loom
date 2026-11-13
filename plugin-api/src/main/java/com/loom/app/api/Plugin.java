package com.loom.app.api;

/**
 * DEX 插件的对外契约（v1）。
 *
 * 插件与宿主同进程加载：没有沙箱、没有权限隔离，等同宿主权限。
 * 因此装载前必须由用户显式确认来源，并在界面上如实说明这一点。
 */
public interface Plugin {

    /** 装载入口。所有注册动作必须经 ctx 完成，卸载时由内核逆序撤销。 */
    void activate(PluginContext ctx) throws Exception;

    /** 释放自身资源（可选）。内核会在 effect 撤销之后再调用。 */
    default void deactivate() {
    }
}
