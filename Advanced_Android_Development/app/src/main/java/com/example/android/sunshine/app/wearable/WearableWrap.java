package com.example.android.sunshine.app.wearable;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.shared.WearableUtility;

import java.io.ByteArrayOutputStream;


public class WearableWrap {
    private GoogleApiClient mGoogleApiClient;
    private Context context;
    private static final String TAG = "WearableWrap";

    public WearableWrap(Context context) {
        this.context = context;
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(context)
                    .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        public void onConnected(Bundle bundle) {
                            Log.d(TAG, "Connected");
                        }

                        @Override
                        public void onConnectionSuspended(int i) {
                            Log.d(TAG, "Suspended");
                        }
                    })
                    .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(ConnectionResult connectionResult) {
                            Log.w(TAG, "Failed");
                        }
                    })
                    .addApi(Wearable.API)
                    .build();
        }
    }

    public void sendWeatherInfoToWearable(double low, double high, int weatherId) {
        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }

        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(WearableUtility.PATH_WEATHER_INFO);

        final long reqT = System.currentTimeMillis();
        Log.d(TAG, "Sending Weather data /" + reqT);
        putDataMapRequest.getDataMap().putLong(WearableUtility.TIMESTAMP, reqT);
        putDataMapRequest.getDataMap().putDouble(WearableUtility.MIN_TEMP, low);
        putDataMapRequest.getDataMap().putDouble(WearableUtility.MAX_TEMP, high);
        int artId = Utility.getArtResourceForWeatherCondition(weatherId);
        Bitmap icon = BitmapFactory.decodeResource(context.getResources(), artId);
        Asset asset = createAssetFromBitmap(icon);
        putDataMapRequest.getDataMap().putAsset(WearableUtility.WEATHER_ICON, asset);

        PutDataRequest request = putDataMapRequest.asPutDataRequest();
        request.setUrgent();

        Log.d(TAG, "Send: high:" + high + ", low:" + low + ", weather ID: " + weatherId);

        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        if (!dataItemResult.getStatus().isSuccess()) {
                            Log.d(TAG, "Failed to send weather data /" + reqT);
                        } else {
                            Log.d(TAG, "Successfully sent weather data /" + reqT);
                        }
                    }
                });
    }


    private static Asset createAssetFromBitmap(Bitmap bitmap) {
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
        return Asset.createFromBytes(byteStream.toByteArray());
    }
}
