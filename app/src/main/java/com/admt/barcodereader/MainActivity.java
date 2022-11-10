package com.admt.barcodereader;

import android.content.Context;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.widget.Toolbar;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity<mDiscoverServerTimerTask> extends AppCompatActivity
        implements cameraFragment.onBarcodeReadListener,
        dataDisplayFragment.onDataDisplayInteraction,
        numberPadFragment.OnNumpadInteractionListener
{
    private cameraFragment mCameraFragment = null;
    private dataDisplayFragment mDataDisplayFragment = null;
    private numberPadFragment mNumPadFragment = null;

    private Timer mDiscoverServerTimer = null;
    private TimerTask mDiscoverServerTimerTask = null;

    boolean torchOn = false;

    private String TAG = "MainActivity";

    public void onBarcodeRead(String barcodeValue)
    {
        mDataDisplayFragment.UpdateDisplayedbarcodeReading(barcodeValue);
    }

    public void onBarcodeEntered(String barcodeValue)
    {
        mDataDisplayFragment.UpdateDisplayedbarcodeReading(barcodeValue);
        onToggleNumPadRequest();
    }

    public void onBarcodeSeen()
    {
        if(mCameraFragment != null)
            mCameraFragment.flashScreen();
    }

    public void onBarcodeReadHandled()
    {
        if(mCameraFragment != null)
            mCameraFragment.cancelBarcodeReadWait();
    }

    public void onToggleTorchRequest()
    {
        if(mCameraFragment != null)
            mCameraFragment.toggleTorch();
    }

    public void onToggleNumPadRequest()
    {
        if(mNumPadFragment == null)
        {
            mNumPadFragment = new numberPadFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.cameraFragmentContainer, mNumPadFragment)
                    .commit();

            mCameraFragment = null;
        }
        else
        {
            mCameraFragment = new cameraFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.cameraFragmentContainer, mCameraFragment)
                    .commit();

            mNumPadFragment = null;
        }
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
//        toolbar.setBackgroundColor(Color.parseColor("#be0f34"));
//        toolbar.setTitleTextColor(0xFFFFFFFF);//Color.parseColor("#fff"));
//        toolbar.setSubtitleTextColor(0xFFFFFFFF);
        setSupportActionBar(toolbar);

        // don't add fragments if the app has resumed from suspension
        if (savedInstanceState != null) {
            return;
        }

        mCameraFragment = new cameraFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.cameraFragmentContainer, mCameraFragment).commit();

        mDataDisplayFragment = new dataDisplayFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.dataDisplayFragmentContainer, mDataDisplayFragment).commit();

        // As this app is expected to only have intermittent access to the network

        // created here but scheduled in onResume and cancelled in onPause
        mDiscoverServerTimerTask = new TimerTask() {
            @Override
            public void run() {
                String TAG = "ServerDiscoveryTimerTask";
                SharedPreferences prefs = getSharedPreferences(getString(R.string.preferences_file_key),
                        Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();

                if(prefs.getBoolean(getString(R.string.prefs_use_server_discovery), true))
                {
                    Log.d(TAG, "Begin server discovery");
                    ServerDiscovery.DiscoveryResult discoveryResult =
                            ServerDiscovery.findServer(getApplicationContext());
                    if(discoveryResult == null)
                    {
                        Log.i(TAG, "Failed to find server");
                        return;
                    }

                    Log.i(TAG, "Found server. Base address is " + discoveryResult.serverBaseAddress);

                    editor.putString(
                            getString(R.string.prefs_server_protocol),
                            discoveryResult.protocol);
                    editor.putString(
                            getString(R.string.prefs_server_url),
                            discoveryResult.ipAddress + ":" + discoveryResult.port);
                    editor.putString(
                            getString(R.string.prefs_server_base_address),
                            discoveryResult.serverBaseAddress);
                }
            }
        };
        mDiscoverServerTimer = new Timer();
        mDiscoverServerTimer.scheduleAtFixedRate(mDiscoverServerTimerTask, 1000, 15000);
    }

    @Override
    public void onClockedOn(String JobId)
    {
        Intent intent = new Intent(this, clockedOnConfirmation.class);
        intent.putExtra("jobId",JobId);
        startActivity(intent);
    }

    @Override
    public void onClockedOff(String JobId) {
        Intent intent = new Intent(this, clockedOffConfirmation.class);
        intent.putExtra("jobId",JobId);
        startActivity(intent);
    }

    @Override
    public void onStoppageStart(String JobId, String StoppageReason) {
        Intent intent = new Intent(this, stoppageStartConfirmation.class);
        intent.putExtra("jobId",JobId);
        intent.putExtra("stoppageReason", StoppageReason);
        startActivity(intent);
    }

    @Override
    public void onStoppageEnd(String JobId, String StoppageReason) {
        Intent intent = new Intent(this, stoppageEndedConfirmation.class);
        intent.putExtra("jobId",JobId);
        intent.putExtra("stoppageReason", StoppageReason);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.menu,menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch(item.getItemId())
        {
            // access the settings page (password protected)
            case R.id.miSettings:
                Intent intent = new Intent(this, settingsPasswordScreen.class);
                startActivity(intent);
                break;
            case R.id.miTorch:
                mCameraFragment.toggleTorch();
                torchOn = !torchOn;
                if (torchOn)
                    item.setIcon(R.drawable.ic_flashlight_yellow);
                else
                    item.setIcon(R.drawable.ic_flashlight_black);
                break;

        }

        return true;
    }
}
