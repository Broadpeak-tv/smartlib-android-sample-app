package tv.broadpeak.smartlib.demo.android;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;

import tv.broadpeak.smartlib.SmartLib;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initApp();

        findViewById(R.id.startLIVE).setOnClickListener(view -> {
            startActivity(new Intent(getApplicationContext(), LiveContentActivity.class));
        });
    }

    public void initApp() {
        SmartLib.getInstance().init(getApplicationContext(), "http://analytics-players.broadpeak.tv/", "", "pf6.broadpeak-vcdn.com");
    }
}