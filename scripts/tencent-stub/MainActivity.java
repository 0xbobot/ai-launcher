package com.tencent.map;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

/**
 * 冒烟桩 Activity：把启动自己的 intent data 原样显示成文本，
 * 让冒烟脚本能在 uiautomator dump 里核对深链 URI 是否真分发到了本包。
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        show(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        show(intent);
    }

    private void show(Intent intent) {
        TextView tv = new TextView(this);
        tv.setTextSize(18);
        String data = (intent != null && intent.getData() != null)
                ? intent.getData().toString()
                : "(launcher)";
        tv.setText("TENCENT STUB " + data);
        setContentView(tv);
    }
}
