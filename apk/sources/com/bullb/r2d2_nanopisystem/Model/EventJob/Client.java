package com.bullb.r2d2_nanopisystem.Model.EventJob;

import com.google.gson.annotations.SerializedName;

/* JADX INFO: loaded from: classes.dex */
public class Client {

    @SerializedName("device_name")
    private String deviceName;

    @SerializedName("uuid")
    private String uuid;

    public Client(String uuid, String deviceName) {
        this.uuid = uuid;
        this.deviceName = deviceName;
    }

    boolean isSameClient(Client compareClient) {
        return this.uuid.equals(compareClient.uuid);
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof Client)) {
            return false;
        }
        Client compareObj = (Client) obj;
        return this.uuid.equals(compareObj.uuid);
    }
}
