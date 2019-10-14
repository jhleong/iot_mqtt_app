/*******************************************************************************
 * Copyright (c) 2014 IBM Corp.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *   http://www.eclipse.org/org/documents/edl-v10.php.
 ******************************************************************************/
package com.ehyper.iot.utils;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;

/**
 * ActionListener implements IMqttActionListener and is needed to handle actions received
 * from the MQTT service. This simple class could be extended for additional error handling.
 */
public class ActionListener implements IMqttActionListener {

    private final static String TAG = ActionListener.class.getName();

    private Context context;
    private Constants.ActionStateStatus action;
    private MqttHandler mqttHandler;
    private IMqttToken token;

    public ActionListener(Context context, Constants.ActionStateStatus action) {
        this.context = context;
        this.action = action;
        mqttHandler = MqttHandler.getInstance(context);
    }

    @Override
    public void onSuccess(IMqttToken token) {
        Log.d(TAG, ".onSuccess() entered");
        this.token = token;
        switch (action) {
            case CONNECTING:
                handleConnectSuccess();
                break;

            case SUBSCRIBE:
                handleSubscribeSuccess();
                break;

            case PUBLISH:
                handlePublishSuccess();
                break;

            default:
                break;
        }
    }

    @Override
    public void onFailure(IMqttToken token, Throwable throwable) {
        Log.d(TAG, ".onFailure() entered");
        switch (action) {
            case CONNECTING:
                handleConnectFailure(throwable);
                break;

            case SUBSCRIBE:
                handleSubscribeFailure(throwable);
                break;

            case PUBLISH:
                handlePublishFailure(throwable);
                break;

            default:
                break;
        }
    }

    /**
     * Called on successful connection to the MQTT broker. Initiates a subscription to the passenger
     * inbox topic
     */
    private void handleConnectSuccess() {
        Log.d(TAG, ".handleConnectSuccess() entered");

        // subscribe to the passenger inbox topic
        mqttHandler.subscribe("/esp8266/#", 0);
    }

    /**
     * Called on successful subscription to the MQTT topic. In case of the initial subscription,
     * this method will publish Last Will and Testament, current location and paring messages.
     */
    private void handleSubscribeSuccess() {
        Log.d(TAG, ".handleSubscribeSuccess() entered");
    }

    private void handlePublishSuccess() {
        Log.d(TAG, ".handlePublishSuccess() entered");
    }

    private void handleConnectFailure(Throwable throwable) {
        Log.e(TAG, ".handleConnectFailure() entered");
        Log.e(TAG, ".handleConnectFailure() - Failed with exception", throwable.getCause());
        throwable.printStackTrace();
    }

    private void handleSubscribeFailure(Throwable throwable) {
        Log.e(TAG, ".handleSubscribeFailure() entered");
        Log.e(TAG, ".handleSubscribeFailure() - Failed with exception", throwable.getCause());
    }

    private void handlePublishFailure(Throwable throwable) {
        Log.e(TAG, ".handlePublishFailure() entered");
        Log.e(TAG, ".handlePublishFailure() - Failed with exception", throwable.getCause());
    }
}
