package org.java_websocket.handshake;

/* JADX INFO: loaded from: classes.dex */
public class HandshakeImpl1Client extends HandshakedataImpl1 implements ClientHandshakeBuilder {
    private String resourceDescriptor = "*";

    @Override // org.java_websocket.handshake.ClientHandshakeBuilder
    public void setResourceDescriptor(String resourceDescriptor) throws IllegalArgumentException {
        if (resourceDescriptor == null) {
            throw new IllegalArgumentException("http resource descriptor must not be null");
        }
        this.resourceDescriptor = resourceDescriptor;
    }

    @Override // org.java_websocket.handshake.ClientHandshake
    public String getResourceDescriptor() {
        return this.resourceDescriptor;
    }
}
