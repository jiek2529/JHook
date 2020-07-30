package com.jiek.jhook;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.jiek.jhook.AMSHook.AMSHookUtil;
import com.jiek.simple.SampleActivity;

public class MainActivity extends BaseActivity {

    Button go_page_btn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        go_page_btn = findViewById(R.id.go_page);

        AMSHookUtil.hookStartActivity(this);
        go_page_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //启动未注册的 Activity
                startActivity(new Intent(MainActivity.this, SampleActivity.class));
            }
        });
    }
}
