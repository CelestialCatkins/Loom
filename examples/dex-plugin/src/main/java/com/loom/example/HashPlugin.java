package com.loom.example;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.loom.app.api.Plugin;
import com.loom.app.api.PluginContext;


import java.security.MessageDigest;

/**
 * DEX 插件示例：
 *  1) 注册一个原生 View 界面贡献点（任意 Compose 版本可用）
 *  2) 同一个类也实现了 sha256，作为界面内容
 *
 * 注意：DEX 插件与宿主同进程加载，没有沙箱，等同宿主权限。
 */
public class HashPlugin implements Plugin {

    private PluginContext ctx;

    @Override
    public void activate(PluginContext context) {
        this.ctx = context;

        context.viewScreen("摘要台", "code", this::buildView);
    }

    private String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(text.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "错误：" + e.getMessage();
        }
    }

    private View buildView(Context c) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(c, 20), dp(c, 20), dp(c, 20), dp(c, 20));

        TextView title = new TextView(c);
        title.setText("DEX 插件 · 摘要台");
        title.setTextSize(20);
        title.setTextColor(Color.parseColor("#1A1B17"));
        col.addView(title);

        TextView hint = new TextView(c);
        hint.setText("这个界面是插件用 Android View 画的，宿主用 AndroidView 把它嵌进 Compose。");
        hint.setTextSize(13);
        hint.setTextColor(Color.parseColor("#6C6A60"));
        hint.setPadding(0, dp(c, 8), 0, dp(c, 16));
        col.addView(hint);

        final TextView out = new TextView(c);
        out.setText("—");
        out.setTextSize(12);
        out.setTypeface(android.graphics.Typeface.MONOSPACE);
        out.setTextColor(Color.parseColor("#B4512A"));
        out.setGravity(Gravity.START);
        out.setTextIsSelectable(true);
        col.addView(out);

        try {
            out.setText(sha256("loom"));
        } catch (Exception ignored) {
        }
        return col;
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    @Override
    public void deactivate() {
        ctx = null;
    }
}
