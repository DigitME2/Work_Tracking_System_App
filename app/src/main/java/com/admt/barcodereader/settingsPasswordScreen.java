package com.admt.barcodereader;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class settingsPasswordScreen extends AppCompatActivity
{

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_password_screen);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        EditText tbPassword = (EditText)findViewById(R.id.etPassword);

        tbPassword.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) || (actionId == EditorInfo.IME_ACTION_DONE)) {
                    return submitPassword();
                }
                return false;
            }
        });
    }

    public void onOkBtnPress(View view)
    {
        submitPassword();
    }

    private boolean submitPassword(){
        boolean successResult = false;

        SharedPreferences prefs = getSharedPreferences(getString(R.string.preferences_file_key),
                Context.MODE_PRIVATE);

        EditText tbPassword = (EditText)findViewById(R.id.etPassword);
        String enteredPassword = tbPassword.getText().toString();
        String storedPassword = prefs.getString(
                getString(R.string.preferences_password),"1234");

        if(enteredPassword.equals(storedPassword))
        {
            successResult = true;
            Intent intent = new Intent(this, settings_page.class);
            startActivity(intent);
        }
        else
        {
            successResult = false;
            tbPassword.setText("");
            Toast.makeText(getApplicationContext(),"Password incorrect",Toast.LENGTH_SHORT)
                    .show();
        }

        return successResult;
    }
}
