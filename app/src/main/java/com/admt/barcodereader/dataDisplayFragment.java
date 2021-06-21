package com.admt.barcodereader;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;

import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import android.text.Html;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;


/**
 * The data display fragment is used to display the barcode that was
 * read by the camera, and allows the user to set the rework level
 * and send the data to the server.
 */
public class dataDisplayFragment extends Fragment
{
    private String mCurrentBarcodeValue = "";
    private TimerTask mServerCheckTimerTask = null;
    private Timer mServerCheckTimer = null;
    private onDataDisplayInteraction mDataDisplayInteracionCallback = null;
    private int mHeartbeatCounter = 0;

    // Not used in all places anymore, values got directly from text boxes, Maybe change??
    private class jobReworkData
    {
        public String jobValue;
        public String userValue;

        public jobReworkData()
        {
            jobValue = null;
            userValue = null;
        }
    }

    jobReworkData mReworkData;
    RequestQueue mRequestQueue;

    String TAG = "ADMTBarcodeReaderDataDisplay";

    public dataDisplayFragment()
    {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment dataDisplayFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static dataDisplayFragment newInstance(String param1, String param2)
    {
        dataDisplayFragment fragment = new dataDisplayFragment();
        return fragment;
    }

    public interface onDataDisplayInteraction
    {
        void onBarcodeReadHandled();
        void onBarcodeSeen();
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        mReworkData = new jobReworkData();
        mRequestQueue = Volley.newRequestQueue(getContext());
        MainActivity mainActivity = (MainActivity)getActivity();
        mDataDisplayInteracionCallback  = (onDataDisplayInteraction) mainActivity;

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_data_display, container, false);

        SharedPreferences prefs =getContext().getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);

        Button btnSend = (Button)view.findViewById(R.id.btnSend);
        btnSend.setOnClickListener(new View.OnClickListener()
        {
            // btnSend handler
            @Override
            public void onClick(View v)
            {
                onBtnSendPressed();
            }
        });

        Button btnCancel = (Button)view.findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                onBtnCancelPressed();
            }
        });

        view.setBackgroundColor(Color.WHITE);

        boolean staticStation = prefs.getBoolean(
                getString(R.string.preferences_staticStation),false);

        String stationName = prefs.getString(
                getString(R.string.preferences_station_name), "");

        Set<String> stationSet = prefs.getStringSet(
                getString(R.string.preferences_stationIds), new HashSet<String>());

        ArrayList<String> stationList = new ArrayList<>(stationSet);

        // if static station and static value present then do not update
        if (!staticStation || !stationList.contains(stationName)) {
            if (!stationList.contains(stationName)){
                stationName = null;
            }
            setStationIdSpinner(view, stationList, stationName);
        }
        else{
            ArrayList<String> arrayList = new ArrayList<>();
            arrayList.add(stationName);

            setStationIdSpinner(view, arrayList, stationName);
        }

        return view;
    }

    private void setStationIdSpinner(View view, ArrayList<String> stationList, String Value){
        Spinner spStationIdValue;
        if (view == null)
            spStationIdValue = (Spinner) (getActivity().findViewById(R.id.spStationIdValue));
        else
            spStationIdValue = (Spinner)view.findViewById(R.id.spStationIdValue);

        Context thisContext = getContext();

        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(thisContext, android.R.layout.simple_spinner_item, stationList);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spStationIdValue.setAdapter(arrayAdapter);

        int stationIndex = stationList.indexOf(Value);

        if (Value != null && (stationIndex > 0)){
            spStationIdValue.setSelection(stationIndex);
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        mRequestQueue.start();
        initServerCheckTimer();
        mServerCheckTimer = new Timer();
        mServerCheckTimer.schedule(mServerCheckTimerTask, 1000,15000);
    }

    private void initServerCheckTimer()
    {
        mServerCheckTimerTask = new TimerTask()
        {
            @Override
            public void run()
            {
                SharedPreferences preferences = getContext().getSharedPreferences(
                        getString(R.string.preferences_file_key),
                        Context.MODE_PRIVATE);

                String ipAddress = preferences.getString(
                        "serverURL","");

                if(ipAddress == "")
                {
                    // Get a handler that can be used to post to the main thread
                    Handler mainHandler = new Handler(getContext().getMainLooper());

                    Runnable runnable = new Runnable() {
                        @Override
                        public void run()
                        {
                            Toast.makeText(
                                    getContext(),
                                    "Please set server address in settings",
                                    Toast.LENGTH_LONG)
                            .show();
                        }
                    };

                    mainHandler.post(runnable);
                }
                else {
                    //check server connected by sending heartbeat, every 50 heartbeats update stations
                    if (mHeartbeatCounter <1){
                        setStations();
                    }else {
                        String serverAddress = getUrlFromIpAddress(ipAddress);
                        checkServerConnected(serverAddress);
                    }
                    mHeartbeatCounter++;
                    if (mHeartbeatCounter > 50){
                        mHeartbeatCounter = 0;
                    }
                }
            }
        };
    }

    private void checkServerConnected(String url)
    {

        if(isConnectedToWifiNetwork())
        {
            url = url + "?request=heartbeat";
            Log.d(TAG, "checkServerConnected: Attempting to get server heartbeat at " + url);

            StringRequest request = new StringRequest(Request.Method.GET, url,
                    new Response.Listener<String>()
                    {
                        @Override
                        public void onResponse(String response)
                        {
                            // got a response. Do nothing, since the server is reachable.
                            if (!response.equals("{\"status\":\"success\",\"result\":\"\"}"))
                            {
                                // Get a handler that can be used to post to the main thread
                                Handler mainHandler = new Handler(getContext().getMainLooper());

                                Runnable runnable = new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        Toast.makeText(
                                                getContext(),
                                                "Error: Unexpected response from server",
                                                Toast.LENGTH_LONG)
                                        .show();
                                    }
                                };

                                mainHandler.post(runnable);

                                Log.d(TAG, "Heartbeat: Unexpected response from server");
                            } else
                                Log.d(TAG, "Heartbeat: Server responded");
                        }

                    },
                    new Response.ErrorListener()
                    {
                        @Override
                        public void onErrorResponse(VolleyError error)
                        {
                            // Get a handler that can be used to post to the main thread
                            Handler mainHandler = new Handler(getContext().getMainLooper());

                            Runnable runnable = new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    Toast.makeText(
                                            getContext(),
                                            "Error: Unable to reach server",
                                            Toast.LENGTH_LONG)
                                    .show();
                                }
                            };
                            Log.d(TAG, "Heartbeat: Unable to reach server ");
                            mainHandler.post(runnable);
                        }
                    }
            );

            request.setRetryPolicy(new DefaultRetryPolicy(
                    5000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

            mRequestQueue.add(request);
        }
        else
        {
            // Get a handler that can be used to post to the main thread
            Handler mainHandler = new Handler(getContext().getMainLooper());

            Runnable runnable = new Runnable()
            {
                @Override
                public void run()
                {
                    Toast.makeText(
                            getContext(),
                            "Please connect device to wifi",
                            Toast.LENGTH_LONG)
                         .show();
                }
            };

            mainHandler.post(runnable);

            Log.d(TAG, "checkServerConnected: Not connected to wifi");
        }
    }

    private boolean isConnectedToWifiNetwork()
    {
        ConnectivityManager connectivityManager = (ConnectivityManager)getContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return networkInfo.isConnected();
    }

    @Override
    public void onPause()
    {
        super.onPause();
        mRequestQueue.stop();
        if(mServerCheckTimer != null)
        {
            mServerCheckTimer.cancel();
            mServerCheckTimer = null;
        }
    }

    /**
     * Sets the code displayed in the tbUserIdValue text box
     */
    void setDisplayedUserIdValue(final String newUserIdValue)
    {
        // Get a handler that can be used to post to the main thread
        Handler mainHandler = new Handler(getContext().getMainLooper());

        Runnable myRunnable = new Runnable() {
            @Override
            public void run()
            {
                TextView tbUserIdValue = (TextView) (getActivity()
                        .findViewById(R.id.tbUserIdValue));

                tbUserIdValue.setText(newUserIdValue);
            }
        };
        mainHandler.post(myRunnable);
    }

    /**
     * Sets the code displayed in the tbJobIdValue text box
     */
    void setDisplayedJobIdValue(final String newJobIdValue)
    {
        // Get a handler that can be used to post to the main thread
        Handler mainHandler = new Handler(getContext().getMainLooper());

        Runnable myRunnable = new Runnable() {
            @Override
            public void run()
            {
                TextView tbJobIdValue = (TextView) (getActivity()
                        .findViewById(R.id.tbJobIdValue));

                tbJobIdValue.setText(newJobIdValue);
            }
        };
        mainHandler.post(myRunnable);
    }

    public void UpdateDisplayedbarcodeReading(final String barcodeValue)
    {
        if(!barcodeValue.equals(mCurrentBarcodeValue))
        {
            SharedPreferences preferences = getContext().getSharedPreferences(
                    getString(R.string.preferences_file_key),
                    Context.MODE_PRIVATE);

            String userPrefix = preferences.getString(
                    "userPrefix","");

            String prefix = "";
            if(barcodeValue.length() >= 5) {
                prefix = barcodeValue.substring(0, 5);
            }

            if (prefix.equals(userPrefix))
            {
                setDisplayedUserIdValue(barcodeValue);
            }
            else
            {
                setDisplayedJobIdValue(barcodeValue);
            }

            mCurrentBarcodeValue = barcodeValue;
            mDataDisplayInteracionCallback.onBarcodeSeen();
            Vibrator v = (Vibrator)getActivity().getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(500);
        }
    }

    private void transmitData(String serverUrl)
    {
        if (serverUrl != null) {

            serverUrl = getUrlFromIpAddress(serverUrl);

            Map<String, String> params = getParameterMap();
            if (params == null)
                return;

            String uri = buildUri(serverUrl, getParameterMap());

            Log.d(TAG, "Server Address" + uri);

            setSendBtnEnabled(false);

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, uri, null,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {

                            setSendBtnEnabled(true);

                            Map<String, String> responseValues = getResponseValues(response);

                            String screenMsg;
                            String textColour = "000000";

                            if (responseValues.get("status").equals("success")) {

                                resetDisplay();

                                String responseState = responseValues.get("state");
                                if (responseState.equals("clockedOn")) {

                                    screenMsg = "Clocked ON";

                                    textColour = "96ce94";
                                } else if (responseState.equals("clockedOff")) {
                                    SharedPreferences preferences = getContext().getSharedPreferences(
                                            getString(R.string.preferences_file_key),
                                            Context.MODE_PRIVATE);

                                    boolean quantityComplete = preferences.getBoolean(
                                            getString(R.string.preferences_quantity_complete),false);

                                    if (quantityComplete)
                                        requestQuantityComplete(Integer.parseInt(responseValues.get("logRef")));

                                    screenMsg = "Clocked OFF";

                                    textColour = "949ace";
                                } else {
                                    screenMsg = responseState;
                                }

                            } else {

                                screenMsg = "Error- " + responseValues.get("result");

                                textColour = "ffffff";
                            }

                            Toast.makeText(getContext(),
                                    Html.fromHtml("<font color='#" + textColour + "' >" + screenMsg + "</font>"),
                                    Toast.LENGTH_LONG)
                                    .show();


                            //TODO: fill in response functionality


                            Log.d(TAG, "Attempted to send data" + response.toString());

                            mDataDisplayInteracionCallback.onBarcodeReadHandled();
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            handleRequestError(error);
                            mDataDisplayInteracionCallback.onBarcodeReadHandled();
                        }
                    }) {
                @Override
                public Map<String, String> getParams()//ToDo not working, replaced with buildUri method
                {
                    return getParameterMap();
                }

                @Override
                public String getBodyContentType() {
                    return "application/x-www-form-urlencoded; charset=UTF-8";
                }
            };

            Log.d(TAG, "Request URL-" + request.getUrl());
            //request.setShouldCache(false);
            mRequestQueue.add(request);
        }
    }

    private Map<String, String> getParameterMap(){
        Map<String,String> params = new HashMap<String, String>();

        SharedPreferences preferences = getContext().getSharedPreferences(
                getString(R.string.preferences_file_key),
                Context.MODE_PRIVATE);

        String stationName = preferences.getString(
                getString(R.string.preferences_station_name),"");

        String userPrefix = preferences.getString(
                getString(R.string.preferences_user_prefix),"");

        Spinner spStationIdValue = (Spinner) (getActivity().findViewById(R.id.spStationIdValue));
        EditText tbUserIdValue = (EditText) (getActivity().findViewById(R.id.tbUserIdValue));
        EditText tbJobIdValue = (EditText) (getActivity().findViewById(R.id.tbJobIdValue));
        Spinner spJobStatus = (Spinner) (getActivity().findViewById(R.id.spJobStatus));

        String stationIdValue = "";
        if (spStationIdValue.getSelectedItem() != null)
            stationIdValue = spStationIdValue.getSelectedItem().toString();

        String userIdValue = tbUserIdValue.getText().toString();

        // if user prefix absent add it, this allows only number section to be entered
        if((userIdValue.length() < 5) || !(userIdValue.substring(0,5).equals(userPrefix)))
            userIdValue = userPrefix + userIdValue;

        String jobIdValue = tbJobIdValue.getText().toString();

        String jobStatus = spJobStatus.getSelectedItem().toString();
        jobStatus = getSystemWorkStatus(jobStatus);

        if (userIdValue.equals("") || jobIdValue.equals("") || stationIdValue.equals("")) {
            params = null;

            String missingCode = "";

            if (stationIdValue.equals("")) {
                Toast.makeText(getContext(),
                        Html.fromHtml("<font color='#" + "ffffff" + "' >" +
                                "Station Id Missing" + "</font>"),
                        Toast.LENGTH_LONG)
                        .show();
            }
            else{
                if (userIdValue.equals("") && jobIdValue.equals(""))
                    missingCode = "User & Job ID";
                else {
                    if (userIdValue.equals(""))
                        missingCode = "User ID";
                    else
                        missingCode = "Job ID";
                }

                Toast.makeText(getContext(),
                    Html.fromHtml("<font color='#" + "ffffff" + "' >" +
                            "Scan " + missingCode + "</font>"),
                    Toast.LENGTH_LONG)
                    .show();
            }
        }else {

            params.put("request", "clockUser");
            params.put("jobId", jobIdValue);//mReworkData.jobValue); // TODO
            params.put("userId", userIdValue);//mReworkData.userValue);
            params.put("stationId", stationIdValue);
            params.put("jobStatus", jobStatus); //TODO //stageComplete

            Log.d(TAG, "Parameters- " + params);
        }

        return params;
    }

    private String buildUri(String serverAddress, Map<String, String> params){

        String builtUri = null;

        if (params != null) {
            Uri.Builder builder = Uri.parse(serverAddress).buildUpon();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                builder.appendQueryParameter(entry.getKey(), entry.getValue());
            }

            builtUri = builder.build().toString();
        }

        return builtUri;
    }

    // convert user chosen work status to the format required by server
    private String getSystemWorkStatus(String workStatus){
        String systemWorkStatus = "";

        String[] status_list = getResources().getStringArray(R.array.list_work_status);
        String[] status_list_values = getResources().getStringArray(R.array.list_work_status_values);

        int index = -1;
        for (int i=0;i<status_list.length;i++) {
            if (status_list[i].equals(workStatus)) {
                index = i;
                break;
            }
        }

        if (index < 0)
        {
            systemWorkStatus = "unknown";
        }
        else {
            systemWorkStatus = status_list_values[index];
        }

        return systemWorkStatus;
    }

    private Map<String, String> getResponseValues(JSONObject serverResponse)
    {
        Map<String, String> responseValues = new HashMap<String, String>();

        String status = "";
        try {
            status = serverResponse.getString("status");

            responseValues.put("status", status);

            if (status.equals("success")) {

                JSONObject jsonResult = serverResponse.getJSONObject("result");

                responseValues.put("state", jsonResult.getString("state"));

                responseValues.put("logRef", jsonResult.getString("logRef"));
            }
            else{
                responseValues.put("result", serverResponse.getString("result"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Log.d(TAG, "Error- Server response to clock request not valid");
        }

        Log.d(TAG, "getResponseValues-responseValues: " + responseValues);

        return responseValues;
    }

    private void onBtnSendPressed()
    {
        // Todo
//        if (mReworkData.userValue == null)
//        {
//            Toast.makeText(getContext(),
//                    "Please scan a user code", Toast.LENGTH_SHORT).show();
//            return;
//        }

        SharedPreferences preferences = getContext().getSharedPreferences(
                getString(R.string.preferences_file_key),
                Context.MODE_PRIVATE);

        String serverAddress = preferences.getString(
                "serverURL","");

        if(serverAddress == "")
        {
            Toast.makeText(getContext(),
                    "Please enter server address in the settings",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        else
            transmitData(serverAddress);
    }

    private void onBtnCancelPressed()
    {
        resetDisplay();
        mDataDisplayInteracionCallback.onBarcodeReadHandled();
    }

    void resetDisplay()
    {
        SharedPreferences preferences = getContext().getSharedPreferences(
                getString(R.string.preferences_file_key),
                Context.MODE_PRIVATE);

        Boolean rememberUserPref = preferences.getBoolean(
                "rememberUser",false);

        if (!rememberUserPref){
            setDisplayedUserIdValue("");
        }

        setDisplayedJobIdValue("");

        Spinner spJobStatus = (Spinner) (getActivity().findViewById(R.id.spJobStatus));
        spJobStatus.setSelection(0);

        mReworkData.jobValue = null;
        mReworkData.userValue = null;
        mCurrentBarcodeValue = null;

        ConstraintLayout clDataDisplayLayout = (ConstraintLayout)
                (getActivity().findViewById(R.id.clDataDisplayLayout));

        clDataDisplayLayout.requestFocus();

        setSendBtnEnabled(true);
    }

    public String getUrlFromIpAddress(String ipAddress){
        String path = getResources().getString(R.string.server_client_input_path);
        return ("http://" + ipAddress + path);
    }

    public void setStations(){

        SharedPreferences preferences = getContext().getSharedPreferences(
                getString(R.string.preferences_file_key),
                Context.MODE_PRIVATE);
        String ipAddress = preferences.getString(
                "serverURL","");

        if(ipAddress == "")
        {
            Toast.makeText(getContext(),
                    "Please enter server address in the settings",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        else{

            String path = getResources().getString(R.string.server_stations_path);

            String serverUrl = "http://" + ipAddress + path;

            Map<String,String> params = new HashMap<String, String>();
            params.put("request", "getAllScannerNames");

            Log.d(TAG, "Parameters- " + params);

            String uri = buildUri(serverUrl, params);

            Log.d(TAG, "Server Address" + uri);

            Log.d(TAG, "*****************************************************");

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, uri, null,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            Log.d(TAG, "^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^: ");

                            String status = "";

                            try {
                                status = response.getString("status");
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                            if (status.equals("success")) {
                                try {
                                    
                                    JSONArray jsonStations = response.getJSONArray("result");

                                    JSONObject stationArray;
                                    ArrayList<String> stationList = new ArrayList<>();

                                    Log.d(TAG, "Got updated Station list");
                                    for (int i = 0, size = jsonStations.length(); i < size; i++)
                                    {
                                        //stationArray = jsonStations.getJSONObject(i);
                                        //stationList.add(stationArray.getString("stationId"));
                                        stationList.add(jsonStations.getString(i));
                                    }

                                    SharedPreferences prefs = getContext().getSharedPreferences(
                                            getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
                                    SharedPreferences.Editor editor = prefs.edit();

                                    Set stationSet = new HashSet(stationList);
                                    editor.putStringSet(getString(R.string.preferences_stationIds), stationSet);
                                    editor.apply();

                                    boolean staticStation = prefs.getBoolean(
                                            getString(R.string.preferences_staticStation),false);

                                    String stationName = prefs.getString(
                                            getString(R.string.preferences_station_name), "");

                                    // if static station and static value present then do not update
                                    if (!staticStation || !stationList.contains(stationName)) {
                                        Spinner spStationIdValue = (Spinner) (getActivity().findViewById(R.id.spStationIdValue));

                                        //get current value if present
                                        String stationIdValue = null;
                                        if (spStationIdValue.getSelectedItem() != null)
                                            stationIdValue = spStationIdValue.getSelectedItem().toString();

                                        setStationIdSpinner(null, stationList, stationIdValue);


                                    }

                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }

                            } else {

                                String screenMsg = null;
                                try {
                                    screenMsg = "Error- " + response.getString("result");
                                } catch (JSONException e) {
                                    e.printStackTrace();

                                    screenMsg = "Error requesting stations";
                                }

                                Toast.makeText(getContext(),
                                        screenMsg,
                                        Toast.LENGTH_LONG)
                                        .show();
                                Log.d(TAG, "$$ Error requesting stations- "+ screenMsg);
                            }

                            //TODO: fill in response functionality

                            Log.d(TAG, "Attempted to retrieve stations" + response.toString());

                            mDataDisplayInteracionCallback.onBarcodeReadHandled();
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            handleRequestError(error);
                        }
                    }) {
                @Override
                public Map<String, String> getParams()//ToDo not working, replaced with buildUri method
                {
                    return null;
                }

                @Override
                public String getBodyContentType() {
                return "application/x-www-form-urlencoded; charset=UTF-8";
            }
            };
            Log.d(TAG, "Request URL-" + request.getUrl());
            //request.setShouldCache(false);
            mRequestQueue.add(request);
        }
    }

    private void setSendBtnEnabled(boolean enabled)
    {
        Button btnSend = (Button)getActivity().findViewById(R.id.btnSend);
        btnSend.setEnabled(enabled);
    }

    // Ask for number of items completed while clocked on and send input to server
    private void requestQuantityComplete(final int timelogRef)
    {
        Context thisContext = getContext();

        AlertDialog.Builder builder = new AlertDialog.Builder(thisContext);
        builder.setTitle(getString(R.string.quantity_complete_alert_message));

// Set up the input
        final EditText input = new EditText(thisContext);
// Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setGravity(Gravity.CENTER);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // on submit send input to server
                int quantityComplete = Integer.parseInt(input.getText().toString());
                transmitQuantityComplete(timelogRef, quantityComplete);
            }
        });
        builder.setNegativeButton("Skip", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    private void transmitQuantityComplete(int timelogRef, int quantityComplete){

        SharedPreferences preferences = getContext().getSharedPreferences(
                getString(R.string.preferences_file_key),
                Context.MODE_PRIVATE);

        String ipAddress = preferences.getString(
                "serverURL","");

        if(ipAddress != "")
        {
            String path = getResources().getString(R.string.server_client_input_path);

            String serverUrl = "http://" + ipAddress + path;

            Map<String,String> params = new HashMap<String, String>();
            params.put("request", "recordNumberCompleted");
            params.put("logRef", Integer.toString(timelogRef));
            params.put("numberCompleted", Integer.toString(quantityComplete));

            Log.d(TAG, "Parameters- " + params);

            String uri = buildUri(serverUrl, params);

            Log.d(TAG, "Server Address" + uri);


            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, uri, null,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {

                            String result = "";

                            try {
                                result = response.getString("status");
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                            String screenMsg = "";

                            if (result.equals("success")) {
                                screenMsg = "Quantity Recorded";

                            } else {

                                screenMsg = "ERROR Recording Quantity";
                            }

                            Toast.makeText(getContext(),
                                    screenMsg,
                                    Toast.LENGTH_LONG)
                                    .show();
                            Log.d(TAG, screenMsg);

                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            handleRequestError(error);
                        }
                    }) {
                @Override
                public Map<String, String> getParams()//ToDo not working, replaced with buildUri method
                {
                    return null;
                }

                @Override
                public String getBodyContentType() {
                    return "application/x-www-form-urlencoded; charset=UTF-8";
                }
            };
            Log.d(TAG, "Request URL-" + request.getUrl());
            //request.setShouldCache(false);
            mRequestQueue.add(request);
        }

    }

    public void handleRequestError(VolleyError error){
        if (error.networkResponse == null) {

            if (error.getClass().equals(TimeoutError.class)) {
                Toast.makeText(getContext(),
                        "Error: No response from server",
                        Toast.LENGTH_LONG)
                        .show();
                Log.d(TAG, "$$onErrorReponse: Sent data to server. No response");
            }else{
                Toast.makeText(getContext(),
                        "Error: Network response",
                        Toast.LENGTH_LONG)
                        .show();

                Log.d(TAG, "$$error.networkResponse == null- " + error.getMessage());

            }
        } else {
            Log.d(TAG, "$$onErrorResponse: " + error.getMessage());
            Toast.makeText(getContext(),
                    "An error occured. Try again.",
                    Toast.LENGTH_LONG)
                    .show();
        }

        setSendBtnEnabled(true);
    }
}
