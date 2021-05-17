package com.example.voice_translator;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if(savedInstanceState== null){
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container,new TranslateFragment())
                    .commitNow();
        }
    }
}