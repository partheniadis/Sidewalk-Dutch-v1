package com.partheniadisk.bliner;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.BoxInsetLayout;
import android.support.wearable.view.DismissOverlayView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NavActivity extends WearableActivity implements SensorEventListener {

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
    private boolean isNavStarted=false;
    private boolean navDone=false;
    private int stage=0;
    private float tempStep;
    private float tempDegree;
    private boolean isTurnable=false;
    private boolean itsFirstTime=true;
    private ImageView arrowHeading;
    private float aimDegree = 0;
    private ImageView arrowAim;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setAmbientEnabled();
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

    public void initListeners(){

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
                return false;
            }
        });

         mContainerView.setOnClickListener(new DoubleClickListener() {

             @Override
             public void onSingleClick(View v) {

             }

             @Override
             public void onDoubleClick(View v) {
                 //start prefixed navigation from joey's room to the right and left.
                 if(stage!=0) Toast.makeText(getApplicationContext(),"Restarting Navigation", Toast.LENGTH_LONG).show();
                 stage=0;
                 StartNavigationFromCurrentOrientation(currentDegree);

             }
         });
    }

    private void StartNavigationFromCurrentOrientation(float currentDegree) {
        isNavStarted = true;


    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);
        updateDisplay();
    }

    @Override
    public void onUpdateAmbient() {
        super.onUpdateAmbient();
        updateDisplay();
    }

    @Override
    public void onExitAmbient() {
        updateDisplay();
        super.onExitAmbient();
    }

    private void updateDisplay() {
        if (isAmbient()) {
            mContainerView.setBackgroundColor(getResources().getColor(android.R.color.black));
            textView.setTextColor(getResources().getColor(android.R.color.white));
            mInstructionText.setTextColor(getResources().getColor(android.R.color.white));
            mClockView.setVisibility(View.VISIBLE);
            arrowHeading.setColorFilter(getResources().getColor(R.color.white));
            mClockView.setText(AMBIENT_DATE_FORMAT.format(new Date()));
            isTurnable=false;
        } else {
            mContainerView.setBackground(null);
            mContainerView.setBackgroundColor(getResources().getColor(R.color.colorPrimaryDark));
            textView.setTextColor(getResources().getColor(R.color.colorPrimary));
            mInstructionText.setTextColor((getResources().getColor(R.color.colorPrimary)));
            arrowHeading.setColorFilter(getResources().getColor(R.color.colorPrimary));
            mClockView.setVisibility(View.GONE);
            isTurnable=true;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        //SIMPLE TYPE_ORIENTATION sensor:
        //values[0]: Angle between the magnetic north direction and the y-axis around the z-axis (0 to 359), where 0=North, 90=East, 180=South, 270=West.
        //values[1]: Rotation around x-axis (-180 to 180), with positive values when the z-axis moves towards the y-axis.
        //values[2]: Rotation around x-axis, (-90 to 90), increasing as the device moves clockwise.
        switch(event.sensor.getType()) {
            case Sensor.TYPE_ORIENTATION:
                float degree = Math.round(event.values[0]);
                currentDegree = degree;
                textView.setText("Heading: " + String.valueOf(currentDegree) + "°, Aim(blue): " + String.valueOf(aimDegree) + "°, Aim-Heading: " + String.valueOf(aimDegree-currentDegree) + ", \n" + String.valueOf(Math.round(stepTotal)) + " Steps.");
                if(!isNavStarted) {
                    arrowHeading.setVisibility(View.GONE);
                    arrowAim.setVisibility(View.GONE);
                    mInstructionText.setVisibility(View.VISIBLE);
                }else{
//                    arrowHeading.setVisibility(View.VISIBLE);
                    arrowAim.setVisibility(View.VISIBLE);
                    mInstructionText.setVisibility(View.GONE);

                    if(Math.abs(aimDegree-currentDegree)<10) arrowAim.setColorFilter(getResources().getColor(R.color.colorPrimary));
                    else arrowAim.setColorFilter(getResources().getColor(R.color.red));
                    arrowAim.setRotation(aimDegree-currentDegree); // now wanting to go to 110 degrees of the real world
//                  arrowHeading.setRotation(360-currentDegree); //(now looking at 212 degrees)
                }

                break;
//
            case Sensor.TYPE_STEP_COUNTER:
                if (startingSteps == 0) startingSteps = event.values[0];
                stepTotal = event.values[0] - startingSteps;
                textView.setText("Heading: " + String.valueOf(currentDegree) + "°, Aim(blue): " + String.valueOf(aimDegree) + "°, Aim-Heading: " + String.valueOf(aimDegree-currentDegree) + ", \n" + String.valueOf(Math.round(stepTotal)) + " Steps.");

        }
        //on any sensor change, while navigation is on, the system must check progress and vibrate properly.
        if(isNavStarted) {
            if(stage == 0){
                mInstructionText.setText("1. Walk 3 meter past the door");
                v.vibrate(500);
                stage++;
                aimDegree = 160;
                tempStep = stepTotal;
                tempDegree = currentDegree;
            }
            if (stage == 1) {
                if (stepTotal - tempStep > 8)
                {
                    v.vibrate(500);
                    if(!isTurnable){
                        mInstructionText.setText("2. Nice. Now stop and raise your watch.");
                        //speak it out too. (Noone reads the screen.)
                        tempDegree = currentDegree;
                        aimDegree=240;
                        stage++;
                        itsFirstTime=true;
                    }
                    else{
                        v.vibrate(50);
                        mInstructionText.setText("2. Nice. Now slowly turn to 3 o'clock.");
                        aimDegree=240;
                        tempDegree = currentDegree;
                        stage++;
                        itsFirstTime=true;
                    }

                }
            }
            if (stage == 2) {
                if(isTurnable) {
                    if(itsFirstTime) {
                        v.vibrate(50);
                        //TODO: Take 10 measurements of the compass and store only the last (more accurate) or the average.
                        tempDegree = currentDegree; //get and store a compass reading when hand is actually raised.
                        itsFirstTime=false; //dont enter again for the next loops. Just proceed and compare as long as in interactive mode.
                    }
                    if (currentDegree >235 && currentDegree < 250) {
                        //TODO:fix the angle differences <0,360> what happens.
                        v.vibrate(500);
                        mInstructionText.setText("3. Perfect! Now walk 3 meters straight before you reach the wall.");
                        tempStep = stepTotal;
                        stage++;
                        itsFirstTime=true;
                    }
                }else{
                    mInstructionText.setText("2. Keep watch raised, then turn to 2 o'clock.");
                    itsFirstTime=true;
                }
            }
            if (stage == 3) {
                if (stepTotal - tempStep > 8)
                {
                    v.vibrate(500);
                    if(!isTurnable){
                        mInstructionText.setText("3. Nice. Now stop and raise your watch.");
                        //speak it out too. (Noone reads the screen.)
                        tempDegree = currentDegree;
                        aimDegree=165;
                        stage++;
                        itsFirstTime=true;
                    }
                    else{
                        v.vibrate(50);
                        mInstructionText.setText("3. Nice. Now slowly turn to 9 o'clock.");
                        aimDegree=165;
                        tempDegree = currentDegree;
                        stage++;
                        itsFirstTime=true;
                    }

                }
            }
            if(stage == 4){
                if(isTurnable) {
                    if(itsFirstTime) {
                        v.vibrate(50);
                        //TODO: Take 10 measurements of the compass and store only the last (more accurate) or the average.
                        tempDegree = currentDegree; //get and store a compass reading when hand is actually raised.
                        itsFirstTime=false; //dont enter again for the next loops. Just proceed and compare as long as in interactive mode.
                    }
                    if (currentDegree >155 && currentDegree < 175) {
                        //TODO:fix the angle differences <0,360> what happens.
                        v.vibrate(500);
                        mInstructionText.setText("4. Perfect! Now walk 15 meters straight the hallway.");
                        tempStep = stepTotal;
                        stage++;
                        itsFirstTime=true;
                    }
                }else{
                    mInstructionText.setText("4. Keep watch raised, then turn to 2 o'clock.");
                    itsFirstTime=true;
                }

            }
            //TODO: LANDMARK: Pass the toilets on the left.
            if (stage == 5) { //confirmation checkpoint midway
                if (stepTotal - tempStep > 14) {
                    v.vibrate(500);
                    if(!isTurnable){
                        mInstructionText.setText("5. Nice. Now stop and raise your watch.");
                        //speak it out too. (Noone reads the screen.)
                        tempDegree = currentDegree;
                        aimDegree=250;
                        stage++;
                        itsFirstTime=true;
                    }
                    else {
                        mInstructionText.setText("5. Ok. Turn slowly to 3 o'clock");
                        tempDegree = currentDegree;
                        stage++;
                        itsFirstTime=true;
                    }
                }
            }
            if (stage == 6) {
                if(isTurnable) {
                    if(itsFirstTime) {
                        v.vibrate(50);
                        //TODO: Take 10 measurements of the compass and store only the last (more accurate) or the average.
                        tempDegree = currentDegree; //get and store a compass reading when hand is actually raised.
                        itsFirstTime=false; //dont enter again for the next loops. Just proceed and compare as long as in interactive mode.
                    }
                    if (currentDegree >240 && currentDegree<260) {
                        v.vibrate(500);
                        mInstructionText.setText("6. Nice! Walk 15 meters straight.");
                        tempStep = stepTotal;
                        stage++;
                        itsFirstTime=true;
                    }
                }else{
                    mInstructionText.setText("6. Keep watch raised, then turn to 3 o'clock.");
                    itsFirstTime=true;
                }

            }
            if(stage==7){
                if (stepTotal - tempStep > 14) {
                    v.vibrate(500);
                    if(!isTurnable){
                        mInstructionText.setText("7. Now stop and raise your watch.");
                        //speak it out too. (Noone reads the screen.)
                        tempDegree = currentDegree;
                        aimDegree=160;
                        stage++;
                        itsFirstTime=true;
                    }
                    else {
                        mInstructionText.setText("7 Now turn to 9 o'clock");
                        tempDegree = currentDegree;
                        stage++;
                        itsFirstTime=true;
                    }
                }
            }
            if (stage == 8) {
                if(isTurnable) {
                    if(itsFirstTime) {
                        v.vibrate(50);
                        //TODO: Take 10 measurements of the compass and store only the last (more accurate) or the average.
                        tempDegree = currentDegree; //get and store a compass reading when hand is actually raised.
                        itsFirstTime=false; //dont enter again for the next loops. Just proceed and compare as long as in interactive mode.
                    }
                    if (currentDegree >150 && currentDegree<165) {
                        v.vibrate(500);
                        mInstructionText.setText("8. Nice! Walk 7 meters straight.");
                        tempStep = stepTotal;
                        stage++;
                        itsFirstTime=true;
                    }
                }else{
                    mInstructionText.setText("8. Keep watch raised, then turn to 9 o'clock.");
                    itsFirstTime=true;
                }

            }
            if(stage==9){
                if (stepTotal - tempStep > 10) {
                    v.vibrate(500);
                    if(!isTurnable){
                        mInstructionText.setText("9. Now stop and raise your watch.");
                        //speak it out too. (Noone reads the screen.)
                        tempDegree = currentDegree;
                        aimDegree=100;
                        stage++;
                        itsFirstTime=true;
                    }
                    else {
                        mInstructionText.setText("9. Now turn to 9 o'clock");
                        tempDegree = currentDegree;
                        stage++;
                        itsFirstTime=true;
                    }
                }
            }
            if (stage == 10) {
                if(isTurnable) {
                    if(itsFirstTime) {
                        v.vibrate(50);
                        //TODO: Take 10 measurements of the compass and store only the last (more accurate) or the average.
                        tempDegree = currentDegree; //get and store a compass reading when hand is actually raised.
                        itsFirstTime=false; //dont enter again for the next loops. Just proceed and compare as long as in interactive mode.
                    }
                    if (currentDegree >85 && currentDegree<105) {
                        v.vibrate(500);
                        mInstructionText.setText("10. Nice! The destiantion is on your left.");
                        tempStep = stepTotal;
                        stage++;
                        itsFirstTime=true;
                        isNavStarted = false; //stop getting in the large if.
                    }
                }else{
                    mInstructionText.setText("10. The destiantion is on your left.");
                    itsFirstTime=true;
                }

            }


//            if (stage == 7) {
//                if (stepTotal - tempStep > 9) {
//                    v.vibrate(500);
//                    mInstructionText.setText("Route Complete!");
////                    navDone = true; //complete, stop looping the shit.
//                    isNavStarted = false; //stop getting in the large if.
//                }
//            }
        }

    }

    private void setArrowDirection(float currentDegree, float aimDegree) {
        this.currentDegree = currentDegree;
        this.aimDegree = aimDegree;
        arrowHeading.setRotation(360-(aimDegree-currentDegree)); //?????????
        //if aimDegree>currentDegree then set 360-aim.
        //if aimDegree<currentDegree eg. 15<310 then

    }

    private String CalculateDirection(float degree)
    {
        if(degree>322 && degree<54)
            return "NE";
        if (degree > 235 && degree < 322)
            return "NW";
        if (degree > 148 && degree < 235)
            return "SW";
        if (degree > 54 && degree < 148)
            return "SE";
        if (degree == 322)
            return "N";
        if (degree == 235)
            return "W";
        if (degree == 54)
            return "E";
        if (degree == 148)
            return "S";

        return "NE"; //Default :)

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }


   /* @Override *//* KeyEvent.Callback *//*
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_NAVIGATE_NEXT:
                // Do something that advances a user View to the next item in an ordered list.
                return moveToNextItem();
            case KeyEvent.KEYCODE_NAVIGATE_PREVIOUS:
                // Do something that advances a user View to the previous item in an ordered list.
                return moveToPreviousItem();
        }
        // If you did not handle it, let it be handled by the next possible element as deemed by the Activity.
        return super.onKeyDown(keyCode, event);
    }

    *//** Shows the next item in the custom list. *//*
    private boolean moveToNextItem() {
        boolean handled = false;
          // Return true if handled successfully, otherwise return false.
        return handled;
    }

    *//** Shows the previous item in the custom list. *//*
    private boolean moveToPreviousItem() {
        boolean handled = false;
        // Return true if handled successfully, otherwise return false.
        return handled;
    }*/

}
