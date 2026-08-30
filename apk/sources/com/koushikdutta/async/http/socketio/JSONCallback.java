package com.koushikdutta.async.http.socketio;

import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
public interface JSONCallback {
    void onJSON(JSONObject jSONObject, Acknowledge acknowledge);
}
