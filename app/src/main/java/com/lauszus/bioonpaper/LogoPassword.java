package com.lauszus.bioonpaper;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

public class LogoPassword extends AppCompatActivity {
    static EditText password;
    String password1 ="bioonp@pe6";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logo_password);
        password=(EditText) findViewById(R.id.password);
        password.setText(password1);
    }

    public void scanQR(View view) {
        System.out.println(password.getText().toString().equals(password1));

        if (password.getText().toString().equals(password1)){
            Intent myIntent =new Intent(getBaseContext(),   FaceRecognitionAppActivity.class);
            myIntent.putExtra("password",password.getText().toString());
            startActivity(myIntent);
        }else {

            Intent myIntent =new Intent(getBaseContext(),   Reject.class);
            startActivity(myIntent);
        }
    }
}
