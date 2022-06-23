package com.admt.barcodereader;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

public class clockedOnConfirmation extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clocked_on_confirmation);

        Bundle bundle = getIntent().getExtras();
        if(bundle != null){
            String jobId = bundle.getString("jobId");
            TextView jobLabel = (TextView)findViewById(R.id.tvClockedOnLabel);
            jobLabel.setText(jobId);
        }
        Timer timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                finish();
            }
        };
        timer.schedule(timerTask, 3000);
    }
}