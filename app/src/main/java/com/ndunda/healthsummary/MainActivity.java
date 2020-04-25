package com.ndunda.healthsummary;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;


import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.samsung.android.sdk.healthdata.HealthConnectionErrorResult;
import com.samsung.android.sdk.healthdata.HealthConstants;
import com.samsung.android.sdk.healthdata.HealthData;
import com.samsung.android.sdk.healthdata.HealthDataResolver;
import com.samsung.android.sdk.healthdata.HealthDataStore;
import com.samsung.android.sdk.healthdata.HealthPermissionManager;
import com.samsung.android.sdk.healthdata.HealthResultHolder;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;

public class MainActivity extends AppCompatActivity {

    public static final String APP_TAG = "SimpleHealth";
    private HealthDataStore mStore;
    private Set<HealthPermissionManager.PermissionKey> mKeySet;
    private HealthDataResolver mResolver;

    TextView stepsTextView;
    TextView stepsRequiredTextView;
    Button refreshButton;

    Map<Long, String> stepsThisWeekMap = new TreeMap<>();
    int day_of_week_today;
    int totalSteps;
    int totalStepsLastWeek;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        stepsTextView = findViewById(R.id.steps_text);
        stepsRequiredTextView = findViewById(R.id.steps_required);
        refreshButton = findViewById(R.id.refresh_button);

        mKeySet = new HashSet<>();
        mKeySet.add(new HealthPermissionManager.PermissionKey(HealthConstants.StepCount.HEALTH_DATA_TYPE, HealthPermissionManager.PermissionType.READ));
        mKeySet.add(new HealthPermissionManager.PermissionKey("com.samsung.shealth.step_daily_trend", HealthPermissionManager.PermissionType.READ));

        // Create a HealthDataStore instance and set its listener
        mStore = new HealthDataStore(this, new HealthDataStore.ConnectionListener() {
            @Override
            public void onConnected() {
                Log.d(APP_TAG, "Health data service is connected.");
                HealthPermissionManager pmsManager = new HealthPermissionManager(mStore);

                try {
                    // Check whether the permissions that this application needs are acquired
                    Map<HealthPermissionManager.PermissionKey, Boolean> resultMap = pmsManager.isPermissionAcquired(mKeySet);

                    if (resultMap.containsValue(Boolean.FALSE)) {
                        // Request the permission for reading step counts if it is not acquired
                        pmsManager.requestPermissions(mKeySet, MainActivity.this).setResultListener(mPermissionListener);
                    } else {
                        updateSteps();
                    }
                } catch (Exception e) {
                    Log.e(APP_TAG, e.getClass().getName() + " - " + e.getMessage());
                    Log.e(APP_TAG, "Permission setting fails.");
                }
            }

            @Override
            public void onConnectionFailed(HealthConnectionErrorResult healthConnectionErrorResult) {
                Log.d(APP_TAG, "onConnectionFailed");

            }

            @Override
            public void onDisconnected() {
                Log.d(APP_TAG, "onDisconnected");

            }
        });
        // Request the connection to the health data store
        mStore.connectService();

        refreshButton.setOnClickListener(view -> {
                    updateSteps();
                }
        );
    }

    public void updateSteps() {
        totalSteps = 0;
        totalStepsLastWeek = 0;

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        day_of_week_today = cal.get(Calendar.DAY_OF_WEEK);

        // reset the calendar to sunday
        cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        cal.add(Calendar.DATE, -7);

        cal.set(Calendar.HOUR_OF_DAY, 0); // ! clear would not reset the hour of day !
        cal.clear(Calendar.MINUTE);
        cal.clear(Calendar.SECOND);
        cal.clear(Calendar.MILLISECOND);


        for (int k = 0; k <= 6; k++) {
            readTodayStepCountData(cal.getTimeInMillis(), DateFormat.format("EEEE", cal).toString(), false);
            cal.add(Calendar.DATE, 1);
        }

        for (int k = 0; k <= day_of_week_today; k++) {
            readTodayStepCountData(cal.getTimeInMillis(), DateFormat.format("EEEE", cal).toString(), true);
            cal.add(Calendar.DATE, 1);
        }
    }

    public void readTodayStepCountData(long timestamp, String day_of_week, boolean this_week) {

        // Suppose that the required permission has been acquired already


        // Create a filter for today's steps from all source devices
        HealthDataResolver.Filter filter = HealthDataResolver.Filter.and(
                HealthDataResolver.Filter.eq("day_time", timestamp),
                HealthDataResolver.Filter.eq("source_type", -2));

        HealthDataResolver.ReadRequest request = new HealthDataResolver.ReadRequest.Builder()
                // Set the data type
                .setDataType("com.samsung.shealth.step_daily_trend")
                // Set a filter
                .setFilter(filter)
                // Build
                .build();

        mResolver = new HealthDataResolver(mStore, null);

        try {
            mResolver.read(request).setResultListener(result -> {
                long dayTime = 0;
                int stepsCount = 0;

                try {
                    Iterator<HealthData> iterator = result.iterator();
                    if (iterator.hasNext()) {
                        HealthData data = iterator.next();
                        dayTime = data.getLong("day_time");
                        stepsCount = data.getInt("count");

                        Log.d(APP_TAG, "Steps count found for " + day_of_week + " = " + stepsCount);

                        if (this_week) {
                            stepsThisWeekMap.put(timestamp, day_of_week + " = " + stepsCount);
                            totalSteps += stepsCount;
                        } else {
                            totalStepsLastWeek += stepsCount;
                        }

                        int avgThisWeek = totalSteps / (day_of_week_today + 1);
                        int avgLastweek = totalStepsLastWeek / 7;
                        String stepsStr = "\n";
                        for (Map.Entry<Long, String> entry : stepsThisWeekMap.entrySet()) {
                            stepsStr += entry.getValue() + "\n";
                        }

                        int stepsRemainingToMatchLastWeekAVG = (avgLastweek * (day_of_week_today + 1)) - totalSteps;

                        String displayText = stepsStr + "\n Total: " + totalSteps + "\n Average: " + avgThisWeek
                                + "\n\nTotal Last Week: " + totalStepsLastWeek + "\n Average Last Week: " + avgLastweek + "\n";

                        stepsRequiredTextView.setText("STEPS REQUIRED: " + stepsRemainingToMatchLastWeekAVG);


                        stepsTextView.setText(displayText);
                    } else {
                        Log.d(APP_TAG, "No steps count found for " + day_of_week);
                    }
                } finally {
                    result.close();
                }
            });
        } catch (Exception e) {
            Log.e(MainActivity.APP_TAG, e.getClass().getName() + " - " + e.getMessage());
        }
    }

    private final HealthResultHolder.ResultListener<HealthPermissionManager.PermissionResult> mPermissionListener =
            new HealthResultHolder.ResultListener<HealthPermissionManager.PermissionResult>() {

                @Override
                public void onResult(HealthPermissionManager.PermissionResult result) {
                    Log.d(APP_TAG, "Permission callback is received.");
                    Map<HealthPermissionManager.PermissionKey, Boolean> resultMap = result.getResultMap();

                    if (resultMap.containsValue(Boolean.FALSE)) {
                        // Requesting permission fails
                        Log.d(APP_TAG, "Requesting permission fails");
                    } else {
                        // Get the current step count and display it
                        Log.d(APP_TAG, "Requesting permission success!!");
                    }
                }
            };


    @Override
    public void onDestroy() {
        mStore.disconnectService();
        super.onDestroy();
    }
}