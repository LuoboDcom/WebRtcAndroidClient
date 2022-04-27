package com.example.webrtcandroid;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import androidx.appcompat.app.AppCompatActivity;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity {
    private TextView mTextViewConnectState;
    private boolean connected = false;
    private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        setContentView(R.layout.activity_main);
        EventBus.getDefault().register(this);
        mTextViewConnectState = findViewById(R.id.connectState);
        final EditText serverEditText = findViewById(R.id.ServerEditText);
        final EditText roomEditText = findViewById(R.id.RoomEditText);
        findViewById(R.id.JoinRoomBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (connected) {
                    String addr = serverEditText.getText().toString();
                    String roomName = roomEditText.getText().toString();
                    if (!"".equals(roomName)) {
                        Intent intent = new Intent(MainActivity.this, CallActivity.class);
                        intent.putExtra("ServerAddr", addr);
                        intent.putExtra("RoomName", roomName);
                        startActivity(intent);
                    }
                } else {
                    SignalManager.getInstance().connectSignal(mContext);
                    SignalManager.getInstance().listenSignalEvents();
                }

            }
        });
        SignalManager.getInstance().connectSignal(mContext);
        SignalManager.getInstance().listenSignalEvents();
        String[] perms = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
        if (!EasyPermissions.hasPermissions(this, perms)) {
            EasyPermissions.requestPermissions(this, "Need permissions for camera & microphone", 0, perms);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        SignalManager.getInstance().releaseSocket();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void handleEvent(SignalEvent event) {
        switch (event.getType()) {
            case SignalEvent.TYPE_JUMP:
                startActivity(new Intent(this, JoinActivity.class));
                break;
            case SignalEvent.TYPE_RECONNECT:
                SignalManager.getInstance().connectSignal(this);
                SignalManager.getInstance().listenSignalEvents();
                break;
            case SignalEvent.TYPE_CONNECT:
                connected = true;
                mTextViewConnectState.setText("已连接");
                break;
            case SignalEvent.TYPE_DISCONNECT:
                connected = false;
                mTextViewConnectState.setText("未连接");
                break;
        }
    }
}