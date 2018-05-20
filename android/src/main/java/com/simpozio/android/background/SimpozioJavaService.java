package com.simpozio.android.background;

import android.annotation.SuppressLint;

import com.facebook.react.modules.core.DeviceEventManagerModule;

import com.simpozio.android.background.event.Events;
import com.simpozio.android.background.heartbeat.*;
import com.facebook.react.bridge.*;
import com.simpozio.android.background.trace.TraceService;

import android.content.*;
import android.os.*;
import android.util.Log;

import static android.content.Context.POWER_SERVICE;
import static android.os.PowerManager.PARTIAL_WAKE_LOCK;

import static com.simpozio.android.background.event.Events.*;
import static com.simpozio.android.background.ServiceURL.*;

public final class SimpozioJavaService extends ReactContextBaseJavaModule {

    private static final String LOG_TAG = "SimpozioJavaService";

    public static final String HEARTBEAT_INTENT_ACTION = "background.service.heartbeat";
    public static final String FEEDBACK_INTENT_ACTION = "background.service.feedback";
    public static final String TRACE_INTENT_ACTION = "background.service.trace";

    public static final String REQUEST_BODY_EVENT_BUNDLE = "request.body.event.bundle";
    public static final String FEEDBACK_EVENT_BUNDLE = "feedback.event.bundle";
    public static final String HEADERS_EVENT_BUNDLE = "headers.event.bundle";

    public static final String SIMPOZIO_URL_EXTRA = "simpozio.url";

    private DeviceEventManagerModule.RCTDeviceEventEmitter eventEmitter;
    private PowerManager.WakeLock wakeLock;

    @SuppressLint("WakelockTimeout")
    public SimpozioJavaService(ReactApplicationContext context) {
        super(context);
    }

    @SuppressLint("WakelockTimeout")
    @Override
    public void initialize() {
        Log.d(LOG_TAG, "initialization hook started");
        this.eventEmitter = getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class);
        this.wakeLock = ((PowerManager) getReactApplicationContext().getSystemService(POWER_SERVICE)).newWakeLock(PARTIAL_WAKE_LOCK, "wl");
        this.wakeLock.acquire();
        //
        this.getReactApplicationContext().registerReceiver(createReceiver(), getFeedbackIntentFilter());
        Log.d(LOG_TAG, "initialization hook finished");
    }

    // React Native API

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * @param metadata is object {"baseUrl":"string", "call":"string", "headers":{...}, "body":{...} or [...]}
     */

    @ReactMethod
    public void start(ReadableMap metadata) {
        Log.d(LOG_TAG, "start started");
        String url = metadata.getString("call");
        switch (url) {
            case TRACE_URL:
                this.startTraceService(metadata);
                break;
            case HEARTBEAT_URL:
                this.startHeartbeatService(metadata);
                break;
            default: {
                this.fireEvent(Events.unknownUrl(url));
            }
        }
        Log.d(LOG_TAG, "start finished");
    }

    @ReactMethod
    public void stop(String url) {
        Log.d(LOG_TAG, "stop started");
        switch (url) {
            case TRACE_URL:
                this.stopTraceService();
                break;
            case HEARTBEAT_URL:
                this.stopHeartbeatService();
                break;
            default: {
                this.fireEvent(Events.unknownUrl(url));
            }
        }
        Log.d(LOG_TAG, "stop finished");
    }

    /**
     * @param metadata is object {"baseUrl":"string", "call":"string", "headers":{...}, "body":{...} or [...]}
     */

    @ReactMethod
    public void update(ReadableMap metadata) {
        Log.d(LOG_TAG, "update started");
        String url = metadata.getString("call");
        switch (url) {
            case TRACE_URL:
                this.sendBroadcast(toTraceIntent(metadata));
                break;
            case HEARTBEAT_URL:
                this.sendBroadcast(toHeartbeatIntent(metadata));
                break;
            default: {
                this.fireEvent(Events.unknownUrl(url));
            }
        }
        Log.d(LOG_TAG, "update finished");
    }

    @ReactMethod
    public void releaseWakeLock() {
        Log.d(LOG_TAG, "releaseWakeLock started");
        this.wakeLock.release();
        Log.d(LOG_TAG, "releaseWakeLock finished");
    }


    private void startTraceService(ReadableMap metadata) {
        throw new UnsupportedOperationException();
    }

    private void startHeartbeatService(ReadableMap metadata) {
        Log.d(LOG_TAG, "startHeartbeatService started");
        Intent heartbeatServiceIntent = getHeartbeatServiceIntent();
        acceptExtra(metadata, heartbeatServiceIntent);
        this.getReactApplicationContext().startService(heartbeatServiceIntent);
        Log.d(LOG_TAG, "startHeartbeatService finished");
    }

    private BroadcastReceiver createReceiver() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(LOG_TAG, "onReceive started");
                SimpozioJavaService.this.fireEvent(intent.getBundleExtra(FEEDBACK_EVENT_BUNDLE));
                Log.d(LOG_TAG, "onReceive finished");
            }
        };
    }

    private Intent toTraceIntent(ReadableMap metadata) {
        throw new UnsupportedOperationException();
//        Intent metadataIntent = new Intent(TRACE_INTENT_ACTION);
//        return acceptExtra(metadata, metadataIntent);
    }

    private Intent toHeartbeatIntent(ReadableMap metadata) {
        Intent metadataIntent = new Intent(HEARTBEAT_INTENT_ACTION);
        return acceptExtra(metadata, metadataIntent);
    }

    private void stopTraceService() {
        throw new UnsupportedOperationException();
//        this.getReactApplicationContext().stopService(getTraceServiceIntent());
    }

    private void stopHeartbeatService() {
        Log.d(LOG_TAG, "stopHeartbeatService started");
        this.getReactApplicationContext().stopService(getHeartbeatServiceIntent());
        Log.d(LOG_TAG, "stopHeartbeatService finished");
    }

    private void sendBroadcast(Intent intent) {
        Log.d(LOG_TAG, "sendBroadcast started");
        this.getReactApplicationContext().sendBroadcast(intent);
        Log.d(LOG_TAG, "sendBroadcast finished");
    }

    private void fireEvent(Bundle event) {
        this.fireEvent(Events.toWritableMap(event));
    }

    private void fireEvent(WritableMap event) {
        Log.d(LOG_TAG, "fireEvent started");
        this.eventEmitter.emit(event.getString(EVENT_TYPE), event);
        Log.d(LOG_TAG, "sendBroadcast finished");
    }

    private Intent getHeartbeatServiceIntent() {
        return new Intent(getReactApplicationContext(), HeartbeatService.class);
    }

    private Intent getTraceServiceIntent() {
        return new Intent(getReactApplicationContext(), TraceService.class);
    }

    private static Intent acceptExtra(ReadableMap metadata, Intent metadataIntent) {
        // simpozio address
        metadataIntent.putExtra(SIMPOZIO_URL_EXTRA, metadata.getString("baseUrl") + metadata.getString("call"));
        // headers
        acceptHeadersExtra(metadata, metadataIntent);
        // request body
        ReadableType requestBodyType = metadata.getType("requestBody");
        if (requestBodyType.equals(ReadableType.Map)) {
            acceptMapRequestBodyExtra(metadata, metadataIntent);
        } else if (requestBodyType.equals(ReadableType.Array)) {
            throw new UnsupportedOperationException();
        } else {
            throw new UnsupportedOperationException();
        }
        return metadataIntent;
    }

    private static void acceptMapRequestBodyExtra(ReadableMap metadata, Intent metadataIntent) {
        ReadableMap requestBody = metadata.getMap("requestBody");
        ReadableMapKeySetIterator requestBodyKeys = requestBody.keySetIterator();
        Bundle requestBodyEventBundle = new Bundle();
        while (requestBodyKeys.hasNextKey()) {
            String key = requestBodyKeys.nextKey();
            requestBodyEventBundle.putString(key, requestBody.getString(key));
        }
        metadataIntent.putExtra(REQUEST_BODY_EVENT_BUNDLE, requestBodyEventBundle);
    }

    private static void acceptHeadersExtra(ReadableMap metadata, Intent metadataIntent) {
        ReadableMap headers = metadata.getMap("headers");
        ReadableMapKeySetIterator headerKeys = headers.keySetIterator();
        Bundle headersEventBundle = new Bundle();
        while (headerKeys.hasNextKey()) {
            String key = headerKeys.nextKey();
            headersEventBundle.putString(key, headers.getString(key));
        }
        metadataIntent.putExtra(HEADERS_EVENT_BUNDLE, headersEventBundle);
    }

//    private static JSONArray convertArrayToJson(ReadableArray readableArray) throws JSONException {
//        JSONArray array = new JSONArray();
//        for (int i = 0; i < readableArray.size(); i++) {
//            switch (readableArray.getType(i)) {
//                case Null:
//                    break;
//                case Boolean:
//                    array.put(readableArray.getBoolean(i));
//                    break;
//                case Number:
//                    array.put(readableArray.getDouble(i));
//                    break;
//                case String:
//                    array.put(readableArray.getString(i));
//                    break;
//                case Map:
//                    array.put(convertMapToJson(readableArray.getMap(i)));
//                    break;
//                case Array:
//                    array.put(convertArrayToJson(readableArray.getArray(i)));
//                    break;
//            }
//        }
//        return array;
//    }

    private static IntentFilter getFeedbackIntentFilter() {
        return new IntentFilter(FEEDBACK_INTENT_ACTION);
    }
}
