/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.partheniadisk.bliner;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.BoxInsetLayout;
import android.support.wearable.view.DismissOverlayView;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import java.text.SimpleDateFormat;
import java.util.Locale;

import static com.partheniadisk.bliner.DataLayerListenerService.LOGD;

public class MainActivity extends WearableActivity implements
        SensorEventListener,
        ConnectionCallbacks,
        OnConnectionFailedListener,
        DataApi.DataListener,
        MessageApi.MessageListener,
        CapabilityApi.CapabilityListener {

    private static final String TAG = "MainActivity";
    private static final String CAPABILITY_1_NAME = "capability_1";
    private static final String CAPABILITY_2_NAME = "capability_2";

    private GoogleApiClient mGoogleApiClient;


    private static final SimpleDateFormat AMBIENT_DATE_FORMAT =
            new SimpleDateFormat("HH:mm", Locale.US);

    private BoxInsetLayout mContainerView;
    private TextView textView;
    private TextView mInstructionText;
    private TextView mClockView;
    /**
     * Overlay that shows a short help text when first launched. It also provides an option to
     * exit the app.
     */
    private DismissOverlayView mDismissOverlay;

    /*SensorFusion Starts*/
    private SensorManager mSensorManager = null;
    // angular speeds from gyro
    private Vibrator v;
    public float currentDegree = 0f;
    private float stepTotal=0;
    private float startingSteps = 0;
    private boolean isNavStarted=true;
    private boolean navDone=false;
    private float tempStep;
    private float tempDegree;
    private boolean isTurnable=false;
    private boolean itsFirstTime=true;
    private ImageView arrowHeading;
    private float aimDegree = 160;
    private ImageView arrowAim;
    private int wearStage = 1;
    private int tempStage = 1 ;
    private boolean isHeadingCorrect = false;
    long[] patternScanning = {0, 100, 50, 100, 700};
    long[] patternCheckpoint = {0, 500, 100, 200, 500};
    private int delay = 1200;
    private boolean preparedForVibration = true;
    private float temp;
    private long tempTime = 0;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//        setAmbientEnabled(); // AMBIENT OFF FOR NOW AS IT MAY INTERFERE WITH CONNECTION
//        setupViews();


        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        mContainerView = (BoxInsetLayout) findViewById(R.id.container);
        textView = (TextView) findViewById(R.id.textView);
        mInstructionText = (TextView) findViewById(R.id.Instruction);
        mClockView = (TextView) findViewById(R.id.clock);
        arrowAim = (ImageView) findViewById(R.id.arrowAim);

        // get sensorManager and initialise sensor listeners
        mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        initListeners();

        mDismissOverlay = (DismissOverlayView) findViewById(R.id.dismiss_overlay);
        mDismissOverlay.setIntroText(R.string.intro_text);
        mDismissOverlay.showIntroIfNecessary();


    }

    public void initListeners() {

        //SIMPLE TYPE ORIENTATION based on xamarin basic compass
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
                SensorManager.SENSOR_DELAY_GAME);

        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER),
                SensorManager.SENSOR_DELAY_GAME);

        mContainerView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                // Display the dismiss overlay with a button to exit this activity.
                mDismissOverlay.show();
                v.vibrate(500);
                v.cancel();
                return false;
            }
        });

/*        mContainerView.setOnClickListener(new DoubleClickListener() {
            @Override
            public void onSingleClick(View v) {

            }

            @Override
            public void onDoubleClick(View v) {
//                if(wearStage !=1) Toast.makeText(getApplicationContext(),"Restarting Navigation", Toast.LENGTH_LONG).show();
//                wearStage = 1;
                isNavStarted = true;

            }
        });*/
    }


    @Override
    protected void onResume() {
        super.onResume();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        if ((mGoogleApiClient != null) && mGoogleApiClient.isConnected()) {
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            Wearable.MessageApi.removeListener(mGoogleApiClient, this);
            Wearable.CapabilityApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }

        super.onPause();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        //SIMPLE TYPE_ORIENTATION sensor:
        //values[0]: Angle between the magnetic north direction and the y-axis around the z-axis (0 to 359), where 0=North, 90=East, 180=South, 270=West.
        //values[1]: Rotation around x-axis (-180 to 180), with positive values when the z-axis moves towards the y-axis.
        //values[2]: Rotation around x-axis, (-90 to 90), increasing as the device moves clockwise.

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ORIENTATION:
                float degree = Math.round(event.values[0]);
                currentDegree = degree;
                textView.setText("Heading: " + String.valueOf(currentDegree) + "°, Aim: " + String.valueOf(aimDegree) + "°, Aim-Heading: " + String.valueOf(aimDegree - currentDegree) + ", \n Checkpoint:" + wearStage);
                if (!isNavStarted) {
                    arrowAim.setVisibility(View.GONE);
//                    mInstructionText.setVisibility(View.VISIBLE);
                } else {
                    arrowAim.setVisibility(View.VISIBLE);
//                    mInstructionText.setVisibility(View.GONE);

                    //TODO: Pass the boolean shit to mobile and speak up or make voices!!!! Realign or Warn user when they mistakenly change direction!! Visual + Vibration + Speech at mobile
                    float diff = Math.abs(aimDegree - currentDegree);
                    if ( diff < 30){ //TODO: (PROT) from 20 to +-30 tolerance.
                        tempTime = System.currentTimeMillis();
                        arrowAim.setColorFilter(getResources().getColor(R.color.colorPrimary));
                        isHeadingCorrect=true;
                        preparedForVibration=true;
//                        delay=2000;
                        v.cancel();
                    }
                    else {

                        arrowAim.setColorFilter(getResources().getColor(R.color.red));
//                        patternScanning[2] = (long) (1000+Math.abs((aimDegree - currentDegree))); //300 to 480 (+180 gain): vibrates faster when closer to correct.
//                        patternScanning[2] = (long) (1000-4*Math.abs((aimDegree - currentDegree))); //300 to 220 (-180 loss): vibrates slower when closer to correct
                        //use time delay
                        //(PROT) delay of vibration 2-3 seconds? for mistake tolerance.
                        if(System.currentTimeMillis() - tempTime > delay){
                            if(preparedForVibration){
                                v.vibrate(patternScanning,0);
                                preparedForVibration=false;
                            }
                        }
                        isHeadingCorrect=false;
                    }
                    arrowAim.setRotation(aimDegree - currentDegree); // now wanting to go to 110 degrees of the real world
                }
                break;
        }
    }

//    private int mobileStage() {
//        mobileStage = 0;
//        //TODO: check one variable coming from mobile called (int) mobileStage
//        // if mobileStage=wearStage then wearStage++ (next stage babee) while costas remains at the previous stage.
//        //When costas feels that wearable stage should go to the next stage, then he clicks the button,
//        //mobile stage gets +1, the comparison happens and if they are equal it means that it should move on.
//        return mobileStage;
//    }


    @Override
    public void onConnected(Bundle connectionHint) {
        LOGD(TAG, "onConnected(): Successfully connected to Google API client");
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
        Wearable.CapabilityApi.addListener(
                mGoogleApiClient, this, Uri.parse("wear://"), CapabilityApi.FILTER_REACHABLE);
    }

    @Override
    public void onConnectionSuspended(int cause) {
        LOGD(TAG, "onConnectionSuspended(): Connection to Google API client was suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.e(TAG, "onConnectionFailed(): Failed to connect, with result: " + result);
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        LOGD(TAG, "onDataChanged(): " + dataEvents);

        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                String path = event.getDataItem().getUri().getPath();
                if (DataLayerListenerService.STAGE_PATH.equals(path)) {
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                    int stage = dataMapItem.getDataMap()
                            .getInt(DataLayerListenerService.STAGE_KEY);
                    //stage is 1-7
                    wearStage=stage;
                    itsFirstTime = true;
                    tempStage = stage;
//                    v.cancel();

//                    v.vibrate(patternCheckpoint, -1);

//                    final Handler handler = new Handler();
//                    handler.postDelayed(new Runnable() {
//                        @Override
//                        public void run() {
                            checkStage();
                            //Do something after 1000ms
//                        }
//                    }, 1000);
                }
            }
                /*else if (DataLayerListenerService.COUNT_PATH.equals(path)) {
                    LOGD(TAG, "Data Changed for COUNT_PATH");
                    mDataFragment.appendItem("DataItem Changed", event.getDataItem().toString());
                } else {
                    LOGD(TAG, "Unrecognized path: " + path);
                }

            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                mDataFragment.appendItem("DataItem Deleted", event.getDataItem().toString());
            } else {
                mDataFragment.appendItem("Unknown data event type", "Type = " + event.getType());
            }*/
        }

/*    OLD:

        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                String path = event.getDataItem().getUri().getPath();
                if (DataLayerListenerService.IMAGE_PATH.equals(path)) {
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                    Asset photoAsset = dataMapItem.getDataMap()
                            .getAsset(DataLayerListenerService.IMAGE_KEY);
                    // Loads image on background thread.
                    new LoadBitmapAsyncTask().execute(photoAsset);

                } else if (DataLayerListenerService.COUNT_PATH.equals(path)) {
                    LOGD(TAG, "Data Changed for COUNT_PATH");
                    mDataFragment.appendItem("DataItem Changed", event.getDataItem().toString());
                } else {
                    LOGD(TAG, "Unrecognized path: " + path);
                }

            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                mDataFragment.appendItem("DataItem Deleted", event.getDataItem().toString());
            } else {
                mDataFragment.appendItem("Unknown data event type", "Type = " + event.getType());
            }
        }*/
    }

    private void checkStage() {
        if (isNavStarted) {
            if (wearStage == 1) {
                if(itsFirstTime) {
                    //TODO: RAISED HAND!!
                    //notify navigation started: vibration, speech.
                    // TODO: (PROT) special vibration patternScanning
                    v.vibrate(patternCheckpoint, -1);
                    //set align Instruction
                    aimDegree = 165;
                    //give instruction: speech from mobile (& text on mobile)
                    mInstructionText.setText("1. Keep heading at 12 o'clock and walk for 3 meters past the door.");
                    tempDegree = currentDegree; //get and store a compass reading
                    itsFirstTime=false;
                }
                tempDegree = currentDegree;
                //TODO: Explain the following at documentation + Figma sketch
                //the code at line 241 - 243 check for big angular displacements and NOTIFIES user to realign
                // User may displace their heading to
                // (1)Avoid obstacles unexpected  by the system
                // (2)Really change direction for a reason: feeling uncomfortable, scared etc.
                // User may mistakenly be notified if
                // (1) Big metallic parts or magnets are around the smartwatch e.g. chargers, trains, computers etc.
                // (2) Bad calibration/bugs
                // To prevent compass mistakes we suggest:
                // (1) Gyroscope for validation of angle dispalcements: Gyro is stable and trustful at calculating angle differences.
                // (2) Warn user on high metallic interference from environment detected
                // (3) Gyroscope to correct and continue routing ~ less accurate but stable
                //TODO: Wait for Costas to skip wearStage when User completed instructions.
//                if(mobileStage==wearStage){
//                    wearStage++;
//                    itsFirstTime=true;
//                }

            }
            if (wearStage == 2){
                if(itsFirstTime) {
                    v.vibrate(patternCheckpoint, -1);
                    aimDegree = 240;
                    mInstructionText.setText("2. Now stop & turn to 3 o'clock. Walk for 3 meters.");
                    tempDegree = currentDegree;
                    itsFirstTime=false;
                }
                tempDegree = currentDegree;
//                if(mobileStage==wearStage){
//                    wearStage++;
//                    itsFirstTime=true;
//                }
            }
            if (wearStage == 3){
                if(itsFirstTime) {
                    v.vibrate(patternCheckpoint, -1);
                    aimDegree = 160;
                    mInstructionText.setText("3. Now stop & turn to 9 o'clock. Walk for 15 meters in the hallway.");
                    tempDegree = currentDegree;
                    itsFirstTime=false;
                }
                tempDegree = currentDegree;
//                if(mobileStage==wearStage){
//                    wearStage++;
//                    itsFirstTime=true;
//                }
            }
            if (wearStage == 4){
                if(itsFirstTime) {
                    v.vibrate(patternCheckpoint, -1);
                    aimDegree = 236;
                    mInstructionText.setText("4. Nice! Turn to 3 o'clock. Walk for 15 meters straight.");
                    tempDegree = currentDegree;
                    itsFirstTime=false;
                }
                tempDegree = currentDegree;
//                if(mobileStage==wearStage){
//                    wearStage++;
//                    itsFirstTime=true;
//                }
            }
            //TODO: Notify "passed from Canon Shop!"
            if (wearStage == 5){
                if(itsFirstTime) {
                    v.vibrate(patternCheckpoint, -1);
                    aimDegree = 143;
                    mInstructionText.setText("5. Now stop & turn to 9 o'clock. Walk for 15 meters to the vending machines.");
                    tempDegree = currentDegree;
                    itsFirstTime=false;
                }
                tempDegree = currentDegree;
//                if(mobileStage==wearStage){
//                    wearStage++;
//                    itsFirstTime=true;
//                }
            }
            if (wearStage == 6){
                if(itsFirstTime) {
                    v.vibrate(patternCheckpoint, -1);
                    aimDegree = 62;
                    mInstructionText.setText("6. Stop. Turn to 9 o'clock and walk past the right side of the Plant buck.");
                    tempDegree = currentDegree;
                    itsFirstTime=false;
                }
                tempDegree = currentDegree;
            }
            if (wearStage == 7){
                if(itsFirstTime) {
                    v.vibrate(patternCheckpoint, -1);
                    aimDegree = 340;
                    mInstructionText.setText("7. Turn to 10 o'clock and walk 3 meters to Brand Stof in front of you!");
                    tempDegree = currentDegree;
                    itsFirstTime=false;
                }
                tempDegree = currentDegree;
            }
        }
    }


    /**
     * Find the connected nodes that provide at least one of the given capabilities
     */

    @Override
    public void onMessageReceived(MessageEvent event) {
        LOGD(TAG, "onMessageReceived: " + event);
//        mDataFragment.appendItem("Message", event.toString());
    }

    @Override
    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        LOGD(TAG, "onCapabilityChanged: " + capabilityInfo);
//        mDataFragment.appendItem("onCapabilityChanged", capabilityInfo.toString());
    }

    /**
     * Switches to the page {@code index}. The first page has index 0.
     */
/*    private void moveToPage(int index) {
        mPager.setCurrentItem(0, index, true);
    }*/


    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

}