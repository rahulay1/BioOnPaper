package com.lauszus.bioonpaper;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class Accept extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accept);
    }

    @Override
    public void onBackPressed() {
        Intent myIntent =new Intent(getBaseContext(),   LogoPassword.class);
        startActivity(myIntent);
    }
}
