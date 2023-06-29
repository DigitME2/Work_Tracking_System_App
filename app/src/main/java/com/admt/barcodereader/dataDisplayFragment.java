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
import android.os.Looper;
import android.os.Vibrator;


import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
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
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;


/**
 * The data display fragment is used to display the barcode that was
 * read by the camera, and allows the user to set the rework level
 * and send the data to the server.
 */
public class dataDisplayFragment extends Fragment {
    private String mCurrentBarcodeValue = "";
    private TimerTask mServerCheckTimerTask = null;
    private Timer mServerCheckTimer = null;
    private onDataDisplayInteraction mDataDisplayInteractionCallback = null;
    private int mHeartbeatCounter = 0;
    private boolean mIsInStoppageMode = false;
    private HashMap<String, String> mStoppageReasons;
    private String mStoppageId = null;
    private TreeMap<String, String> mJobIdtoJobNameMap;
    private String mJobId = null;
    private TreeMap<String, String> mJobNametoJobIdMap;
    private TreeMap<String, String> mUserIdtoUserNameMap;
    private String mUserId = null;
    private TreeMap<String, String> mUserNametoUserIdMap;

    private Timer mGetUserStatusTimer = null;
    private TimerTask mGetUserStatusTimerTask = null;
    private SharedPreferences.Editor editor;
    private Spinner spinner;

    // Not used in all places anymore, values got directly from text boxes, Maybe change??
    private class jobReworkData {
        public String jobValue;
        public String userValue;

        public jobReworkData() {
            jobValue = null;
            userValue = null;
        }
    }

    jobReworkData mReworkData;
    RequestQueue mRequestQueue;

    String TAG = "ADMTBarcodeReaderDataDisplay";

    public dataDisplayFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment dataDisplayFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static dataDisplayFragment newInstance(String param1, String param2) {
        dataDisplayFragment fragment = new dataDisplayFragment();
        return fragment;
    }

    public interface onDataDisplayInteraction {
        void onBarcodeReadHandled();

        void onBarcodeSeen();

        void onClockedOn(String JobId);

        void onClockedOff(String JobId);

        void onStoppageStart(String JobId, String StoppageReason);

        void onStoppageEnd(String JobId, String StoppageReason);
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mReworkData = new jobReworkData();
        mRequestQueue = Volley.newRequestQueue(getContext());
        MainActivity mainActivity = (MainActivity) getActivity();
        mDataDisplayInteractionCallback = (onDataDisplayInteraction) mainActivity;
        mStoppageReasons = new HashMap<String, String>();
        mJobIdtoJobNameMap = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        mJobNametoJobIdMap = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        mUserIdtoUserNameMap = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        mUserNametoUserIdMap = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        mGetUserStatusTimerTask = new TimerTask() {
            @Override
            public void run() {
                if (mIsInStoppageMode) {
                    return;
                }
                try {
                    EditText etUserID = (EditText) getActivity().findViewById(R.id.tbUserIdValue);
                    String UserID = etUserID.getText().toString();
                    UserID = UserID.trim();
                    updateUserStatusIndicator(UserID);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        mGetUserStatusTimer = new Timer();
        mGetUserStatusTimer.schedule(mGetUserStatusTimerTask, 1000, 5000);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        View view = inflater.inflate(R.layout.fragment_data_display, container, false);

        SharedPreferences prefs = getContext().getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);

        Button btnSend = (Button) view.findViewById(R.id.btnSend);
        btnSend.setOnClickListener(new View.OnClickListener() {
            // btnSend handler
            @Override
            public void onClick(View v) {
                onBtnSendPressed();
             }
        });

        Button btnCancel = (Button) view.findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnCancelPressed();
            }
        });



        EditText ettbUserIdValue = (EditText) view.findViewById(R.id.tbUserIdValue);
        ettbUserIdValue.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable editable) {

                if(!mIsInStoppageMode || mStoppageId != null) {
                    try {
                        String UserId = editable.toString();
                        UserId = UserId.trim();
                        updateUserStatusIndicator(UserId);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            }
        });


        SharedPreferences preferences = getContext().getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        if (preferences.getBoolean("enableUserStatus", true)) {
            ConstraintLayout clUserStatusContainer = (ConstraintLayout) view.findViewById(R.id.clUserStatusContainer);
            clUserStatusContainer.setVisibility(View.VISIBLE);
        }
        else{
            ConstraintLayout clUserStatusContainer = (ConstraintLayout) view.findViewById(R.id.clUserStatusContainer);
            clUserStatusContainer.setVisibility(View.GONE);
        }

        view.setBackgroundColor(Color.WHITE);


        boolean staticStation = prefs.getBoolean(
                getString(R.string.preferences_staticStation), false);

        boolean rememberStation = prefs.getBoolean(
                "rememberStation", false);
        String stationName = "";

        if(staticStation){
            stationName = prefs.getString(getString(R.string.preferences_station_name), "");
        }else if(rememberStation){
            stationName = prefs.getString(getString(R.string.lastSeenStationName), "");
        }

        Set<String> stationSet = prefs.getStringSet(
                getString(R.string.preferences_stationIds), new HashSet<String>());

        ArrayList<String> stationList = new ArrayList<>(stationSet);

        // if static station and static value present then do not update
        if (!staticStation || !stationList.contains(stationName)) {
            if (!stationList.contains(stationName)) {
                stationName = null;
            }
            setStationIdSpinner(view, stationList, stationName);

        } else {
            ArrayList<String> arrayList = new ArrayList<>();
            arrayList.add(stationName);

            setStationIdSpinner(view, arrayList, stationName);
        }
        return view;
    }

    private void setStationIdSpinner(View view, ArrayList<String> stationList, String Value) {
        Spinner spStationIdValue;
        if (view == null)
            spStationIdValue = (Spinner) (getActivity().findViewById(R.id.spStationIdValue));
        else
            spStationIdValue = (Spinner) view.findViewById(R.id.spStationIdValue);

        Context thisContext = getContext();

        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(thisContext, android.R.layout.simple_spinner_item, stationList);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        spStationIdValue.setAdapter(arrayAdapter);

        int stationIndex = stationList.indexOf(Value);

        if (Value != null && (stationIndex > 0)) {
            spStationIdValue.setSelection(stationIndex);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Spinner spinner = (Spinner) getActivity().findViewById(R.id.spStationIdValue);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long l) {
                String locationName = (String) parent.getItemAtPosition(position);
                SharedPreferences prefs = getContext().getSharedPreferences(getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(getString(R.string.lastSeenStationName), locationName);
                editor.commit();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                //Do nothing;
            }
        });

    }

    @Override
    public void onResume() {
        super.onResume();
        mRequestQueue.start();
        initServerCheckTimer();
        mServerCheckTimer = new Timer();
        mServerCheckTimer.schedule(mServerCheckTimerTask, 1000, 5000);
    }

    private void initServerCheckTimer() {
        mServerCheckTimerTask = new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "run: checking server connectivity");
                SharedPreferences preferences = getContext().getSharedPreferences(
                        getString(R.string.preferences_file_key),
                        Context.MODE_PRIVATE);

                String ipAddress = preferences.getString(
                        getString(R.string.prefs_server_base_address), "");

                if (ipAddress == "") {
                    // Get a handler that can be used to post to the main thread
                    Handler mainHandler = new Handler(getContext().getMainLooper());

                    Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(
                                            getContext(),
                                            "Please set server address in settings",
                                            Toast.LENGTH_LONG)
                                    .show();
                        }
                    };

                    mainHandler.post(runnable);
                } else {
                    //check server connected by sending heartbeat, every 10 heartbeats update stations
                    if (mHeartbeatCounter == 0) {
                        setStations();
                        getStoppageReasons();
                        getUserNames();
                        getJobNamesForIds();
                        getJobIdsForNames();
                        getUserIdsForUserNames();
                    } else {
                        String serverAddress = getUrlFromIpAddress(ipAddress);
                        checkServerConnected(serverAddress);
                    }
                    mHeartbeatCounter++;
                    if (mHeartbeatCounter > 3) {
                        mHeartbeatCounter = 0;
                    }
                }
            }
        };
    }

    private void checkServerConnected(String url) {
        if (isConnectedToWifiNetwork()) {
            SharedPreferences prefs = getContext().getSharedPreferences(
                    getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
            String appVersion = getString(R.string.app_version);

            Spinner spStationIdValue = (Spinner) (getActivity().findViewById(R.id.spStationIdValue));
            // the station location is used by default, and the display name is used as a fallback
            // This might need reworking, but let's see how it goes
            String appIdentifierName = "";
            String nameType = "location";
            if (spStationIdValue.getSelectedItem() != null)
                appIdentifierName = spStationIdValue.getSelectedItem().toString();

            if (appIdentifierName == "") {
                appIdentifierName = prefs.getString(
                        getString(R.string.preferences_app_id_name),
                        getString(R.string.default_app_id_name)
                );
                nameType = "appId";
            }
            url = url + "?request=heartbeat&stationId=" + appIdentifierName +
                    "&isApp=true&version=" + appVersion +
                    "&nameType=" + nameType;


            Log.d(TAG, "checkServerConnected: Attempting to get server heartbeat at " + url);

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            // got a response. Do nothing, since the server is reachable.
                            String status = "";
                            try {
                                status = response.getString("status");
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            if (!(status.equals("success")))
                            {
                                // Get a handler that can be used to post to the main thread
                                Handler mainHandler = new Handler(getContext().getMainLooper());

                                Runnable runnable = new Runnable() {
                                    @Override
                                    public void run() {
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
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            // Get a handler that can be used to post to the main thread
                            Handler mainHandler = new Handler(getContext().getMainLooper());

                            Runnable runnable = new Runnable() {
                                @Override
                                public void run() {
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
        } else {
            // Get a handler that can be used to post to the main thread
            Handler mainHandler = new Handler(getContext().getMainLooper());

            Runnable runnable = new Runnable() {
                @Override
                public void run() {
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

    private boolean isConnectedToWifiNetwork() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return networkInfo.isConnected();
    }

    @Override
    public void onPause() {
        super.onPause();
        mRequestQueue.stop();
        if (mServerCheckTimer != null) {
            mServerCheckTimer.cancel();
            mServerCheckTimer = null;
        }
    }

    private void setJobStatusOptions(ArrayList<String> optionList) {
        Spinner spinner;
        spinner = (Spinner) (getActivity().findViewById(R.id.spJobStatus));

        Context thisContext = getContext();

        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(
                thisContext, android.R.layout.simple_spinner_item, optionList);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(arrayAdapter);

        spinner.setSelection(0);
    }

    /**
     * Sets the code displayed in the tbUserIdValue text box
     */
    void setDisplayedUserIdValue(final String newUserNameValue, String newUserIdValue) {
        mIsInStoppageMode = false;
        // Get a handler that can be used to post to the main thread
        Handler mainHandler = new Handler(getContext().getMainLooper());

        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                ArrayList<String> statusNames = new ArrayList<>(
                        Arrays.asList(getResources().getStringArray(R.array.list_work_status))
                );
                setJobStatusOptions(statusNames);
                TextView tbUserIdValue = (TextView) (getActivity()
                        .findViewById(R.id.tbUserIdValue));

                TextView tvUserIdLabel = (TextView) (getActivity().findViewById(R.id.tvUserIdLable));
                String userIdLabel = "User:";
                tvUserIdLabel.setText(userIdLabel);

                SharedPreferences preferences = getContext().getSharedPreferences(
                        getString(R.string.preferences_file_key),
                        Context.MODE_PRIVATE);

                if (preferences.getBoolean("enableUserStatus", true)){
                    ConstraintLayout clUserStatusContainer = (ConstraintLayout) getActivity().findViewById(R.id.clUserStatusContainer);

                    clUserStatusContainer.setVisibility(View.VISIBLE);

                    TextView tvUserStatus = (TextView) getActivity().findViewById(R.id.tvUserStatus);
                    TextView tvUserStatusJobId = (TextView) getActivity().findViewById(R.id.tvUserStatusJobId);
                    TextView tvUserStatusProductId = (TextView) getActivity().findViewById(R.id.tvUserStatusProductId);
                    TextView tvUserStatusStationId = (TextView) getActivity().findViewById(R.id.tvUserStatusStationId);
                    String userStatus = "Loading...";
                    tvUserStatus.setText(userStatus);
                    tvUserStatus.setBackgroundColor(getResources().getColor(R.color.blankUSerStatusBackgroundColour));
                    tvUserStatus.setTextColor(getResources().getColor(R.color.blankUserStatusTextColour));

                    tvUserStatusJobId.setText("");
                    tvUserStatusJobId.setBackgroundColor(getResources().getColor(R.color.blankUSerStatusBackgroundColour));
                    tvUserStatusJobId.setTextColor(getResources().getColor(R.color.blankUserStatusTextColour));

                    tvUserStatusProductId.setText("");
                    tvUserStatusProductId.setBackgroundColor(getResources().getColor(R.color.blankUSerStatusBackgroundColour));
                    tvUserStatusProductId.setTextColor(getResources().getColor(R.color.blankUserStatusTextColour));

                    tvUserStatusStationId.setText("");
                    tvUserStatusStationId.setBackgroundColor(getResources().getColor(R.color.blankUSerStatusBackgroundColour));
                    tvUserStatusStationId.setTextColor(getResources().getColor(R.color.blankUserStatusTextColour));
                }
                else
                {
                    ConstraintLayout clUserStatusContainer = (ConstraintLayout) getActivity().findViewById(R.id.clUserStatusContainer);
                    clUserStatusContainer.setVisibility(View.GONE);
                }

                tbUserIdValue.setText(newUserNameValue);
                updateUserStatusIndicator(newUserNameValue);

                TextView tvStoppageLabel = (TextView) getActivity().findViewById(R.id.tvStoppageDescriptionLabel);
                EditText etStoppageDesc = (EditText) getActivity().findViewById(R.id.etStoppageDescription);

                tvStoppageLabel.setVisibility(View.GONE);
                etStoppageDesc.setVisibility(View.GONE);
                etStoppageDesc.setText("");
            }
        };
        mainHandler.post(myRunnable);
    }

    /**
     * Sets the code displayed in the tbJobIdValue text box, if starts with 'job_'
     */
    void setDisplayedJobIdValue(final String newJobNameValue) {
        // Get a handler that can be used to post to the main thread
        Handler mainHandler = new Handler(getContext().getMainLooper());

        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                TextView tbJobIdValue = (TextView) (getActivity()
                        .findViewById(R.id.tbJobIdValue));

                tbJobIdValue.setText(newJobNameValue);
            }
        };
        mainHandler.post(myRunnable);
    }

    /**
     * Sets the code displayed in the tbJobIdValue text box, if starts with 'pdrt_'
     */
    void setDisplayedProductIdValue(final String newProductValue) {
        // Get a handler that can be used to post to the main thread
        Handler mainHandler = new Handler(getContext().getMainLooper());

        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                TextView tbJobIdValue = (TextView) (getActivity()
                        .findViewById(R.id.tbJobIdValue));

                tbJobIdValue.setText(newProductValue);
            }
        };
        mainHandler.post(myRunnable);
    }

    void setDisplayedStoppageName(final String newStoppageName) {
        // Get a handler that can be used to post to the main thread
        Handler mainHandler = new Handler(getContext().getMainLooper());

        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                ArrayList<String> statusNames = new ArrayList<>(
                        Arrays.asList(getResources().getStringArray(R.array.stoppage_status))
                );
                setJobStatusOptions(statusNames);

                SharedPreferences preferences = getContext().getSharedPreferences(
                        getString(R.string.preferences_file_key),
                        Context.MODE_PRIVATE);

                TextView tvUserIdLabel = (TextView) (getActivity().findViewById(R.id.tvUserIdLable));
                String stoppageLabel = "Problem:";
                tvUserIdLabel.setText(stoppageLabel);

                TextView tbstoppageIdValue = (TextView) (getActivity()
                        .findViewById(R.id.tbUserIdValue)); /* for speed, this is being left as
                                                                "tbUserId", and just changed on
                                                                the UI"
                                                                */

                tbstoppageIdValue.setText(newStoppageName);

                if (preferences.getBoolean("allowStoppageDescription", true)) {
                    TextView tvStoppageLabel = (TextView) getActivity().findViewById(R.id.tvStoppageDescriptionLabel);
                    EditText etStoppageDesc = (EditText) getActivity().findViewById(R.id.etStoppageDescription);
                    ConstraintLayout clUserStatusContainer = (ConstraintLayout) getActivity().findViewById(R.id.clUserStatusContainer);

                    clUserStatusContainer.setVisibility(View.GONE);
                    tvStoppageLabel.setVisibility(View.VISIBLE);
                    etStoppageDesc.setVisibility(View.VISIBLE);
                }
            }
        };
        mainHandler.post(myRunnable);
    }

    public void UpdateDisplayedbarcodeReading(final String barcodeValue) {
        // note that "barcode" might refer to a QR code or other type
        if (!barcodeValue.equals(mCurrentBarcodeValue)) {
            Looper myLooper = Looper.myLooper();
            SharedPreferences preferences = getContext().getSharedPreferences(
                    getString(R.string.preferences_file_key),
                    Context.MODE_PRIVATE);

            String userPrefix = preferences.getString(
                    "userPrefix", "user_");

            String stoppagePrefix = preferences.getString(
                    "stoppagePrefix", "stpg_");

            String jobPrefix = preferences.getString(
                    "jobPrefix", "job_");

            String productPrefix = preferences.getString(
                    "productPrefix", "pdrt_");

            String prefix = "";
            if (barcodeValue.startsWith("job_")){
                prefix = barcodeValue.substring(0, 4);
            } else {
                if (barcodeValue.length() >= 5) {
                    prefix = barcodeValue.substring(0, 5);
                }
            }

            if (prefix.equals(userPrefix)) {
                mIsInStoppageMode = false;
                mStoppageId = null;
                mUserId = barcodeValue;
                String UserIdValue = (mUserId);
                if (mUserIdtoUserNameMap.containsKey(mUserId)) {
                    String UserNameValue = mUserIdtoUserNameMap.get(barcodeValue);
                    setDisplayedUserIdValue(UserNameValue, UserIdValue);
                } else {
                    Handler mainHandler = new Handler(getContext().getMainLooper());

                    Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getContext(), "Unknown User ID", Toast.LENGTH_LONG).show();
                            onBtnCancelPressed();
                        }
                    };
                    mainHandler.post(runnable);
                }
            } else if (prefix.equals(stoppagePrefix)) {
                String stoppageReason = mStoppageReasons.get(barcodeValue);
                if (stoppageReason != null) {
                    mIsInStoppageMode = true;
                    mStoppageId = barcodeValue;
                    setDisplayedStoppageName(stoppageReason);
                } else {
                    // Get a handler that can be used to post to the main thread
                    Handler mainHandler = new Handler(getContext().getMainLooper());

                    Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getContext(), "Unknown Problem ID", Toast.LENGTH_LONG).show();
                            onBtnCancelPressed();
                        }
                    };

                    mainHandler.post(runnable);
                }

            } else if (prefix.equals(jobPrefix)) {
                String jobName = mJobIdtoJobNameMap.get(barcodeValue);
                if (jobName != null) {
                    mJobId = barcodeValue;
                    setDisplayedJobIdValue(jobName);
                } else {
                    Handler mainHandler = new Handler(getContext().getMainLooper());

                    Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getContext(), "Unknown Job ID", Toast.LENGTH_LONG).show();
                            onBtnCancelPressed();
                        }
                    };
                    mainHandler.post(runnable);
                }
            }
            else if (prefix.equals(productPrefix)){
                String ProductIdValue = barcodeValue;
                setDisplayedProductIdValue(ProductIdValue);
            }

            mCurrentBarcodeValue = barcodeValue;
            mDataDisplayInteractionCallback.onBarcodeSeen();
            if (preferences.getBoolean("enableVibration", true)) {
                Vibrator v = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
                v.vibrate(500);
            }
        }
    }

    private void transmitData(String serverUrl) {
        if (serverUrl != null) {

            serverUrl = getUrlFromIpAddress(serverUrl);

            final Map<String, String> params = getParameterMap();
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

                            String screenMsg = "";
                            String textColour = "000000";

                            SharedPreferences preferences = getContext().getSharedPreferences(
                                    getString(R.string.preferences_file_key),
                                    Context.MODE_PRIVATE);
                            Boolean useFullscreenConfirmations =
                                    preferences.getBoolean("useFullscreenConfirmations", true);

                            if (responseValues.get("status").equals("success")) {

                                resetDisplay();

                                String responseState = null;
                                if (responseValues.containsKey("state"))
                                    responseState = responseValues.get("state");
                                else if (responseValues.containsKey("result"))
                                    responseState = responseValues.get("result");

                                if (responseState.equals("clockedOn")) {
                                    if (useFullscreenConfirmations)
                                        mDataDisplayInteractionCallback.onClockedOn(params.get("jobId"));
                                    else {
                                        screenMsg = "Clocked ON";
                                        textColour = "96ce94";
                                    }
                                } else if (responseState.equals("clockedOff")) {
                                    boolean quantityComplete = preferences.getBoolean(
                                            getString(R.string.preferences_quantity_complete), false);

                                    if (quantityComplete)
                                        requestQuantityComplete(Integer.parseInt(responseValues.get("logRef")));

                                    if (useFullscreenConfirmations) {
                                        if (quantityComplete) {
                                            requestQuantityComplete(Integer.parseInt(responseValues.get("logRef")));
                                            mDataDisplayInteractionCallback.onClockedOff(params.get("jobId"));
                                        }else {
                                            mDataDisplayInteractionCallback.onClockedOff(params.get("jobId"));
                                        }
                                    }
                                    else {
                                        if (quantityComplete)
                                            requestQuantityComplete(Integer.parseInt(responseValues.get("logRef")));
                                        screenMsg = "Clocked OFF";
                                        textColour = "949ace";
                                    }

                                } else if (responseState.equals("stoppageOn")) {
                                    if (useFullscreenConfirmations)
                                        mDataDisplayInteractionCallback.onStoppageStart(
                                                params.get("jobId"),
                                                mStoppageReasons.get(params.get("stoppageId"))
                                        );
                                    else {
                                        textColour = "ffffff";
                                        screenMsg = "Stoppage Start Recorded";
                                    }
                                } else if (responseState.equals("stoppageOff")) {
                                    if (useFullscreenConfirmations)
                                        mDataDisplayInteractionCallback.onStoppageEnd(
                                                params.get("jobId"),
                                                mStoppageReasons.get(params.get("stoppageId"))
                                        );
                                    else {
                                        textColour = "ffffff";
                                        screenMsg = "Stoppage Resolved";
                                    }
                                } else {
                                    screenMsg = responseState;
                                }
                            } else {
                                screenMsg = "Error- " + responseValues.get("result");
                                textColour = "ffffff";
                            }

                            if (screenMsg != "") {
                                Toast.makeText(getContext(),
                                                Html.fromHtml("<font font-size='200%' color='#" + textColour + "' >" + screenMsg + "</font>"),
                                                Toast.LENGTH_LONG)
                                        .show();
                            }

                            //TODO: fill in response functionality


                            Log.d(TAG, "Attempted to send data" + response.toString());

                            mDataDisplayInteractionCallback.onBarcodeReadHandled();
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            handleRequestError(error);
                            mDataDisplayInteractionCallback.onBarcodeReadHandled();
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

    private Map<String, String> getParameterMap() {
        Map<String, String> params = new HashMap<String, String>();

        SharedPreferences preferences = getContext().getSharedPreferences(
                getString(R.string.preferences_file_key),
                Context.MODE_PRIVATE);

        String stationName = preferences.getString(
                getString(R.string.preferences_station_name), "");

        String userPrefix = preferences.getString(
                getString(R.string.preferences_user_prefix), "user_");

        String jobPrefix = preferences.getString(
                getString(R.string.preferences_job_prefix), "job_");

        Spinner spStationIdValue = (Spinner) (getActivity().findViewById(R.id.spStationIdValue));
        EditText tbUserIdValue = (EditText) (getActivity().findViewById(R.id.tbUserIdValue));
        EditText tbJobIdValue = (EditText) (getActivity().findViewById(R.id.tbJobIdValue));
        Spinner spJobStatus = (Spinner) (getActivity().findViewById(R.id.spJobStatus));

        String stationIdValue = "";
        if (spStationIdValue.getSelectedItem() != null)
            stationIdValue = spStationIdValue.getSelectedItem().toString();

        String userIdValue = null;

        if (mIsInStoppageMode == false) {
            userIdValue = tbUserIdValue.getText().toString();
            userIdValue = userIdValue.trim();
            if (userIdValue.startsWith("00")) {    //todo: string to be changed regex
                userIdValue = "user_" + userIdValue;
            }
            if (!userIdValue.startsWith("user_")) {
                userIdValue = mUserNametoUserIdMap.get(userIdValue);
            }
        }

        String jobIdValue = tbJobIdValue.getText().toString();
        jobIdValue = jobIdValue.trim();
//        jobIdValue.equalsIgnoreCase();
        if (jobIdValue.matches("0-9")){
            jobIdValue = "job_" + jobIdValue;

        }
        else if (jobIdValue.startsWith("pdrt_")){
            jobIdValue = jobIdValue;
        }
        else if (!jobIdValue.startsWith("job_")){
            jobIdValue = mJobNametoJobIdMap.get(jobIdValue);
        }
        else {
            jobIdValue = jobIdValue;
        }

        String jobStatus = spJobStatus.getSelectedItem().toString();
        jobStatus = getSystemWorkStatus(jobStatus);

        if ((!mIsInStoppageMode) && (userIdValue == null ||  userIdValue.equals("")) || jobIdValue.equals("") || stationIdValue.equals("")) {
            params = null;

            String missingCode = "";
            if (userIdValue == null){
                Toast.makeText(getContext(),
                                "Unknown User",
                                Toast.LENGTH_LONG)
                        .show();
            }
            else if (stationIdValue.equals("")) {
                Toast.makeText(getContext(),
                                Html.fromHtml("<font color='#" + "ffffff" + "' >" +
                                        "Station Id Missing" + "</font>"),
                                Toast.LENGTH_LONG)
                        .show();
            } else {
                if (mIsInStoppageMode && userIdValue.equals("") && jobIdValue.equals(""))
                    missingCode = "User & Job";
                else {
                    if (userIdValue.equals("") && !mIsInStoppageMode)
                        missingCode = "User ID";
                    else
                        missingCode = "Job";
                }

                Toast.makeText(getContext(),
                                Html.fromHtml("<font color='#" + "ffffff" + "' >" +
                                        "Scan " + missingCode + "</font>"),
                                Toast.LENGTH_LONG)
                        .show();
            }
        } else {
            if (mIsInStoppageMode) {
                params.put("request", "recordStoppage");
                params.put("stoppageId", mStoppageId);
                params.put("jobId", jobIdValue);
                params.put("stationId", stationIdValue);
                params.put("jobStatus", jobStatus);

                // the user may optionally provide a brief description, depending on app settings
                if (preferences.getBoolean("allowStoppageDescription", false)) {
                    EditText etStoppageDesc = (EditText) getActivity().findViewById(R.id.etStoppageDescription);
                    params.put("description", etStoppageDesc.getText().toString());
                }
            } else {
                params.put("request", "clockUser");
                params.put("jobId", jobIdValue);//mReworkData.jobValue); // TODO
                params.put("userId", userIdValue);//mReworkData.userValue);
                params.put("stationId", stationIdValue);
                params.put("jobStatus", jobStatus); //TODO //stageComplete
            }

            Log.d(TAG, "Parameters- " + params);
        }

        return params;
    }

    private String buildUri(String serverAddress, Map<String, String> params) {

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
    private String getSystemWorkStatus(String workStatus) {
        String systemWorkStatus = "";

        String[] status_list = getResources().getStringArray(R.array.list_work_status);
        String[] status_list_values = getResources().getStringArray(R.array.list_work_status_values);
        String[] stoppageStatusList = getResources().getStringArray(R.array.stoppage_status);
        String[] stoppageStatusListValues = getResources().getStringArray(
                R.array.stoppage_status_values
        );

        int index = -1;
        if (!mIsInStoppageMode) {
            for (int i = 0; i < status_list.length; i++) {
                if (status_list[i].equals(workStatus)) {
                    index = i;
                    break;
                }
            }

            if (index < 0) {
                systemWorkStatus = "unknown";
            } else {
                systemWorkStatus = status_list_values[index];
            }
        } else {
            for (int i = 0; i < stoppageStatusList.length; i++) {
                if (stoppageStatusList[i].equals(workStatus)) {
                    index = i;
                    break;
                }
            }

            if (index < 0) {
                systemWorkStatus = "unknown";
            } else {
                systemWorkStatus = stoppageStatusListValues[index];
            }
        }

        return systemWorkStatus;
    }

    private Map<String, String> getResponseValues(JSONObject serverResponse) {
        Map<String, String> responseValues = new HashMap<String, String>();

        String status = "";
        try {
            status = serverResponse.getString("status");

            responseValues.put("status", status);

            if (status.equals("success")) {
                // result might be a json object or a string. Test for type using optJSONObject
                JSONObject jsonResult = serverResponse.optJSONObject("result");
                if (jsonResult != null) {
                    responseValues.put("state", jsonResult.getString("state"));
                    responseValues.put("logRef", jsonResult.getString("logRef"));
                } else {
                    String result = serverResponse.optString("result", null);
                    responseValues.put("result", result);
                }
            } else {
                responseValues.put("result", serverResponse.getString("result"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Log.d(TAG, "Error- Server response to clock request not valid");
        }

        Log.d(TAG, "getResponseValues-responseValues: " + responseValues);

        return responseValues;
    }

    private void onBtnSendPressed() {
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
                getString(R.string.prefs_server_base_address), "");

        if (serverAddress == "") {
            Toast.makeText(getContext(),
                    "Please enter server address in the settings",
                    Toast.LENGTH_SHORT).show();
            return;
        } else
            transmitData(serverAddress);
    }



    private void onBtnCancelPressed() {
        resetDisplay();
        mDataDisplayInteractionCallback.onBarcodeReadHandled();
    }

    void resetDisplay() {
        SharedPreferences preferences = getContext().getSharedPreferences(
                getString(R.string.preferences_file_key),
                Context.MODE_PRIVATE);

        Boolean rememberUserPref = preferences.getBoolean(
                "rememberUser", false);

        if(mIsInStoppageMode){
            mIsInStoppageMode = false;
            setDisplayedUserIdValue("", "");
        }


        ArrayList<String> statusNames = new ArrayList<>(
                Arrays.asList(getResources().getStringArray(R.array.list_work_status))
        );
        setJobStatusOptions(statusNames);


        TextView tvUserIdLabel = (TextView) (getActivity().findViewById(R.id.tvUserIdLable));
        String userIdLabel = getResources().getString(R.string.user_id_label);
        tvUserIdLabel.setText(userIdLabel);

        if (!rememberUserPref) {
            setDisplayedUserIdValue("", "");
        }

        setDisplayedJobIdValue("");

        Spinner spJobStatus = (Spinner) (getActivity().findViewById(R.id.spJobStatus));
        spJobStatus.setSelection(0);

        if (preferences.getBoolean("enableUserStatus", true)) {
            ConstraintLayout clUserStatusContainer = (ConstraintLayout) getActivity().findViewById(R.id.clUserStatusContainer);
            clUserStatusContainer.setVisibility(View.VISIBLE);
        }
        else {
            ConstraintLayout clUserStatusContainer = (ConstraintLayout) getActivity().findViewById(R.id.clUserStatusContainer);
            clUserStatusContainer.setVisibility(View.GONE);
        }

        TextView tvStoppageLabel = (TextView) getActivity().findViewById(R.id.tvStoppageDescriptionLabel);
        EditText etStoppageDesc = (EditText) getActivity().findViewById(R.id.etStoppageDescription);

        tvStoppageLabel.setVisibility(View.GONE);
        etStoppageDesc.setVisibility(View.GONE);
        etStoppageDesc.setText("");

        mReworkData.jobValue = null;
        mReworkData.userValue = null;
        mCurrentBarcodeValue = null;

        ConstraintLayout clDataDisplayLayout = (ConstraintLayout)
                (getActivity().findViewById(R.id.clDataDisplayLayout));

        clDataDisplayLayout.requestFocus();

        setSendBtnEnabled(true);
    }

    public String getUrlFromIpAddress(String ipAddress) {
        String path = getResources().getString(R.string.server_client_input_path);
        return (ipAddress + path);
    }

    public void setStations() {

        SharedPreferences preferences = getContext().getSharedPreferences(
                getString(R.string.preferences_file_key),
                Context.MODE_PRIVATE);
        String ipAddress = preferences.getString(
                getString(R.string.prefs_server_base_address), "");

        if (ipAddress == "") {
            Toast.makeText(getContext(),
                    "Please enter server address in the settings",
                    Toast.LENGTH_SHORT).show();
            return;
        } else {

            String path = getResources().getString(R.string.server_stations_path);

            String serverUrl = ipAddress + path;

            Map<String, String> params = new HashMap<String, String>();
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
                                    for (int i = 0, size = jsonStations.length(); i < size; i++) {
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
                                            getString(R.string.preferences_staticStation), false);

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
                                Log.d(TAG, "$$ Error requesting stations- " + screenMsg);
                            }

                            //TODO: fill in response functionality

                            Log.d(TAG, "Attempted to retrieve stations" + response.toString());

                            mDataDisplayInteractionCallback.onBarcodeReadHandled();
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

    public void getStoppageReasons() {

        SharedPreferences preferences = getContext().getSharedPreferences(
                getString(R.string.preferences_file_key),
                Context.MODE_PRIVATE);
        String ipAddress = preferences.getString(
                getString(R.string.prefs_server_base_address), "");

        if (ipAddress == "") {
            return;
        } else {

            String path = getResources().getString(R.string.server_stoppages_path);

            String serverUrl = ipAddress + path;

            Map<String, String> params = new HashMap<String, String>();
            params.put("request", "getStoppageReasonTableData");
            params.put("tableOrdering", "byAlphabetic");

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

                                    JSONArray jsonReasonsArray = response.getJSONArray("result");
                                    JSONObject jsonStoppageReason;

                                    mStoppageReasons.clear();

                                    for (int i = 0, size = jsonReasonsArray.length(); i < size; i++) {
                                        jsonStoppageReason = jsonReasonsArray.getJSONObject(i);
                                        mStoppageReasons.put(
                                                jsonStoppageReason.getString("stoppageReasonId"),
                                                jsonStoppageReason.getString("stoppageReasonName")
                                        );
                                    }

                                    Log.d(TAG, "Got updated stoppages list");

                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }

                            } else {

                                String screenMsg = null;
                                try {
                                    screenMsg = "Error- " + response.getString("result");
                                } catch (JSONException e) {
                                    e.printStackTrace();

                                    screenMsg = "Error requesting stoppages";
                                }

                                Toast.makeText(getContext(),
                                                screenMsg,
                                                Toast.LENGTH_LONG)
                                        .show();
                                Log.d(TAG, "$$ Error requesting stoppages- " + screenMsg);
                            }

                            //TODO: fill in response functionality

                            Log.d(TAG, "Attempted to retrieve stoppages" + response.toString());

                            mDataDisplayInteractionCallback.onBarcodeReadHandled();
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

    public void getUserNames() {

        SharedPreferences preferences = getContext().getSharedPreferences(
                getString(R.string.preferences_file_key),
                Context.MODE_PRIVATE);
        String ipAddress = preferences.getString(
                getString(R.string.prefs_server_base_address), "");

        if (ipAddress == "") {
            return;
        } else {

            String path = getResources().getString(R.string.server_users_path);

            String serverUrl = ipAddress + path;

            Map<String, String> params = new HashMap<String, String>();
            params.put("request", "getUserTableData");
            params.put("tableOrdering", "byAlphabetic");

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

                                    JSONArray jsonUserNamesArray = response.getJSONArray("result");
                                    JSONObject jsonUserName;

                                    mUserIdtoUserNameMap.clear();

                                    for (int i = 0, size = jsonUserNamesArray.length(); i < size; i++) {
                                        jsonUserName = jsonUserNamesArray.getJSONObject(i);
                                        mUserIdtoUserNameMap.put(
                                                jsonUserName.getString("userId"),
                                                jsonUserName.getString("userName")
                                        );
                                    }

                                    Log.d(TAG, "Got updated users list");

                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }

                            } else {

                                String screenMsg = null;
                                try {
                                    screenMsg = "Error- " + response.getString("result");
                                } catch (JSONException e) {
                                    e.printStackTrace();

                                    screenMsg = "Error requesting User Names";
                                }

                                Toast.makeText(getContext(),
                                                screenMsg,
                                                Toast.LENGTH_LONG)
                                        .show();
                                Log.d(TAG, "$$ Error requesting User Names- " + screenMsg);
                            }

                            //TODO: fill in response functionality

                            Log.d(TAG, "Attempted to retrieve User Names" + response.toString());

                            mDataDisplayInteractionCallback.onBarcodeReadHandled();
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

    public void getJobNamesForIds() {

        SharedPreferences preferences = getContext().getSharedPreferences(
                getString(R.string.preferences_file_key),
                Context.MODE_PRIVATE);
        String ipAddress = preferences.getString(
                getString(R.string.prefs_server_base_address), "");

        if (ipAddress == "") {
            return;
        } else {

            String path = getResources().getString(R.string.server_jobs_path);

            String serverUrl = ipAddress + path;

            Map<String, String> params = new HashMap<String, String>();
            params.put("request", "getJobList");

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

                                    JSONArray jsonJobNamesArray = response.getJSONArray("result");
                                    JSONObject jsonJobName;

                                    mJobIdtoJobNameMap.clear();

                                    for (int i = 0, size = jsonJobNamesArray.length(); i < size; i++) {
                                        jsonJobName = jsonJobNamesArray.getJSONObject(i);
                                        mJobIdtoJobNameMap.put(
                                                jsonJobName.getString("jobId"),
                                                jsonJobName.getString("jobName")
                                        );
                                    }

                                    Log.d(TAG, "Got updated jobs list");

                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }

                            } else {

                                String screenMsg = null;
                                try {
                                    screenMsg = "Error- " + response.getString("result");
                                } catch (JSONException e) {
                                    e.printStackTrace();

                                    screenMsg = "Error requesting Job Names";
                                }

                                Toast.makeText(getContext(),
                                                screenMsg,
                                                Toast.LENGTH_LONG)
                                        .show();
                                Log.d(TAG, "$$ Error requesting Job Names- " + screenMsg);
                            }

                            //TODO: fill in response functionality

                            Log.d(TAG, "Attempted to retrieve Job Names" + response.toString());

                            mDataDisplayInteractionCallback.onBarcodeReadHandled();
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

    public void getJobIdsForNames() {

        SharedPreferences preferences = getContext().getSharedPreferences(
                getString(R.string.preferences_file_key),
                Context.MODE_PRIVATE);
        String ipAddress = preferences.getString(
                getString(R.string.prefs_server_base_address), "");

        if (ipAddress == "") {
            return;
        } else {

            String path = getResources().getString(R.string.server_jobs_path);

            String serverUrl = ipAddress + path;

            Map<String, String> params = new HashMap<String, String>();
            params.put("request", "getJobList");

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

                                    JSONArray jsonJobIdsArray = response.getJSONArray("result");
                                    JSONObject jsonJobId;

                                    mJobNametoJobIdMap.clear();

                                    for (int i = 0, size = jsonJobIdsArray.length(); i < size; i++) {
                                        jsonJobId = jsonJobIdsArray.getJSONObject(i);
                                        mJobNametoJobIdMap.put(
                                                jsonJobId.getString("jobName"),
                                                jsonJobId.getString("jobId")
                                        );
                                    }

                                    Log.d(TAG, "Got updated jobs list");

                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }

                            } else {

                                String screenMsg = null;
                                try {
                                    screenMsg = "Error- " + response.getString("result");
                                } catch (JSONException e) {
                                    e.printStackTrace();

                                    screenMsg = "Error requesting Job Ids";
                                }

                                Toast.makeText(getContext(),
                                                screenMsg,
                                                Toast.LENGTH_LONG)
                                        .show();
                                Log.d(TAG, "$$ Error requesting Job Ids- " + screenMsg);
                            }

                            //TODO: fill in response functionality

                            Log.d(TAG, "Attempted to retrieve Job Ids" + response.toString());

                            mDataDisplayInteractionCallback.onBarcodeReadHandled();
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

    public void getUserIdsForUserNames() {

        SharedPreferences preferences = getContext().getSharedPreferences(
                getString(R.string.preferences_file_key),
                Context.MODE_PRIVATE);
        String ipAddress = preferences.getString(
                getString(R.string.prefs_server_base_address), "");

        if (ipAddress == "") {
            return;
        } else {

            String path = getResources().getString(R.string.server_users_path);

            String serverUrl = ipAddress + path;

            Map<String, String> params = new HashMap<String, String>();
            params.put("request", "getUserTableData");
            params.put("tableOrdering", "byAlphabetic");

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

                                    JSONArray jsonUserNamesArray = response.getJSONArray("result");
                                    JSONObject jsonUserName;

                                    mUserNametoUserIdMap.clear();

                                    for (int i = 0, size = jsonUserNamesArray.length(); i < size; i++) {
                                        jsonUserName = jsonUserNamesArray.getJSONObject(i);
                                        mUserNametoUserIdMap.put(
                                                jsonUserName.getString("userName"),
                                                jsonUserName.getString("userId")
                                        );
                                    }

                                    Log.d(TAG, "Got updated users list");

                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }

                            } else {

                                String screenMsg = null;
                                try {
                                    screenMsg = "Error- " + response.getString("result");
                                } catch (JSONException e) {
                                    e.printStackTrace();

                                    screenMsg = "Error requesting User Ids";
                                }

                                Toast.makeText(getContext(),
                                                screenMsg,
                                                Toast.LENGTH_LONG)
                                        .show();
                                Log.d(TAG, "$$ Error requesting User Ids- " + screenMsg);
                            }

                            //TODO: fill in response functionality

                            Log.d(TAG, "Attempted to retrieve User Ids" + response.toString());

                            mDataDisplayInteractionCallback.onBarcodeReadHandled();
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

    private void setSendBtnEnabled(boolean enabled) {
        Button btnSend = (Button) getActivity().findViewById(R.id.btnSend);
        btnSend.setEnabled(enabled);
    }

    // Ask for number of items completed while clocked on and send input to server
    private void requestQuantityComplete(final int timelogRef) {
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

    private void transmitQuantityComplete(int timelogRef, int quantityComplete) {

        SharedPreferences preferences = getContext().getSharedPreferences(
                getString(R.string.preferences_file_key),
                Context.MODE_PRIVATE);

        String ipAddress = preferences.getString(
                getString(R.string.prefs_server_base_address), "");

        if (ipAddress != "") {
            String path = getResources().getString(R.string.server_client_input_path);

            String serverUrl = ipAddress + path;

            Map<String, String> params = new HashMap<String, String>();
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

    public void handleRequestError(VolleyError error) {
        if (error.networkResponse == null) {

            if (error.getClass().equals(TimeoutError.class)) {
                Toast.makeText(getContext(),
                                "Error: No response from server",
                                Toast.LENGTH_LONG)
                        .show();
                Log.d(TAG, "$$onErrorReponse: Sent data to server. No response");
            } else {
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



    public void updateUserStatusIndicator(String userIdentifierString) {
        // needs a valid user id, otherwise blank indicator
        String userId = "";
        userIdentifierString = userIdentifierString.trim();
        if(userIdentifierString.startsWith("00")){    //todo: string to be changed regex
            userId = "user_"+userIdentifierString;
        }
        else if(!userIdentifierString.startsWith("user_")) {
            userId = mUserNametoUserIdMap.get(userIdentifierString);
        }
        else {
            if (!mIsInStoppageMode) {
                userId = userIdentifierString;
            }
        }

        if (userId == null || userId == "" || userId == " " || userId.length() == 0){
            Handler mainHandler = new Handler(getContext().getMainLooper());

            Runnable runnable = new Runnable() {
                @Override
                public void run() {

                    TextView tvUserStatus = (TextView) getActivity().findViewById(R.id.tvUserStatus);
                    TextView tvUserStatusJobId = (TextView) getActivity().findViewById(R.id.tvUserStatusJobId);
                    TextView tvUserStatusProductId = (TextView) getActivity().findViewById(R.id.tvUserStatusProductId);
                    TextView tvUserStatusStationId = (TextView) getActivity().findViewById(R.id.tvUserStatusStationId);
                    String userStatus = "";
                    tvUserStatus.setText(userStatus);
                    tvUserStatus.setBackgroundColor(getResources().getColor(R.color.blankUSerStatusBackgroundColour));
                    tvUserStatus.setTextColor(getResources().getColor(R.color.blankUserStatusTextColour));

                    tvUserStatusJobId.setText("");
                    tvUserStatusJobId.setBackgroundColor(getResources().getColor(R.color.blankUSerStatusBackgroundColour));
                    tvUserStatusJobId.setTextColor(getResources().getColor(R.color.blankUserStatusTextColour));

                    tvUserStatusProductId.setText("");
                    tvUserStatusProductId.setBackgroundColor(getResources().getColor(R.color.blankUSerStatusBackgroundColour));
                    tvUserStatusProductId.setTextColor(getResources().getColor(R.color.blankUserStatusTextColour));

                    tvUserStatusStationId.setText("");
                    tvUserStatusStationId.setBackgroundColor(getResources().getColor(R.color.blankUSerStatusBackgroundColour));
                    tvUserStatusStationId.setTextColor(getResources().getColor(R.color.blankUserStatusTextColour));

                }
            };

            mainHandler.post(runnable);

            return;
        }
        SharedPreferences preferences = getContext().getSharedPreferences(
                getString(R.string.preferences_file_key),
                Context.MODE_PRIVATE);

        String ipAddress = preferences.getString(
                getString(R.string.prefs_server_base_address), "");

        if (ipAddress == "") {
            Toast.makeText(getContext(),
                    "Please enter server address in the settings",
                    Toast.LENGTH_SHORT).show();
            return;
        } else {

            String path = "/timelogger/scripts/server/current_users.php";

            String serverUrl = ipAddress + path;

            Map<String, String> params = new HashMap<String, String>();
            params.put("request", "GetUserStatus");
            params.put("userId", userId);

            Log.d(TAG, "Parameters- " + params);

            String uri = buildUri(serverUrl, params);

            Log.d(TAG, "Server Address" + uri);

            Log.d(TAG, "*****************************************************");

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, uri, null,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject jsonResponse) {
                            try {
                                TextView tvUserStatus = (TextView) getActivity().findViewById(R.id.tvUserStatus);
                                TextView tvUserStatusJobId = (TextView) getActivity().findViewById(R.id.tvUserStatusJobId);
                                TextView tvUserStatusProductId = (TextView) getActivity().findViewById(R.id.tvUserStatusProductId);
                                TextView tvUserStatusStationId = (TextView) getActivity().findViewById(R.id.tvUserStatusStationId);
                                if (jsonResponse.getString("status").equals("success"))
                                {
                                    JSONObject result = jsonResponse.getJSONObject("result");
                                    String userStatus = "";
                                    if (result.getString("status").equals("clockedOn"))
                                    {
                                        userStatus = "status: Clocked ON";
                                        tvUserStatus.setBackgroundColor(getResources().getColor(R.color.clockOnConfirmationBackgroundColour));
                                        tvUserStatus.setTextColor(getResources().getColor(R.color.clockOnConfirmationTextColour));

                                        String JobId = "Job: " + result.getString("jobName");
                                        tvUserStatusJobId.setText(JobId);
                                        tvUserStatusJobId.setBackgroundColor(getResources().getColor(R.color.clockOnConfirmationBackgroundColour));
                                        tvUserStatusJobId.setTextColor(getResources().getColor(R.color.clockOnConfirmationTextColour));

                                        String ProductId = "Product ID: " + result.getString("productId");
                                        tvUserStatusProductId.setText(ProductId);
                                        tvUserStatusProductId.setBackgroundColor(getResources().getColor(R.color.clockOnConfirmationBackgroundColour));
                                        tvUserStatusProductId.setTextColor(getResources().getColor(R.color.clockOnConfirmationTextColour));

                                        String StationId = "Station ID: " + result.getString("stationId");
                                        tvUserStatusStationId.setText(StationId);
                                        tvUserStatusStationId.setBackgroundColor(getResources().getColor(R.color.clockOnConfirmationBackgroundColour));
                                        tvUserStatusStationId.setTextColor(getResources().getColor(R.color.clockOnConfirmationTextColour));
                                    }
                                    else
                                    {
                                        userStatus = "status: Clocked OFF";
                                        tvUserStatus.setBackgroundColor(getResources().getColor(R.color.clockOffConfirmationBackgroundColour));
                                        tvUserStatus.setTextColor(getResources().getColor(R.color.clockOffConfirmationTextColour));

                                        tvUserStatusJobId.setText("");
                                        tvUserStatusJobId.setBackgroundColor(getResources().getColor(R.color.clockOffConfirmationBackgroundColour));
                                        tvUserStatusJobId.setTextColor(getResources().getColor(R.color.clockOffConfirmationTextColour));

                                        tvUserStatusProductId.setText("");
                                        tvUserStatusProductId.setBackgroundColor(getResources().getColor(R.color.clockOffConfirmationBackgroundColour));
                                        tvUserStatusProductId.setTextColor(getResources().getColor(R.color.clockOffConfirmationTextColour));

                                        tvUserStatusStationId.setText("");
                                        tvUserStatusStationId.setBackgroundColor(getResources().getColor(R.color.clockOffConfirmationBackgroundColour));
                                        tvUserStatusStationId.setTextColor(getResources().getColor(R.color.clockOffConfirmationTextColour));


                                    }
                                    tvUserStatus.setText(userStatus);
                                }
                                else{
                                    tvUserStatus.setText("failed to get user status");
                                }

                            }

                            catch (JSONException e) {
                                e.printStackTrace();
                            }

                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError volleyError) {

                        }
                    }
            );
            mRequestQueue.add(request);
        }
    }
}
