package com.koushikdutta.async.http.socketio;

import org.json.JSONArray;

/* JADX INFO: loaded from: classes.dex */
public interface EventCallback {
    void onEvent(JSONArray jSONArray, Acknowledge acknowledge);
}
