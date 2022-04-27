package com.example.webrtcandroid;

import android.content.Context;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONObject;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class SignalManager {

    private static final String TAG = "SignalManager";

    private static SignalManager mInstance;
    private OnSignalEventListener mOnSignalEventListener;

    private Socket mSocket;
    private String mRoomName;

    public interface OnSignalEventListener {
        void onConnected();

        void onConnecting();

        void onDisconnected();

        void onUserJoined(String roomName, String userID);

        void onUserLeaved(String roomName, String userID);

        void onRemoteUserJoined(String roomName);

        void onRemoteUserLeaved(String roomName, String userID);

        void onRoomFull(String roomName, String userID);

        void onMessage(JSONObject message);
    }

    public static SignalManager getInstance() {
        synchronized (SignalManager.class) {
            if (mInstance == null) {
                mInstance = new SignalManager();
            }
        }
        return mInstance;
    }

    public void setSignalEventListener(final OnSignalEventListener listener) {
        if (mOnSignalEventListener == null) {
            mOnSignalEventListener = listener;
        }
    }

    public void connectSignal(Context context) {
        Log.i(TAG, "connectSignal: ");
        try {
            mSocket = IO.socket(context.getResources().getString(R.string.default_server));
            mSocket.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }
    }

    public void joinRoom(String roomName) {
        Log.i(TAG, "joinRoom: ");
        mRoomName = roomName;
        mSocket.emit("join", mRoomName);
    }

    public void invite() {
        Log.i(TAG, "invite: ");
        mSocket.emit("invite", mRoomName);
    }

    public void leaveRoom() {
        Log.i(TAG, "leaveRoom: " + mRoomName);
        if (mSocket == null) {
            return;
        }

        mSocket.emit("leave", mRoomName);
    }

    public void releaseSocket() {
        if (mSocket != null) {
            mSocket.off();
            mSocket.disconnect();
            mSocket.close();
            mSocket = null;
            EventBus.getDefault().post(new SignalEvent(SignalEvent.TYPE_DISCONNECT));
        }
    }

    public void sendMessage(JSONObject message) {
        Log.i(TAG, "broadcast: " + message);
        if (mSocket == null) {
            return;
        }
        mSocket.emit("message", mRoomName, message);
    }

    //侦听从服务器收到的消息
    public void listenSignalEvents() {

        if (mSocket == null) {
            return;
        }

        mSocket.on(Socket.EVENT_CONNECT_ERROR, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.e(TAG, "onConnectError: " + args);
            }
        });

        mSocket.on(Socket.EVENT_ERROR, new Emitter.Listener() {
            @Override
            public void call(Object... args) {

                Log.e(TAG, "onError: " + args);
            }
        });

        mSocket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String sessionId = mSocket.id();
                Log.i(TAG, "onConnected");
                EventBus.getDefault().post(new SignalEvent(SignalEvent.TYPE_CONNECT));
                if (mOnSignalEventListener != null) {
                    mOnSignalEventListener.onConnected();
                }
            }
        });

        mSocket.on(Socket.EVENT_CONNECTING, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i(TAG, "onConnecting");
                if (mOnSignalEventListener != null) {
                    mOnSignalEventListener.onConnecting();
                }
            }
        });

        mSocket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i(TAG, "onDisconnected");
                EventBus.getDefault().post(new SignalEvent(SignalEvent.TYPE_DISCONNECT));
                if (mOnSignalEventListener != null) {
                    mOnSignalEventListener.onDisconnected();
                }
            }
        });

        mSocket.on("joined", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String roomName = (String) args[0];
                String userId = (String) args[1];
                if (/*!mUserId.equals(userId) &&*/ mOnSignalEventListener != null) {
                    //mOnSignalEventListener.onRemoteUserJoined(userId);
                    mOnSignalEventListener.onUserJoined(roomName, userId);
                }
                //Log.i(TAG, "onRemoteUserJoined: " + userId);
                Log.i(TAG, "onUserJoined, room:" + roomName + "uid:" + userId);
            }
        });

        mSocket.on("invited", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i(TAG, "invited");
                SignalEvent event = new SignalEvent(SignalEvent.TYPE_JUMP);
                EventBus.getDefault().post(event);
            }
        });

        mSocket.on("leaved", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String roomName = (String) args[0];
                String userId = (String) args[1];
                if (mOnSignalEventListener != null) {
                    mOnSignalEventListener.onUserLeaved(roomName, userId);
                }
                Log.i(TAG, "onUserLeaved, room:" + roomName + "uid:" + userId);
            }
        });

        mSocket.on("otherjoin", new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                String roomName = (String) args[0];
                String userId = (String) args[1];
                if (mOnSignalEventListener != null) {
                    mOnSignalEventListener.onRemoteUserJoined(roomName);
                }
                Log.i(TAG, "onRemoteUserJoined, room:" + roomName + "uid:" + userId);
            }
        });

        mSocket.on("bye", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String roomName = (String) args[0];
                String userId = (String) args[1];
                Log.i(TAG, "mOnSignalEventListener:" + mOnSignalEventListener);
                if (mOnSignalEventListener != null) {
                    mOnSignalEventListener.onRemoteUserLeaved(roomName, userId);
                }
                Log.i(TAG, "onRemoteUserLeaved, room:" + roomName + "uid:" + userId);
            }
        });

        mSocket.on("full", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String roomName = (String) args[0];
                String userId = (String) args[1];

                if (mOnSignalEventListener != null) {
                    mOnSignalEventListener.onRoomFull(roomName, userId);
                }

                Log.i(TAG, "onRoomFull, room:" + roomName + "uid:" + userId);
            }
        });

        mSocket.on("message", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String roomName = (String) args[0];
                JSONObject msg = (JSONObject) args[1];

                if (mOnSignalEventListener != null) {
                    mOnSignalEventListener.onMessage(msg);
                }

                Log.i(TAG, "onMessage, room:" + roomName + "data:" + msg);
            }
        });
    }
}
