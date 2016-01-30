package com.example.android.sunshine.app.wearable;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.shustrik.wearable.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.shared.WearableUtility;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;


public class WeatherWatchFace extends CanvasWatchFaceService {
    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    private static final String TAG = "WeatherWatchFace";

    public LayoutInflater mInflater;

    @Override
    public Engine onCreateEngine() {
        mInflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<WeatherWatchFace.Engine> mWeakReference;

        public EngineHandler(WeatherWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            WeatherWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine
            implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener {

        final Handler mUpdateTimeHandler = new EngineHandler(this);

        boolean mRegisteredTimeZoneReceiver = false;

        Time mTime;

        SimpleDateFormat timeFormat = new SimpleDateFormat("kk:mm");
        SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM, yyyy");
        View view;
        TextView timeView;
        TextView dateView;
        TextView tempView;
        TextView noInfoView;
        ImageView icon;

        Double receivedMinTemp = null;
        Double receivedMaxTemp = null;
        Bitmap receivedIcon = null;


        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        GoogleApiClient mGoogleApiClient;

            /**
         * -----------------------------Create/Destroy-----------
         */

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            view = mInflater.inflate(R.layout.watchface, null);
            timeView = (TextView) view.findViewById(R.id.time_text);
            dateView = (TextView) view.findViewById(R.id.date_text);
            tempView = (TextView) view.findViewById(R.id.min_max_temp);
            noInfoView = (TextView) view.findViewById(R.id.no_info);
            icon = (ImageView) view.findViewById(R.id.weather_pic);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WeatherWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            mTime = new Time();

            mGoogleApiClient = new GoogleApiClient.Builder(WeatherWatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApiIfAvailable(Wearable.API)
                .build();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
            super.onDestroy();
        }

        /**
         * -----------------------------DRAW--------------------
         */

//        @Override
//        public void onPropertiesChanged(Bundle properties) {
//            super.onPropertiesChanged(properties);
//            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
//        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            int widthSpec = View.MeasureSpec.makeMeasureSpec(bounds.width(), View.MeasureSpec.EXACTLY);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(bounds.height(), View.MeasureSpec.EXACTLY);
            view.measure(widthSpec, heightSpec);

            view.layout(0, 0, bounds.width(), bounds.height());

            mTime.setToNow();
            timeView.setText(timeFormat.format(mTime.toMillis(true)));
            dateView.setText(dateFormat.format(mTime.toMillis(true)));
            //tempView.setText(String.format("%s'...%s'", receivedMinTemp, receivedMaxTemp));
            if (receivedMinTemp != null && receivedMaxTemp != null) {
                tempView.setText(String.format(getApplicationContext()
                        .getString(R.string.format_temperature), receivedMinTemp, receivedMaxTemp));
                tempView.setVisibility(View.VISIBLE);
                noInfoView.setVisibility(View.GONE);

            } else {
                tempView.setVisibility(View.GONE);
                noInfoView.setVisibility(View.VISIBLE);
            }

            if (receivedIcon != null) {
                icon.setVisibility(View.VISIBLE);
                icon.setImageResource(0);
                icon.setImageBitmap(receivedIcon);
            } else {
                icon.setVisibility(View.GONE);
            }

            view.draw(canvas);
        }

        /**
         * ----------------------------Time/activity workflow-------------------
         */

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if (visible) {
                mGoogleApiClient.connect();
                registerReceiver();
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            WeatherWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            WeatherWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }


        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        /**
         * Income Data Processing
         */
        private void updateUiForConfigDataMap(final DataMap config) {
            for (String configKey : config.keySet()) {
                if (WearableUtility.MIN_TEMP.equals(configKey)) {
                    receivedMinTemp = config.getDouble(configKey);
                    Log.w(TAG, "New min temp: " + receivedMinTemp);
                } else if (WearableUtility.MAX_TEMP.equals(configKey)) {
                    receivedMaxTemp = config.getDouble(configKey);
                    Log.w("ANNA", "New max temp: " + receivedMaxTemp);
                } else if (WearableUtility.WEATHER_ICON.equals(configKey)) {
                    new LoadBitmapAsyncTask().execute(config.getAsset(configKey));
                }
            }
        }

        /*
        * Hello, Data Layer sample
        */
        private class LoadBitmapAsyncTask extends AsyncTask<Asset, Void, Bitmap> {
            @Override
            protected Bitmap doInBackground(Asset... params) {
                if (params.length > 0) {
                    Asset asset = params[0];
                    InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                            mGoogleApiClient, asset).await().getInputStream();
                    if (assetInputStream == null) {
                        Log.w(TAG, "Requested an unknown Asset.");
                        return null;
                    }
                    return BitmapFactory.decodeStream(assetInputStream);
                } else {
                    Log.w(TAG, "Asset must be non-null");
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap != null) {
                    Log.d(TAG, "Bitmap is ready");
                    receivedIcon = bitmap;
                }
            }
        }

        /**
         * --------------------------CALLBACKS---------------------------------
         */

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            for (DataEvent dataEvent : dataEvents) {
                if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                    continue;
                }

                DataItem dataItem = dataEvent.getDataItem();
                if (!WearableUtility.PATH_WEATHER_INFO.equals(
                        dataItem.getUri().getPath())) {
                    continue;
                }

                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                DataMap config = dataMapItem.getDataMap();
                Log.d(TAG, "Config DataItem updated:" + config);
                updateUiForConfigDataMap(config);
            }
        }

        @Override
        public void onConnected(Bundle connectionHint) {
            Log.d(TAG, "onConnected: " + connectionHint);
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);

            requestInitData();
        }

        @Override
        public void onConnectionSuspended(int cause) {
            Log.d(TAG, "onConnectionSuspended: " + cause);
        }

        @Override
        public void onConnectionFailed(ConnectionResult result) {
            Log.w(TAG, "onConnectionFailed: " + result);
        }


        public void requestInitData() {
            final long reqT = System.currentTimeMillis();
            Log.d(TAG, "Requesting init data /" + reqT);
            PutDataMapRequest putDataMapReq = PutDataMapRequest.create(WearableUtility.PATH_INIT);
            putDataMapReq.getDataMap().putLong(WearableUtility.TIMESTAMP, System.currentTimeMillis());
            PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
            putDataReq.setUrgent();
            PendingResult<DataApi.DataItemResult> pendingResult =
                    Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);
            pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(final DataApi.DataItemResult result) {
                    if (result.getStatus().isSuccess()) {
                        Log.d(TAG, "Pending result: success /" + reqT);
                    } else {
                        Log.w(TAG, result.getStatus().toString() + " /" + reqT);
                    }
                }
            });

        }


        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    requestInitData();
                    break;
            }
            invalidate();
        }
    }
}
