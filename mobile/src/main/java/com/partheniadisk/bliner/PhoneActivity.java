package com.partheniadisk.bliner;

/**
 * Created by gpart on 21/10/2017.
 */


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataApi.DataItemResult;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageApi.SendMessageResult;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Receives its own events using a listener API designed for foreground activities. Updates a data
 * item every second while it is open. Also allows user to take a photo and send that as an asset
 * to the paired wearable.
 */
public class PhoneActivity extends Activity implements
        TextToSpeech.OnInitListener,
        CapabilityApi.CapabilityListener,
        MessageApi.MessageListener,
        DataApi.DataListener,
        ConnectionCallbacks,
        OnConnectionFailedListener {

    private static final String TAG = "PhoneActivity";

    //Request code for launching the Intent to resolve Google Play services errors.
    private static final int REQUEST_RESOLVE_ERROR = 1000;

    private static final int REQUEST_IMAGE_CAPTURE = 1;

    private static final String START_ACTIVITY_PATH = "/start-activity";
    private static final String COUNT_PATH = "/count";
    private static final String IMAGE_PATH = "/image";
    private static final String IMAGE_KEY = "photo";
    private static final String COUNT_KEY = "count";

    private static final String STAGE_PATH = "/stage";
    private static final String STAGE_KEY = "stage";

    private GoogleApiClient mGoogleApiClient;
    private boolean mResolvingError = false;
    private boolean mCameraSupported = false;

    private ListView mDataItemList;
    private Button mSendPhotoBtn;
    private ImageView mThumbView;
    private Bitmap mImageBitmap;
    private View mStartActivityBtn;

    private DataItemAdapter mDataItemListAdapter;

    // Send DataItems.
    private ScheduledExecutorService mGeneratorExecutor;
    private ScheduledFuture<?> mDataItemGeneratorFuture;
    private int messageCounter = 0;
    private int mobileStage = 1;
    private Button mNextStageBtn;
    private Button mPrevStageBtn;
    private TextView mInstructionText;
    private TextToSpeech speaker;
    private boolean navStaretd = false;
    private Button mSoundCurrentCheckpoint;
    private Button mSoundLandmark;
    private int delay = 1100;
    private long tempTime = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LOGD(TAG, "onCreate");
        mCameraSupported = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
        setContentView(R.layout.main_activity);
        setupViews();
        mNextStageBtn.setText(getResources().getString(R.string.next_stage)+": "+String.valueOf(mobileStage+1));
//        mPrevStageBtn.setText(getResources().getString(R.string.next_stage)+": "+String.valueOf(mobileStage+1));
        mPrevStageBtn.setEnabled(false);
        mSoundCurrentCheckpoint = (Button) findViewById(R.id.current);
        mSoundLandmark = (Button) findViewById(R.id.landmarks);

        speaker=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    speaker.setLanguage(new Locale("nl_NL"));
                }
            }
        });

//        mImageBitmap = BitmapFactory.decodeResource(getApplicationContext().getResources(),
//                R.drawable.ic_launcher);
//        mThumbView.setImageBitmap(mImageBitmap);
        // Stores DataItems received by the local broadcaster or from the paired watch.
        mDataItemListAdapter = new DataItemAdapter(this, android.R.layout.simple_list_item_1);
        mDataItemList.setAdapter(mDataItemListAdapter);

        mGeneratorExecutor = new ScheduledThreadPoolExecutor(1);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();



    }


    @Override
    protected void onStart() {
        super.onStart();
        if (!mResolvingError) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mDataItemGeneratorFuture = mGeneratorExecutor.scheduleWithFixedDelay(
                new DataItemGenerator(), 1, 5, TimeUnit.SECONDS);
    }

    @Override
    public void onPause() {

        if(speaker!=null){
            speaker.stop();
            speaker.shutdown();
        }
        super.onPause();
        mDataItemGeneratorFuture.cancel(true /* mayInterruptIfRunning */);
    }

    @Override
    protected void onStop() {
        if (!mResolvingError && (mGoogleApiClient != null) && (mGoogleApiClient.isConnected())) {
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            Wearable.MessageApi.removeListener(mGoogleApiClient, this);
            Wearable.CapabilityApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            mImageBitmap = (Bitmap) extras.get("data");
            mThumbView.setImageBitmap(mImageBitmap);
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        LOGD(TAG, "Google API Client was connected");
        mResolvingError = false;
        mStartActivityBtn.setEnabled(true);
//        mSendPhotoBtn.setEnabled(mCameraSupported);
//        mNextStageBtn.setEnabled(false);
//        mPrevStageBtn.setEnabled(false);
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
        Wearable.CapabilityApi.addListener(
                mGoogleApiClient, this, Uri.parse("wear://"), CapabilityApi.FILTER_REACHABLE);
    }

    @Override
    public void onConnectionSuspended(int cause) {
        LOGD(TAG, "Connection to Google API client was suspended");
        mStartActivityBtn.setEnabled(false);
//        mSendPhotoBtn.setEnabled(false);
        mNextStageBtn.setEnabled(false);
        mPrevStageBtn.setEnabled(false);
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (!mResolvingError) {

            if (result.hasResolution()) {
                try {
                    mResolvingError = true;
                    result.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
                } catch (IntentSender.SendIntentException e) {
                    // There was an error with the resolution intent. Try again.
                    mGoogleApiClient.connect();
                }
            } else {
                Log.e(TAG, "Connection to Google API client has failed");
                mResolvingError = false;
                mStartActivityBtn.setEnabled(false);
//                mSendPhotoBtn.setEnabled(false);
                mNextStageBtn.setEnabled(false);
                mPrevStageBtn.setEnabled(false);
                Wearable.DataApi.removeListener(mGoogleApiClient, this);
                Wearable.MessageApi.removeListener(mGoogleApiClient, this);
                Wearable.CapabilityApi.removeListener(mGoogleApiClient, this);
            }
        }
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        LOGD(TAG, "onDataChanged: " + dataEvents);
        //TODO:  change log text!!!!!! Important to see where we going and debugging!
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                mDataItemListAdapter.add(
                        new Event("DataItem Changed", event.getDataItem().toString()));
            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                mDataItemListAdapter.add(
                        new Event("DataItem Deleted", event.getDataItem().toString()));
            }
        }
    }

    @Override
    public void onMessageReceived(final MessageEvent messageEvent) {
        LOGD(TAG, "onMessageReceived() A message from watch was received:"
                + messageEvent.getRequestId() + " " + messageEvent.getPath());
        messageCounter+=1;
        mDataItemListAdapter.add(new Event(String.valueOf(messageCounter) + "message(s) from watch", messageEvent.toString()));
    }

    @Override
    public void onCapabilityChanged(final CapabilityInfo capabilityInfo) {
        LOGD(TAG, "onCapabilityChanged: " + capabilityInfo);

        mDataItemListAdapter.add(new Event("onCapabilityChanged", capabilityInfo.toString()));
    }

    /**
     * Sets up UI components and their callback handlers.
     */
    private void setupViews() {
//        mSendPhotoBtn = (Button) findViewById(R.id.sendPhoto);

        mNextStageBtn = (Button) findViewById(R.id.nextStage);
        mPrevStageBtn = (Button) findViewById(R.id.previousStage);
        mStartActivityBtn = findViewById(R.id.start_wearable_activity);
        mInstructionText = (TextView) findViewById(R.id.instructions_text);

//        mThumbView = (ImageView) findViewById(R.id.imageView);

        mDataItemList = (ListView) findViewById(R.id.data_item_list);
    }

    public void onNextStageClick(View view) {
        if (mGoogleApiClient.isConnected()) {
            if(mobileStage < 7){ //Means 1-6 (can go next)
                if (mobileStage==6) {
                    mNextStageBtn.setText("Restart Route");
                    mPrevStageBtn.setEnabled(true);
                }else{ //1,2,3,4,5 go next
                    mNextStageBtn.setText(getResources().getString(R.string.next_stage)+": " + String.valueOf(mobileStage+2));
                    mPrevStageBtn.setEnabled(true);
                }
                mobileStage++;

            }else{ //be at 7
                //restarting route
                mPrevStageBtn.setEnabled(false);
                mobileStage = 1;
                mNextStageBtn.setText(getResources().getString(R.string.next_stage)+": " + String.valueOf(mobileStage+1));
            }
            sendStage(mobileStage);
        }
    }

    public void onPreviousStageClick(View view) {
        if (mGoogleApiClient.isConnected()) {
            if(mobileStage > 1){
                if (mobileStage==2){
                    mPrevStageBtn.setEnabled(false);
                }
                mobileStage--;
                mNextStageBtn.setText(getResources().getString(R.string.next_stage)+": " + String.valueOf(mobileStage));
            }
            sendStage(mobileStage);
        }
    }

    /**
     * Sends an RPC to start a fullscreen Activity on the wearable.
     */
    public void onStartWearableActivityClick(View view) {
        LOGD(TAG, "Generating RPC");
        if(!navStaretd) {
            mNextStageBtn.setEnabled(true);
            stageInformation();
            navStaretd=true;
        }
        // Trigger an AsyncTask that will query for a list of connected nodes and send a
        // "start-activity" message to each connected node.
        new StartWearableActivityTask().execute();
    }

    private void sendStartActivityMessage(String node) {
        Wearable.MessageApi.sendMessage(
                mGoogleApiClient, node, START_ACTIVITY_PATH, new byte[0]).setResultCallback(
                new ResultCallback<SendMessageResult>() {
                    @Override
                    public void onResult(SendMessageResult sendMessageResult) {
                        if (!sendMessageResult.getStatus().isSuccess()) {
                            Log.e(TAG, "Failed to send message with status code: "
                                    + sendMessageResult.getStatus().getStatusCode());
                        }
                    }
                }
        );
    }

    /**
     * Dispatches an {@link android.content.Intent} to take a photo. Result will be returned back
     * in onActivityResult().
     */
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    /**
     * Builds an {@link com.google.android.gms.wearable.Asset} from a bitmap. The image that we get
     * back from the camera in "data" is a thumbnail size. Typically, your image should not exceed
     * 320x320 and if you want to have zoom and parallax effect in your app, limit the size of your
     * image to 640x400. Resize your image before transferring to your wearable device.
     */
    private static Asset toAsset(Bitmap bitmap) {
        ByteArrayOutputStream byteStream = null;
        try {
            byteStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
            return Asset.createFromBytes(byteStream.toByteArray());
        } finally {
            if (null != byteStream) {
                try {
                    byteStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    /**
     * Sends the asset that was created from the photo we took by adding it to the Data Item store.
     */

    private void sendStage(int stage) {
        PutDataMapRequest dataMap = PutDataMapRequest.create(STAGE_PATH);
        dataMap.getDataMap().putInt(STAGE_KEY, stage);
        dataMap.getDataMap().putLong("time", new Date().getTime()); //TODO: fix up time differences and calculate measurements
        PutDataRequest request = dataMap.asPutDataRequest();
        request.setUrgent();
        //TODO: (PROT) delay the stage information:

//        final Handler handler = new Handler();
//        handler.postDelayed(new Runnable() {
//            @Override
//            public void run() {
                stageInformation();
//            }
//        }, 1000);

        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .setResultCallback(new ResultCallback<DataItemResult>() {
                    @Override
                    public void onResult(DataItemResult dataItemResult) {
                        LOGD(TAG, "Skipping stage was successful: " + dataItemResult.getStatus()
                                .isSuccess());
                    }
                });
    }

    private Collection<String> getNodes() {
        HashSet<String> results = new HashSet<>();
        NodeApi.GetConnectedNodesResult nodes =
                Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();

        for (Node node : nodes.getNodes()) {
            results.add(node.getId());
        }

        return results;
    }

    /**
     * As simple wrapper around Log.d
     */
    private static void LOGD(final String tag, String message) {
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message);
        }
    }

    @Override
    public void onInit(int i) {
        if(i==TextToSpeech.SUCCESS){
            Locale bahasa = speaker.getLanguage();
            int result = speaker.setLanguage(bahasa);
            if(result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED){
                Log.e("TTS", "this lang not supported.");
            }else{

            }

        }else{
            Log.e ("TTS","initialization failed.");
        }
    }

    public void soundCurrentCheckpoint(View view) {
                speaker.speak(mSoundCurrentCheckpoint.getText().toString(), TextToSpeech.QUEUE_FLUSH, null);
    }

    public void soundALandmark(View view) {
        if(mobileStage==3){
            speaker.speak(mSoundLandmark.getText().toString(), TextToSpeech.QUEUE_FLUSH, null);

        }else if(mobileStage==4){
            speaker.speak(mSoundLandmark.getText().toString(), TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    /**
     * A View Adapter for presenting the Event objects in a list
     */
    private static class DataItemAdapter extends ArrayAdapter<Event> {

        private final Context mContext;

        public DataItemAdapter(Context context, int unusedResource) {
            super(context, unusedResource);
            mContext = context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                holder = new ViewHolder();
                LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(android.R.layout.two_line_list_item, null);
                convertView.setTag(holder);
                holder.text1 = (TextView) convertView.findViewById(android.R.id.text1);
                holder.text2 = (TextView) convertView.findViewById(android.R.id.text2);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            Event event = getItem(position);
            holder.text1.setText(event.title);
            holder.text2.setText(event.text);
            return convertView;
        }

        private class ViewHolder {
            TextView text1;
            TextView text2;
        }
    }

    private class Event {

        String title;
        String text;

        public Event(String title, String text) {
            this.title = title;
            this.text = text;
        }
    }

    private class StartWearableActivityTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... args) {
            Collection<String> nodes = getNodes();
            for (String node : nodes) {
                sendStartActivityMessage(node);
            }
            return null;
        }
    }

    /**
     * Generates a DataItem based on an incrementing count.
     */
    private class DataItemGenerator implements Runnable {

        private int count = 0;

        @Override
        public void run() {
            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(COUNT_PATH);
            putDataMapRequest.getDataMap().putInt(COUNT_KEY, count++);

            PutDataRequest request = putDataMapRequest.asPutDataRequest();
            request.setUrgent();

            LOGD(TAG, "Generating DataItem: " + request);
            if (!mGoogleApiClient.isConnected()) {
                return;
            }
            Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                    .setResultCallback(new ResultCallback<DataItemResult>() {
                        @Override
                        public void onResult(DataItemResult dataItemResult) {
                            if (!dataItemResult.getStatus().isSuccess()) {
                                Log.e(TAG, "ERROR: failed to putDataItem, status code: "
                                        + dataItemResult.getStatus().getStatusCode());
                            }
                        }
                    });
        }
    }
    private void stageInformation() {
        //give instruction: speech from mobile (& text on mobile)
        if (mobileStage == 1) {
            mInstructionText.setText("1. Draai naar 12 uur en loop 3 meter rechtdoor.");
            mSoundCurrentCheckpoint.setText("Loop 3 meter rechtdoor.");
        }
        if (mobileStage == 2) {
            mInstructionText.setText("2. U bent nu in de lounge. Draai naar 3 uur en loop 3 meter rechtdoor.");
            mSoundCurrentCheckpoint.setText("Loop 3 meter rechtdoor.");
            //Raise hand
//            TODO: if(isTurnable){}
            //is firsttime ughh

                //Say instruction to walk.
        }

        if (mobileStage == 3) {
            mInstructionText.setText("3. Stop en draai naar 10 uur. Loop 15 meter door de gang.");
            mSoundCurrentCheckpoint.setText("Over 5 meter moet u links afslaan");
            mSoundLandmark.setText("U passeert de toiletten aan uw linkerhand.");

        }

        if (mobileStage == 4) {
            mSoundLandmark.setText("U passeert de Canonshop aan uw linkerhand!");
            mInstructionText.setText("4.  U heeft 4 van de 7 controlepunten gehad. Draai naar 3 uur en loop 18 meter door de gang.");
            mSoundCurrentCheckpoint.setText("Loop 12 meter rechtdoor.");
        }

        //TODO: Notify "passed from Canon Shop!"
        if (mobileStage == 5) {
            mInstructionText.setText("5. Draai naar 9 uur. Loop 15 meter rechtdoor richting de automaten.");
            mSoundCurrentCheckpoint.setText("Loop 12 meter rechtdoor richting de automaten.");

        }
        if (mobileStage == 6) {
            mInstructionText.setText("6. Draai naar 9 uur en loop langs de plantenbak aan uw linkerhand.");
            mSoundCurrentCheckpoint.setText("loop langs de plantenbak aan uw linkerhand.");
        }

        if (mobileStage == 7) {
            mInstructionText.setText("Draai naar 10 uur en loop 3 meter rechtdoor naar cafe Brandstof.");
            mSoundCurrentCheckpoint.setText("Bestemming bereikt, cafe Brandstof!.");

        }
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                speaker.speak(mInstructionText.getText().toString(), TextToSpeech.QUEUE_FLUSH, null);
                //Do something after 1300ms
            }
        }, 1300);


    }

}