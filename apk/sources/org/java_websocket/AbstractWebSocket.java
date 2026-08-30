package org.java_websocket;

import java.util.Collection;
import java.util.Timer;
import java.util.TimerTask;

/* JADX INFO: loaded from: classes.dex */
public abstract class AbstractWebSocket extends WebSocketAdapter {
    private int connectionLostTimeout = 60;
    private Timer connectionLostTimer;
    private TimerTask connectionLostTimerTask;
    private boolean tcpNoDelay;

    protected abstract Collection<WebSocket> connections();

    public int getConnectionLostTimeout() {
        return this.connectionLostTimeout;
    }

    public void setConnectionLostTimeout(int connectionLostTimeout) {
        this.connectionLostTimeout = connectionLostTimeout;
        if (this.connectionLostTimeout <= 0) {
            stopConnectionLostTimer();
        } else {
            startConnectionLostTimer();
        }
    }

    protected void stopConnectionLostTimer() {
        if (this.connectionLostTimer != null || this.connectionLostTimerTask != null) {
            if (WebSocketImpl.DEBUG) {
                System.out.println("Connection lost timer stoped");
            }
            cancelConnectionLostTimer();
        }
    }

    protected void startConnectionLostTimer() {
        if (this.connectionLostTimeout <= 0) {
            if (WebSocketImpl.DEBUG) {
                System.out.println("Connection lost timer deactivated");
            }
        } else {
            if (WebSocketImpl.DEBUG) {
                System.out.println("Connection lost timer started");
            }
            cancelConnectionLostTimer();
            this.connectionLostTimer = new Timer();
            this.connectionLostTimerTask = new TimerTask() { // from class: org.java_websocket.AbstractWebSocket.1
                @Override // java.util.TimerTask, java.lang.Runnable
                public void run() {
                    Collection<WebSocket> con = AbstractWebSocket.this.connections();
                    synchronized (con) {
                        long current = System.currentTimeMillis() - ((long) (AbstractWebSocket.this.connectionLostTimeout * 1500));
                        for (WebSocket conn : con) {
                            if (conn instanceof WebSocketImpl) {
                                if (((WebSocketImpl) conn).getLastPong() < current) {
                                    if (WebSocketImpl.DEBUG) {
                                        System.out.println("Closing connection due to no pong received: " + conn.toString());
                                    }
                                    conn.close(1006);
                                } else {
                                    conn.sendPing();
                                }
                            }
                        }
                    }
                }
            };
            this.connectionLostTimer.scheduleAtFixedRate(this.connectionLostTimerTask, this.connectionLostTimeout * 1000, this.connectionLostTimeout * 1000);
        }
    }

    private void cancelConnectionLostTimer() {
        if (this.connectionLostTimer != null) {
            this.connectionLostTimer.cancel();
            this.connectionLostTimer = null;
        }
        if (this.connectionLostTimerTask != null) {
            this.connectionLostTimerTask.cancel();
            this.connectionLostTimerTask = null;
        }
    }

    public boolean isTcpNoDelay() {
        return this.tcpNoDelay;
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }
}
