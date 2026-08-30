package com.koushikdutta.async.http.cache;

import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/* JADX INFO: loaded from: classes.dex */
final class RawHeaders {
    private static final Comparator<String> FIELD_NAME_COMPARATOR = new Comparator<String>() { // from class: com.koushikdutta.async.http.cache.RawHeaders.1
        @Override // java.util.Comparator
        public int compare(String a, String b) {
            if (a == b) {
                return 0;
            }
            if (a == null) {
                return -1;
            }
            if (b == null) {
                return 1;
            }
            return String.CASE_INSENSITIVE_ORDER.compare(a, b);
        }
    };
    private String responseMessage;
    private String statusLine;
    private final List<String> namesAndValues = new ArrayList(20);
    private int httpMinorVersion = 1;
    private int responseCode = -1;

    public RawHeaders() {
    }

    public RawHeaders(RawHeaders copyFrom) {
        copy(copyFrom);
    }

    public void copy(RawHeaders copyFrom) {
        this.namesAndValues.addAll(copyFrom.namesAndValues);
        this.statusLine = copyFrom.statusLine;
        this.httpMinorVersion = copyFrom.httpMinorVersion;
        this.responseCode = copyFrom.responseCode;
        this.responseMessage = copyFrom.responseMessage;
    }

    public void setStatusLine(String statusLine) {
        String statusLine2 = statusLine.trim();
        this.statusLine = statusLine2;
        if (statusLine2 != null && statusLine2.startsWith("HTTP/")) {
            String statusLine3 = statusLine2.trim();
            int mark = statusLine3.indexOf(" ") + 1;
            if (mark != 0) {
                if (statusLine3.charAt(mark - 2) != '1') {
                    this.httpMinorVersion = 0;
                }
                int last = mark + 3;
                if (last > statusLine3.length()) {
                    last = statusLine3.length();
                }
                this.responseCode = Integer.parseInt(statusLine3.substring(mark, last));
                if (last + 1 <= statusLine3.length()) {
                    this.responseMessage = statusLine3.substring(last + 1);
                }
            }
        }
    }

    public String getStatusLine() {
        return this.statusLine;
    }

    public int getHttpMinorVersion() {
        if (this.httpMinorVersion != -1) {
            return this.httpMinorVersion;
        }
        return 1;
    }

    public int getResponseCode() {
        return this.responseCode;
    }

    public String getResponseMessage() {
        return this.responseMessage;
    }

    public void addLine(String line) {
        int index = line.indexOf(":");
        if (index == -1) {
            add("", line);
        } else {
            add(line.substring(0, index), line.substring(index + 1));
        }
    }

    public void add(String fieldName, String value) {
        if (fieldName == null) {
            throw new IllegalArgumentException("fieldName == null");
        }
        if (value == null) {
            System.err.println("Ignoring HTTP header field '" + fieldName + "' because its value is null");
        } else {
            this.namesAndValues.add(fieldName);
            this.namesAndValues.add(value.trim());
        }
    }

    public void removeAll(String fieldName) {
        for (int i = 0; i < this.namesAndValues.size(); i += 2) {
            if (fieldName.equalsIgnoreCase(this.namesAndValues.get(i))) {
                this.namesAndValues.remove(i);
                this.namesAndValues.remove(i);
            }
        }
    }

    public void addAll(String fieldName, List<String> headerFields) {
        for (String value : headerFields) {
            add(fieldName, value);
        }
    }

    public void set(String fieldName, String value) {
        removeAll(fieldName);
        add(fieldName, value);
    }

    public int length() {
        return this.namesAndValues.size() / 2;
    }

    public String getFieldName(int index) {
        int fieldNameIndex = index * 2;
        if (fieldNameIndex < 0 || fieldNameIndex >= this.namesAndValues.size()) {
            return null;
        }
        return this.namesAndValues.get(fieldNameIndex);
    }

    public String getValue(int index) {
        int valueIndex = (index * 2) + 1;
        if (valueIndex < 0 || valueIndex >= this.namesAndValues.size()) {
            return null;
        }
        return this.namesAndValues.get(valueIndex);
    }

    public String get(String fieldName) {
        for (int i = this.namesAndValues.size() - 2; i >= 0; i -= 2) {
            if (fieldName.equalsIgnoreCase(this.namesAndValues.get(i))) {
                return this.namesAndValues.get(i + 1);
            }
        }
        return null;
    }

    public RawHeaders getAll(Set<String> fieldNames) {
        RawHeaders result = new RawHeaders();
        for (int i = 0; i < this.namesAndValues.size(); i += 2) {
            String fieldName = this.namesAndValues.get(i);
            if (fieldNames.contains(fieldName)) {
                result.add(fieldName, this.namesAndValues.get(i + 1));
            }
        }
        return result;
    }

    public String toHeaderString() {
        StringBuilder result = new StringBuilder(256);
        result.append(this.statusLine).append("\r\n");
        for (int i = 0; i < this.namesAndValues.size(); i += 2) {
            result.append(this.namesAndValues.get(i)).append(": ").append(this.namesAndValues.get(i + 1)).append("\r\n");
        }
        result.append("\r\n");
        return result.toString();
    }

    public Map<String, List<String>> toMultimap() {
        Map<String, List<String>> result = new TreeMap<>(FIELD_NAME_COMPARATOR);
        for (int i = 0; i < this.namesAndValues.size(); i += 2) {
            String fieldName = this.namesAndValues.get(i);
            String value = this.namesAndValues.get(i + 1);
            List<String> allValues = new ArrayList<>();
            List<String> otherValues = result.get(fieldName);
            if (otherValues != null) {
                allValues.addAll(otherValues);
            }
            allValues.add(value);
            result.put(fieldName, Collections.unmodifiableList(allValues));
        }
        if (this.statusLine != null) {
            result.put(null, Collections.unmodifiableList(Collections.singletonList(this.statusLine)));
        }
        return Collections.unmodifiableMap(result);
    }

    public static RawHeaders fromMultimap(Map<String, List<String>> map) {
        RawHeaders result = new RawHeaders();
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            String fieldName = entry.getKey();
            List<String> values = entry.getValue();
            if (fieldName != null) {
                result.addAll(fieldName, values);
            } else if (!values.isEmpty()) {
                result.setStatusLine(values.get(values.size() - 1));
            }
        }
        return result;
    }

    public static RawHeaders parse(String payload) {
        String[] lines = payload.split("\n");
        RawHeaders headers = new RawHeaders();
        for (String str : lines) {
            String line = str.trim();
            if (!TextUtils.isEmpty(line)) {
                if (headers.getStatusLine() == null) {
                    headers.setStatusLine(line);
                } else {
                    headers.addLine(line);
                }
            }
        }
        return headers;
    }
}
