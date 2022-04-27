package com.example.webrtcandroid;

public class SignalEvent {
    public static final int TYPE_JUMP = 0x01;
    public static final int TYPE_RECONNECT = 0x02;
    public static final int TYPE_CONNECT = 0x03;
    public static final int TYPE_DISCONNECT = 0x04;
    private int type;

    public SignalEvent(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }
}
