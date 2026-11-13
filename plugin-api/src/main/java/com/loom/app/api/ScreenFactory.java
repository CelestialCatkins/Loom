package com.loom.app.api;

import android.content.Context;
import android.view.View;

/**
 * 原生 View 界面贡献点：由宿主用 AndroidView 包进 Compose。
 * 这条路径没有 Compose 版本耦合，是 DEX 插件最稳的界面形式。
 */
public interface ScreenFactory {
    View create(Context context);
}
