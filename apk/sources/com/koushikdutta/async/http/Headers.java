package com.koushikdutta.async.http;

import android.text.TextUtils;
import com.koushikdutta.async.util.TaggedList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public class Headers {
    final Multimap map = new Multimap() { // from class: com.koushikdutta.async.http.Headers.1
        @Override // com.koushikdutta.async.http.Multimap
        protected List<String> newList() {
            return new TaggedList();
        }
    };

    public Headers() {
    }

    public Headers(Map<String, List<String>> mm) {
        this.map.putAll(mm);
    }

    public Multimap getMultiMap() {
        return this.map;
    }

    public List<String> getAll(String header) {
        return this.map.get(header.toLowerCase(Locale.US));
    }

    public String get(String header) {
        return this.map.getString(header.toLowerCase(Locale.US));
    }

    public Headers set(String header, String value) {
        if (value != null && (value.contains("\n") || value.contains("\r"))) {
            throw new IllegalArgumentException("value must not contain a new line or line feed");
        }
        String lc = header.toLowerCase(Locale.US);
        this.map.put(lc, value);
        TaggedList<String> list = (TaggedList) this.map.get(lc);
        list.tagNull(header);
        return this;
    }

    public Headers add(String header, String value) {
        String lc = header.toLowerCase(Locale.US);
        this.map.add(lc, value);
        TaggedList<String> list = (TaggedList) this.map.get(lc);
        list.tagNull(header);
        return this;
    }

    public Headers addLine(String line) {
        if (line != null) {
            String[] parts = line.trim().split(":", 2);
            if (parts.length == 2) {
                add(parts[0].trim(), parts[1].trim());
            } else {
                add(parts[0].trim(), "");
            }
        }
        return this;
    }

    public Headers addAll(String header, List<String> values) {
        for (String v : values) {
            add(header, v);
        }
        return this;
    }

    public Headers addAll(Map<String, List<String>> m) {
        for (String key : m.keySet()) {
            for (String value : m.get(key)) {
                add(key, value);
            }
        }
        return this;
    }

    public Headers addAll(Headers headers) {
        this.map.putAll(headers.map);
        return this;
    }

    public List<String> removeAll(String header) {
        return (List) this.map.remove(header.toLowerCase(Locale.US));
    }

    public String remove(String header) {
        List<String> r = removeAll(header.toLowerCase(Locale.US));
        if (r == null || r.size() == 0) {
            return null;
        }
        return r.get(0);
    }

    public Headers removeAll(Collection<String> headers) {
        for (String header : headers) {
            remove(header);
        }
        return this;
    }

    public StringBuilder toStringBuilder() {
        StringBuilder result = new StringBuilder(256);
        for (String key : this.map.keySet()) {
            TaggedList<String> list = (TaggedList) this.map.get(key);
            for (String v : list) {
                result.append((String) list.tag()).append(": ").append(v).append("\r\n");
            }
        }
        result.append("\r\n");
        return result;
    }

    public String toString() {
        return toStringBuilder().toString();
    }

    public String toPrefixString(String prefix) {
        return toStringBuilder().insert(0, prefix + "\r\n").toString();
    }

    public static Headers parse(String payload) {
        String[] lines = payload.split("\n");
        Headers headers = new Headers();
        for (String str : lines) {
            String line = str.trim();
            if (!TextUtils.isEmpty(line)) {
                headers.addLine(line);
            }
        }
        return headers;
    }
}
