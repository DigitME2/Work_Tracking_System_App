package com.admt.barcodereader;

import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import androidx.appcompat.widget.Toolbar;

import android.view.Menu;
import android.view.MenuItem;

public class MainActivity extends AppCompatActivity
        implements cameraFragment.onBarcodeReadListener,
        dataDisplayFragment.onDataDisplayInteraction,
        numberPadFragment.OnNumpadInteractionListener
{
    private cameraFragment mCameraFragment = null;
    private dataDisplayFragment mDataDisplayFragment = null;
    private numberPadFragment mNumPadFragment = null;
    boolean torchOn = false;

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
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
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
