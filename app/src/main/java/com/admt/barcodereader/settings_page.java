package com.admt.barcodereader;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;

import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class settings_page extends AppCompatActivity
{

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_page);

        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

//        Button btnOk = (Button) findViewById(R.id.btnOK);
//        btnOk.requestFocus();

        EditText tbServerURL = (EditText) findViewById(R.id.tbServerURL);
        EditText tbSettingsPassword = (EditText)findViewById(R.id.tbSettingsPassword);
        Switch swUseServerDiscovery = (Switch)findViewById(R.id.swUseServerDiscovery);
        Button btnFindServerNow = (Button)findViewById(R.id.btnFindServerNow); 
        Switch swEnableUserStatus = (Switch)findViewById(R.id.swEnableUserStatus);
        Spinner spCameraSelect = (Spinner)findViewById(R.id.spCameraSelect);
        Spinner spDefaultStation = (Spinner)findViewById(R.id.spDefaultStation);
        Switch swStaticStation = (Switch)findViewById(R.id.swStaticStation);
        Switch swRememberUser = (Switch)findViewById(R.id.swRememberUser);
        Switch swRememberStation = (Switch)findViewById(R.id.swRememberStation);
        Switch swQuantityComplete = (Switch)findViewById(R.id.swQuantityComplete);
        EditText tbAppIdentifierName = (EditText)findViewById(R.id.tbAppIdentifierName);
        Spinner spDetectionDelay = (Spinner)findViewById(R.id.spDetectionDelay);
        Switch swUseFullscreenConfirmations = (Switch)findViewById(R.id.swUseFullscreenConfirmations);
        Switch swAllowStoppageDescriptions = (Switch)findViewById(R.id.swAllowStoppageDescriptions);
        Switch swEnableVibration = (Switch)findViewById(R.id.swEnableVibration);

        SharedPreferences prefs = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);

        tbServerURL.setText(prefs.getString(
                getString(R.string.prefs_server_base_address), "")
        );
//
//        String serverAddress = prefs.getString(
//                "serverURL", "");
        String settingsPassword = prefs.getString(
                "password", "1234");
        boolean enableUserStatus = prefs.getBoolean(
                "enableUserStatus", true);
        boolean useFrontCamera = prefs.getBoolean(
                getString(R.string.preferences_use_front_camera),true);
        String stationName = prefs.getString(
                getString(R.string.preferences_station_name), "");
        boolean staticStation = prefs.getBoolean(
                "staticStation",false);
        boolean rememberUser = prefs.getBoolean(
                "rememberUser",false);
        boolean rememberStation = prefs.getBoolean(
                "rememberStation",false);
        boolean quantityComplete = prefs.getBoolean(
                getString(R.string.preferences_quantity_complete),false);
        String userPrefix = prefs.getString(
                getString(R.string.preferences_user_prefix), getString(R.string.default_user_prefix));
        String productPrefix = prefs.getString(
                getString(R.string.preferences_product_prefix), getString(R.string.default_product_prefix));
        String appIdentifierName = prefs.getString(
                getString(R.string.preferences_app_id_name), getString(R.string.default_app_id_name));
        Set<String> stationSet = prefs.getStringSet(
                getString(R.string.preferences_stationIds), new HashSet<String>());
        boolean enableVibration = prefs.getBoolean(
                "enableVibration", true);
        long detectionDelay = prefs.getLong("detectionDelay", 1000);
        boolean useFullscreenConfirmations = prefs.getBoolean("useFullscreenConfirmations",true);
        boolean allowStoppageDescriptions = prefs.getBoolean("allowStoppageDescription",true);

        if(prefs.getBoolean(getString(R.string.prefs_use_server_discovery), false)){
            swUseServerDiscovery.setChecked(true);
            btnFindServerNow.setEnabled(true);
        }
        else{
            swUseServerDiscovery.setChecked(false);
            btnFindServerNow.setEnabled(false);
        }
        swUseServerDiscovery.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onUseServerDiscoveryChanged();
            }
        });
        btnFindServerNow.setOnClickListener(this::onFindServerBtnClicked);


        tbSettingsPassword.setText(settingsPassword);
        if(useFrontCamera)
            spCameraSelect.setSelection(0);
        else
            spCameraSelect.setSelection(1);

        // note times are in milliseconds
        if(detectionDelay == 0)
            spDetectionDelay.setSelection(0);
        else if(detectionDelay == 500)
            spDetectionDelay.setSelection(1);
        else if(detectionDelay == 1000)
            spDetectionDelay.setSelection(2);
        else if(detectionDelay == 2000)
            spDetectionDelay.setSelection(3);
        else if(detectionDelay == 3000)
            spDetectionDelay.setSelection(4);

        ArrayList<String> stationList = new ArrayList<>(stationSet);
        stationList.add(0, "");

        //set entries for default station spinner
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, stationList);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spDefaultStation.setAdapter(arrayAdapter);

        //set value of default station if valid
        int stationIndex = stationList.indexOf(stationName);
        if (stationName != null && (stationIndex > 0)){
            spDefaultStation.setSelection(stationIndex);
        }

        swStaticStation.setChecked(staticStation);
        swRememberUser.setChecked(rememberUser);
        swRememberStation.setChecked(rememberStation);
        swEnableUserStatus.setChecked(enableUserStatus);
        swQuantityComplete.setChecked(quantityComplete);
        tbAppIdentifierName.setText(appIdentifierName);
        swUseFullscreenConfirmations.setChecked(useFullscreenConfirmations);
        swAllowStoppageDescriptions.setChecked(allowStoppageDescriptions);
        swEnableVibration.setChecked(enableVibration);
    }



    public void onUseServerDiscoveryChanged(){
        Switch swUseServerDiscovery = (Switch) findViewById(R.id.swUseServerDiscovery);
        Button btnFindServerNow = (Button) findViewById(R.id.btnFindServerNow);
        Boolean state = swUseServerDiscovery.isChecked();
        btnFindServerNow.setEnabled(state);
    }

    public void onFindServerBtnClicked(View v){
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                EditText tbServerURL = (EditText) findViewById(R.id.tbServerURL);

                String TAG = "ServerDiscoverySettings";
                Log.d(TAG, "Begin server discovery");
                ServerDiscovery.DiscoveryResult discoveryResult =
                        ServerDiscovery.findServer(getApplicationContext());
                if (discoveryResult == null) {
                    Log.i(TAG, "Failed to find server");
                    return;
                }

                Log.i(TAG, "Found server. Base address is " + discoveryResult.serverBaseAddress);

                Handler mainHandler = new Handler(getApplicationContext().getMainLooper());
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        tbServerURL.setText(discoveryResult.serverBaseAddress);
                    }
                };
                mainHandler.post(runnable);
            }
        };
        new Thread(runnable).start();
    }

    public void onBtnOkClicked(View v)
    {
        SharedPreferences prefs = getSharedPreferences(getString(R.string.preferences_file_key),
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        EditText tbServerURL = (EditText)findViewById(R.id.tbServerURL);
        EditText tbSettingsPassword = (EditText)findViewById(R.id.tbSettingsPassword);
        Switch swUseServerDiscovery = (Switch) findViewById(R.id.swUseServerDiscovery);
        Spinner spCameraSelect = (Spinner)findViewById(R.id.spCameraSelect);
        Spinner spDefaultStation = (Spinner)findViewById(R.id.spDefaultStation);
        Switch swEnableUserStatus = (Switch)findViewById(R.id.swEnableUserStatus);
        Switch swStaticStation = (Switch)findViewById(R.id.swStaticStation);
        Switch swRememberUser = (Switch)findViewById(R.id.swRememberUser);
        Switch swRememberStation = (Switch)findViewById(R.id.swRememberStation);
        Switch swQuantityComplete = (Switch)findViewById(R.id.swQuantityComplete);
        EditText tbAppIdName = (EditText)findViewById(R.id.tbAppIdentifierName);
        Spinner spDetectionDelay = (Spinner)findViewById(R.id.spDetectionDelay);
        Switch swUseFullscreenConfirmations = (Switch)findViewById(R.id.swUseFullscreenConfirmations);
        Switch swAllowStoppageDescriptions = (Switch)findViewById(R.id.swAllowStoppageDescriptions);
        Switch swEnableVibration = (Switch)findViewById(R.id.swEnableVibration);
        Spinner spStationIdValue = (Spinner)findViewById(R.id.spStationIdValue);

        int camOptionSelection = spCameraSelect.getSelectedItemPosition();
        boolean useFrontCamera = true;
        if(camOptionSelection == 1)
            useFrontCamera = false;

        String serverBaseAddress = tbServerURL.getText().toString();

        editor.putString(getString(R.string.prefs_server_base_address), serverBaseAddress);

        String password = tbSettingsPassword.getText().toString();
        boolean staticStation = swStaticStation.isChecked();
        boolean quantityComplete = swQuantityComplete.isChecked();
                String stationName;
        if (spDefaultStation.getSelectedItem() != null)
            stationName = spDefaultStation.getSelectedItem().toString();
        else
            stationName = "";
        boolean enableUserStatus = swEnableUserStatus.isChecked();
        boolean enableVibration = swEnableVibration.isChecked();
        boolean rememberUser = swRememberUser.isChecked();
        boolean rememberStation = swRememberStation.isChecked();

        String appIdName = tbAppIdName.getText().toString();

        //if static station confirm that a default station has been selected
        if (staticStation && stationName.equals(""))
        {
            Toast.makeText(this,
                    "Enter Default Station",
                    Toast.LENGTH_LONG)
                    .show();
        }
        else {
            editor.putString(getString(R.string.preferences_password), password);
            editor.putBoolean(getString(R.string.preferences_use_front_camera), useFrontCamera);
            editor.putString(getString(R.string.preferences_station_name), stationName);
            editor.putBoolean("enableUserStatus", enableUserStatus);
            editor.putBoolean("staticStation", staticStation);
            editor.putBoolean("rememberUser", rememberUser);
            editor.putBoolean("rememberStation", rememberStation);
            editor.putBoolean(getString(R.string.preferences_quantity_complete), quantityComplete);
            editor.putString(getString(R.string.preferences_app_id_name), appIdName);
            editor.putBoolean("enableVibration", enableVibration);

            if(spDetectionDelay.getSelectedItemPosition() == 0)
                editor.putLong("detectionDelay", 0);
            else if(spDetectionDelay.getSelectedItemPosition() == 1)
                editor.putLong("detectionDelay", 500);
            else if(spDetectionDelay.getSelectedItemPosition() == 2)
                editor.putLong("detectionDelay", 1000);
            else if(spDetectionDelay.getSelectedItemPosition() == 3)
                editor.putLong("detectionDelay", 2000);
            else if(spDetectionDelay.getSelectedItemPosition() == 4)
                editor.putLong("detectionDelay", 3000);

            editor.putBoolean("useFullscreenConfirmations",swUseFullscreenConfirmations.isChecked());
            editor.putBoolean("allowStoppageDescription", swAllowStoppageDescriptions.isChecked());

            editor.putBoolean(getString(R.string.prefs_use_server_discovery), swUseServerDiscovery.isChecked());

            editor.commit();

            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }
    }
}
