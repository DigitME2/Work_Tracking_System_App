package com.admt.barcodereader;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;

import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
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

        EditText tbServerURL = (EditText)findViewById(R.id.tbServerURL);
        EditText tbSettingsPassword = (EditText)findViewById(R.id.tbSettingsPassword);
        Spinner spCameraSelect = (Spinner)findViewById(R.id.spCameraSelect);
        Spinner spDefaultStation = (Spinner)findViewById(R.id.spDefaultStation);
        Switch swStaticStation = (Switch)findViewById(R.id.swStaticStation);
        Switch swRememberUser = (Switch)findViewById(R.id.swRememberUser);
        Switch swQuantityComplete = (Switch)findViewById(R.id.swQuantityComplete);
        EditText tbUserIdPrefix = (EditText)findViewById(R.id.tbUserIdPrefix);
        EditText tbProductIdPrefix = (EditText)findViewById(R.id.tbProductIdPrefix);
        EditText tbAppIdentifierName = (EditText)findViewById(R.id.tbAppIdentifierName);


        SharedPreferences prefs = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);

        String serverAddress = prefs.getString(
                "serverURL", "");
        String settingsPassword = prefs.getString(
                "password", "1234");
        boolean useFrontCamera = prefs.getBoolean(
                getString(R.string.preferences_use_front_camera),true);
        String stationName = prefs.getString(
                getString(R.string.preferences_station_name), "");
        boolean staticStation = prefs.getBoolean(
                "staticStation",false);
        boolean rememberUser = prefs.getBoolean(
                "rememberUser",false);
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
        tbServerURL.setText(serverAddress);
        tbSettingsPassword.setText(settingsPassword);
        if(useFrontCamera)
            spCameraSelect.setSelection(0);
        else
            spCameraSelect.setSelection(1);

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
        swQuantityComplete.setChecked(quantityComplete);
        tbUserIdPrefix.setText(userPrefix);
        tbProductIdPrefix.setText(productPrefix);
        tbAppIdentifierName.setText(appIdentifierName);
    }

    public void onBtnOkClicked(View v)
    {
        SharedPreferences prefs = getSharedPreferences(getString(R.string.preferences_file_key),
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        EditText tbServerURL = (EditText)findViewById(R.id.tbServerURL);
        EditText tbSettingsPassword = (EditText)findViewById(R.id.tbSettingsPassword);
        Spinner spCameraSelect = (Spinner)findViewById(R.id.spCameraSelect);
        Spinner spDefaultStation = (Spinner)findViewById(R.id.spDefaultStation);
        Switch swStaticStation = (Switch)findViewById(R.id.swStaticStation);
        Switch swRememberUser = (Switch)findViewById(R.id.swRememberUser);
        Switch swQuantityComplete = (Switch)findViewById(R.id.swQuantityComplete);
        EditText tbUserIdPrefix = (EditText)findViewById(R.id.tbUserIdPrefix);
        EditText tbProductIdPrefix = (EditText)findViewById(R.id.tbProductIdPrefix);
        EditText tbAppIdName = (EditText)findViewById(R.id.tbAppIdentifierName);

        int camOptionSelection = spCameraSelect.getSelectedItemPosition();
        boolean useFrontCamera = true;
        if(camOptionSelection == 1)
            useFrontCamera = false;

        String serverAddress = tbServerURL.getText().toString();
        String password = tbSettingsPassword.getText().toString();
        boolean staticStation = swStaticStation.isChecked();
        boolean quantityComplete = swQuantityComplete.isChecked();
                String stationName;
        if (spDefaultStation.getSelectedItem() != null)
            stationName = spDefaultStation.getSelectedItem().toString();
        else
            stationName = "";

        boolean rememberUser = swRememberUser.isChecked();

        String userPrefix = tbUserIdPrefix.getText().toString();
        String productPrefix = tbProductIdPrefix.getText().toString();
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

            editor.putString("serverURL", serverAddress);
            editor.putString(getString(R.string.preferences_password), password);
            editor.putBoolean(getString(R.string.preferences_use_front_camera), useFrontCamera);
            editor.putString(getString(R.string.preferences_station_name), stationName);
            editor.putBoolean("staticStation", staticStation);
            editor.putBoolean("rememberUser", rememberUser);
            editor.putBoolean(getString(R.string.preferences_quantity_complete), quantityComplete);
            editor.putString("userPrefix", userPrefix);
            editor.putString("productPrefix", productPrefix);
            editor.putString(getString(R.string.preferences_app_id_name), appIdName);
            editor.commit();

            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }
    }
}
