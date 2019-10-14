package com.ehyper.iot;

import com.ehyper.iot.util.SystemUiHider;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.preference.PreferenceManager;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;


import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;

import com.ehyper.iot.utils.Constants;
import com.ehyper.iot.utils.MqttHandler;

import java.text.SimpleDateFormat;
import java.util.Date;


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 * @see SystemUiHider
 */
public class FullscreenActivity extends Activity  {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * If set, will toggle the system UI visibility upon interaction. Otherwise,
     * will show the system UI visibility upon interaction.
     */
    private static final boolean TOGGLE_ON_CLICK = true;

    /**
     * The flags to pass to {@link SystemUiHider#getInstance}.
     */
    private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;

    /**
     * The instance of the {@link SystemUiHider} for this activity.
     */
    private SystemUiHider mSystemUiHider;

    private MqttAndroidClient client;

    private int waitForCompletionTime = 10000;

    TextView txtView_Info = null;
    TextView txtView_temperature = null;
    TextView txtView_humidity = null;
    Switch switch_relay_1 = null;

    private MqttHandler mqttHandler;

    private BroadcastReceiver connectivityReceiver;
    private final static String TAG = FullscreenActivity.class.getName();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen);

        final View controlsView = findViewById(R.id.fullscreen_content_controls);
        final View contentView = findViewById(R.id.fullscreen_frame);

        txtView_Info = (TextView)findViewById(R.id.textView_msg);
        txtView_temperature = (TextView)findViewById(R.id.fullscreen_temp);
        txtView_humidity = (TextView)findViewById(R.id.fullscreen_humidity);
        switch_relay_1 = (Switch)findViewById(R.id.fullscreen_relay_switch1);

        // Set up an instance of SystemUiHider to control the system UI for
        // this activity.
        mSystemUiHider = SystemUiHider.getInstance(this, contentView, HIDER_FLAGS);
        mSystemUiHider.setup();
        mSystemUiHider
                .setOnVisibilityChangeListener(new SystemUiHider.OnVisibilityChangeListener() {
                    // Cached values.
                    int mControlsHeight;
                    int mShortAnimTime;

                    @Override
                    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
                    public void onVisibilityChange(boolean visible) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
                            // If the ViewPropertyAnimator API is available
                            // (Honeycomb MR2 and later), use it to animate the
                            // in-layout UI controls at the bottom of the
                            // screen.
                            if (mControlsHeight == 0) {
                                mControlsHeight = controlsView.getHeight();
                            }
                            if (mShortAnimTime == 0) {
                                mShortAnimTime = getResources().getInteger(
                                        android.R.integer.config_shortAnimTime);
                            }
                            controlsView.animate()
                                    .translationY(visible ? 0 : mControlsHeight)
                                    .setDuration(mShortAnimTime);
                        } else {
                            // If the ViewPropertyAnimator APIs aren't
                            // available, simply show or hide the in-layout UI
                            // controls.
                            controlsView.setVisibility(visible ? View.VISIBLE : View.GONE);
                        }

                        if (visible && AUTO_HIDE) {
                            // Schedule a hide().
                            delayedHide(AUTO_HIDE_DELAY_MILLIS);
                        }
                    }
                });

        // Set up the user interaction to manually show or hide the system UI.
        contentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (TOGGLE_ON_CLICK) {
                    mSystemUiHider.toggle();
                } else {
                    mSystemUiHider.show();
                }
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.setting_button).setOnTouchListener(mDelayHideTouchListener);



    }

    public void onSwitchRelay1Clicked(View view) {
        boolean on = ((Switch) view).isChecked();

        if (on) {
            // publish /esp8266/relay on
            MqttHandler.getInstance(this).publish("/esp8266/relay", "on", true, 0);
        } else {
            // publish /esp8266/relay off
            MqttHandler.getInstance(this).publish("/esp8266/relay", "off", true, 0);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        //reConnect(this);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, ".onResume() entered");
        super.onResume();

        //register receivers
        registerReceivers();

        //connect to MQTT broker
        MqttHandler.getInstance(this).connect();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // unregister connectivityReceiver
        unregisterReceivers();
    }


    /**
     * Create and register connectivityReceiver
     */
    private void registerReceivers() {

        // create connectivityReceiver if it doesn't exist
        if (connectivityReceiver == null) {
            Log.d(TAG, ".onResume() - Registering connectivityReceiver");
            connectivityReceiver = new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(TAG, ".onReceive() - Received intent for connectivityReceiver");
                    String temperature = intent.getStringExtra(Constants.TEMPERATURE);
                    String humidity = intent.getStringExtra(Constants.HUMIDITY);
                    String relay_state = intent.getStringExtra(Constants.RELAY);

                    if(temperature != null)
                        txtView_temperature.setText(temperature);
                    else if(humidity != null)
                        txtView_humidity.setText(humidity);
                    else if(relay_state != null) {
                            if (relay_state.equalsIgnoreCase("on"))
                                switch_relay_1.setChecked(true);
                            else if(relay_state.equalsIgnoreCase("off"))
                                switch_relay_1.setChecked(false);
                    }

                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
                    String timestamp = sdf.format(new Date());

                    String info_msg = intent.getStringExtra(Constants.SUBSCRIDED_MSG);
                    txtView_Info.setText("[ "+ timestamp + " ]  " + info_msg);

                    }
            };
        }

        // register connectivityReceiver
        getApplicationContext().registerReceiver(connectivityReceiver,
                new IntentFilter(Constants.ACTION_INTENT_SUBSCRIBED_MSG_RECEIVED));

    }

    /**
     * Unregister all local BroadcastReceivers
     */
    private void unregisterReceivers() {
        Log.d(TAG, ".unregisterReceivers() entered");
        if (connectivityReceiver != null) {
            getApplicationContext().unregisterReceiver(connectivityReceiver);
            connectivityReceiver = null;
        }
    }


    /*
    public void reConnect(Context context){


        final Context m_context = context;

        new Thread(new Runnable() {
            @Override
            public void run() {

                SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(m_context);
                // /data/data/com.ehyper.iot/shared_prefs/com.ehyper.iot_preferences.xml

                Boolean bCleanSession = sharedPrefs.getBoolean("pref_key_clean_session", true);
                String host = sharedPrefs.getString("pref_key_server", "!192.168.20.93");
                String port = sharedPrefs.getString("pref_key_port", "!1883");
                String clientId = sharedPrefs.getString("pref_key_client_id", "!android_iot_client_id");


                String handle = null;
                String uri = null;

                uri = "tcp://" + host + ":" + port;
                handle = uri + clientId;

                Log.d("uri", uri);
                Log.d("clientId", clientId);

                client = new MqttAndroidClient(m_context, uri, clientId);
                MqttConnectOptions conOpt = new MqttConnectOptions();
                conOpt.setCleanSession(bCleanSession);

                String[] actionArgs = new String[1];
                actionArgs[0] = clientId;
                String clientHandle = uri + clientId;

                final ActionListener callback = new ActionListener(m_context,
                        ActionListener.Action.CONNECT, clientHandle, actionArgs);

                client.setCallback(new MqttCallbackHandler(m_context, clientHandle));
                //set traceCallback
                client.setTraceCallback(new MqttTraceCallback());

                String[] topics = new String[1];
                topics[0] = "/esp8266/#";
                int qos = ActivityConstants.defaultQos;

                boolean bconnectInitiated = false;
                int connectTimeWait = 0;

                for (; ; ) {
                    try {
                        if (!bconnectInitiated) {
                            IMqttToken connectToken = client.connect(conOpt, null, callback);
                            connectToken.waitForCompletion(waitForCompletionTime);
                            bconnectInitiated = true;

                        } else {
                            if (client.isConnected()) {
                                Thread.sleep(3000);
                                Log.d("Connect isConnected", "" + client.isConnected());

                                IMqttToken subToken = null;
                                subToken = client.subscribe(topics[0], qos, null,
                                        new ActionListener(m_context, Action.SUBSCRIBE, clientHandle, topics));
                                subToken.waitForCompletion(waitForCompletionTime);

                                break;
                            } else {
                                Thread.sleep(1000);
                                connectTimeWait = connectTimeWait + 1;
                            }
                        }

                        if(connectTimeWait > 10)
                            bconnectInitiated = false; // call the connect() again.

                    } catch (MqttException e) {
                        Log.e(this.getClass().getCanonicalName(), "MqttException Occured" + clientHandle, e);
                    } catch (InterruptedException e) {
                        Log.e(this.getClass().getCanonicalName(), "InterruptedException Occured", e);
                    }
                }
            }
        }).start();


        return;

    }
*/

    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    Handler mHideHandler = new Handler();
    Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            mSystemUiHider.hide();
        }
    };

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    public void clickedSettings(View view) {
        Intent settingsIntent = new Intent(getApplicationContext(),
                SettingsActivity.class);

        /*
        Intent settingsIntent = new Intent(getApplicationContext(),
                NewConnection.class);
*/
        startActivity(settingsIntent);
    }

    /**
     * @see android.app.ListActivity#onActivityResult(int,int, android.content.Intent)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode == RESULT_CANCELED) {
            return;
        }

        Bundle dataBundle = data.getExtras();

        // perform connection create and connect
        //connectAction(dataBundle);

    }
}
