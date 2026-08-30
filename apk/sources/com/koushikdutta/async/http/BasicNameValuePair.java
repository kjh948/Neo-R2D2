package com.koushikdutta.async.http;

import android.text.TextUtils;

/* JADX INFO: loaded from: classes.dex */
public class BasicNameValuePair implements NameValuePair, Cloneable {
    private final String name;
    private final String value;

    public BasicNameValuePair(String name, String value) {
        if (name == null) {
            throw new IllegalArgumentException("Name may not be null");
        }
        this.name = name;
        this.value = value;
    }

    @Override // com.koushikdutta.async.http.NameValuePair
    public String getName() {
        return this.name;
    }

    @Override // com.koushikdutta.async.http.NameValuePair
    public String getValue() {
        return this.value;
    }

    public String toString() {
        return this.name + "=" + this.value;
    }

    public boolean equals(Object object) {
        if (object == null) {
            return false;
        }
        if (this == object) {
            return true;
        }
        if (!(object instanceof NameValuePair)) {
            return false;
        }
        BasicNameValuePair that = (BasicNameValuePair) object;
        return this.name.equals(that.name) && TextUtils.equals(this.value, that.value);
    }

    public int hashCode() {
        return this.name.hashCode() ^ this.value.hashCode();
    }

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
