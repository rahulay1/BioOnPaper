package com.lauszus.bioonpaper;

import android.content.Intent;
import android.os.Handler;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class Reject extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reject);



    }

    @Override
    public void onBackPressed() {
        Intent myIntent =new Intent(getBaseContext(),   LogoPassword.class);
        startActivity(myIntent);
    }
}
