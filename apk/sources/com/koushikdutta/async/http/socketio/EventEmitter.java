package com.koushikdutta.async.http.socketio;

import com.koushikdutta.async.util.HashList;
import java.util.Iterator;
import java.util.List;
import org.json.JSONArray;

/* JADX INFO: loaded from: classes.dex */
public class EventEmitter {
    HashList<EventCallback> callbacks = new HashList<>();

    interface OnceCallback extends EventCallback {
    }

    void onEvent(String event, JSONArray arguments, Acknowledge acknowledge) {
        List<EventCallback> list = this.callbacks.get(event);
        if (list != null) {
            Iterator<EventCallback> iter = list.iterator();
            while (iter.hasNext()) {
                EventCallback cb = iter.next();
                cb.onEvent(arguments, acknowledge);
                if (cb instanceof OnceCallback) {
                    iter.remove();
                }
            }
        }
    }

    public void addListener(String event, EventCallback callback) {
        on(event, callback);
    }

    public void once(String event, final EventCallback callback) {
        on(event, new OnceCallback() { // from class: com.koushikdutta.async.http.socketio.EventEmitter.1
            @Override // com.koushikdutta.async.http.socketio.EventCallback
            public void onEvent(JSONArray arguments, Acknowledge acknowledge) {
                callback.onEvent(arguments, acknowledge);
            }
        });
    }

    public void on(String event, EventCallback callback) {
        this.callbacks.add(event, callback);
    }

    public void removeListener(String event, EventCallback callback) {
        List<EventCallback> list = this.callbacks.get(event);
        if (list != null) {
            list.remove(callback);
        }
    }
}
