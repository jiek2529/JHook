package com.jiek.simple;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.jiek.jhook.BaseActivity;
import com.jiek.jhook.MainActivity;
import com.jiek.jhook.Page2Activity;
import com.jiek.jhook.R;

/**
 * 未注册的,且不一主包下的 Actviity
 */
public class SampleActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample);

//        AMSHookUtil.hookStartActivity(this);
        findViewById(R.id.go_page).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SampleActivity.this, Page2Activity.class));
            }
        });
    }
}
