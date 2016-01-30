package com.example.android.sunshine.app.wearable;

import android.util.Log;

import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.WearableListenerService;
import com.shared.WearableUtility;

public class WatchInitService extends WearableListenerService {
    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                String path = dataEvent.getDataItem().getUri().getPath();
                if (path.equals(WearableUtility.PATH_INIT)) {
                    SunshineSyncAdapter.syncImmediately(this);
                }
            }
        }
    }
}
