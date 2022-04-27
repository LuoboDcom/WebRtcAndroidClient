package com.example.webrtcandroid;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;

import com.example.webrtcandroid.observer.MySdpObserver;
import com.example.webrtcandroid.observer.PeerConnectionObserver;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.RtpReceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class CallActivity extends AppCompatActivity {

    private static final int VIDEO_RESOLUTION_WIDTH = 1280;
    private static final int VIDEO_RESOLUTION_HEIGHT = 720;
    private static final int VIDEO_FPS = 30;

    private String mState = "init";

    private static final String TAG = "CallActivity";
    private Context mContext;
    public static final String VIDEO_TRACK_ID = "1";//"ARDAMSv0";
    public static final String AUDIO_TRACK_ID = "2";//"ARDAMSa0";

    //用于数据传输
    private PeerConnection mPeerConnection;
    private PeerConnectionFactory mPeerConnectionFactory;

    //OpenGL ES
    private EglBase mRootEglBase;

    //继承自 surface view
    private SurfaceViewRenderer mLocalSurfaceView;
    private SurfaceViewRenderer mRemoteSurfaceView;

    private VideoTrack mVideoTrack;
    private AudioTrack mAudioTrack;

    private MySdpObserver mSdpObserver;
    private ExecutorService mExecutorService;
    private static final int CREATE_PEER = 0x01;
    private static final int JOIN_HOME = 0x02;
    private static final int INVITE = 0x03;
    private static final int CREATE_OFFER = 0x04;
    private static final int SET_REMOTE_OFFER = 0x05;
    private static final int RELEASE = 0x06;

    private Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case CREATE_PEER:
                    Log.e(TAG, "1、CREATE_PEER");
                    createPeerConnection();
                    break;
                case JOIN_HOME:
                    Log.e(TAG, "2、JOIN_HOME");
                    SignalManager.getInstance().joinRoom(getString(R.string.default_room_number));
                    SignalManager.getInstance().setSignalEventListener(mOnSignalEventListener);
                    break;
                case INVITE:
                    Log.e(TAG, "3、INVITE");
                    SignalManager.getInstance().invite();
                    break;
                case CREATE_OFFER:
                    Log.e(TAG, "4、CREATE_OFFER-----" + mPeerConnection);
                    mState = "joined_conn";
                    MediaConstraints mediaConstraints = new MediaConstraints();
                    mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
                    mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
                    mediaConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
                    mPeerConnection.createOffer(mSdpObserver, mediaConstraints);
                    break;
                case SET_REMOTE_OFFER:
                    Log.e(TAG, "5、SET_REMOTE_OFFER");
                    try {
                        String description = ((JSONObject) msg.obj).getString("sdp");
                        mPeerConnection.setRemoteDescription(
                                mSdpObserver,
                                new SessionDescription(
                                        SessionDescription.Type.ANSWER,
                                        description));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    break;
                case RELEASE:
                    Log.e(TAG, "6、RELEASE");
                    release();
                    break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);
        mContext = this;
        mLocalSurfaceView = findViewById(R.id.LocalSurfaceView);
        mRemoteSurfaceView = findViewById(R.id.RemoteSurfaceView);
        handler.sendEmptyMessage(CREATE_PEER);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        handler.sendEmptyMessage(RELEASE);
    }

    private void initView() {
        // NOTE: this _must_ happen while PeerConnectionFactory is alive!
        Logging.enableLogToDebugOutput(Logging.Severity.LS_VERBOSE);
        mLocalSurfaceView.init(mRootEglBase.getEglBaseContext(), null);
        mLocalSurfaceView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        mLocalSurfaceView.setMirror(true);
        mLocalSurfaceView.setEnableHardwareScaler(false /* enabled */);

        mRemoteSurfaceView.init(mRootEglBase.getEglBaseContext(), null);
        mRemoteSurfaceView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        mRemoteSurfaceView.setMirror(true);
        mRemoteSurfaceView.setEnableHardwareScaler(true /* enabled */);
        mRemoteSurfaceView.setZOrderMediaOverlay(true);


        VideoCapturer mVideoCapturer = createVideoCapturer();
        SurfaceTextureHelper mSurfaceTextureHelper = SurfaceTextureHelper.create(Thread.currentThread().getName(), mRootEglBase.getEglBaseContext());
        VideoSource videoSource = mPeerConnectionFactory.createVideoSource(false);
        mVideoCapturer.initialize(mSurfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());
        mVideoCapturer.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, VIDEO_FPS);

        mVideoTrack = mPeerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        mVideoTrack.setEnabled(true);
        mVideoTrack.addSink(mLocalSurfaceView);

        AudioSource audioSource = mPeerConnectionFactory.createAudioSource(new MediaConstraints());
        mAudioTrack = mPeerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
        mAudioTrack.setEnabled(true);


        List<String> mediaStreamLabels = Collections.singletonList("ARDAMS");
        mPeerConnection.addTrack(mVideoTrack, mediaStreamLabels);
        mPeerConnection.addTrack(mAudioTrack, mediaStreamLabels);
    }

    private void initObserver() {
        mSdpObserver = new MySdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                //将会话描述设置在本地
                mPeerConnection.setLocalDescription(this, sessionDescription);
                SessionDescription localDescription = mPeerConnection.getLocalDescription();
                SessionDescription.Type type = localDescription.type;
                Log.e(TAG, "onCreateSuccess == " + " type == " + type + "\nSDP:" + sessionDescription.description);
                //接下来使用之前的WebSocket实例将offer发送给服务器
                if (type == SessionDescription.Type.OFFER) {
                    //应答
                    doStartCall(sessionDescription);
                }
            }
        };
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.e(TAG, "onDestroy mPeerConnectionFactory2:" + mPeerConnectionFactory + "---" + Thread.currentThread());

    }

    private void release() {
        if (mPeerConnection != null) {
            mPeerConnection.dispose();
            mPeerConnection = null;
        }
        if (mLocalSurfaceView != null) {
            mLocalSurfaceView.release();
        }
        if (mRemoteSurfaceView != null) {
            mRemoteSurfaceView.release();
        }
        if (mPeerConnectionFactory != null) {
            mPeerConnectionFactory.dispose();
            mPeerConnectionFactory = null;
        }
        if (mRootEglBase != null) {
            mRootEglBase.release();
            mRootEglBase = null;
        }
        SignalManager.getInstance().releaseSocket();
        SignalManager.getInstance().leaveRoom();
        updateCallState(false);
        finish();
    }

    private void updateCallState(boolean idle) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (idle) {
                    mRemoteSurfaceView.setVisibility(View.GONE);
                } else {
                    mRemoteSurfaceView.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    public void doStartCall(SessionDescription sessionDescription) {
        JSONObject message = new JSONObject();
        try {
            message.put("type", "offer");
            message.put("sdp", sessionDescription.description);
            SignalManager.getInstance().sendMessage(message);
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    public void createPeerConnection() {
        // 1. 初始化的方法，必须在开始之前调用
        PeerConnectionFactory.InitializationOptions initializationOptions = PeerConnectionFactory.InitializationOptions.builder(this)
                .setEnableInternalTracer(true)
                .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
                .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);
        mRootEglBase = EglBase.create();
        // 2. 设置编解码方式：默认方法
        final VideoEncoderFactory encoderFactory;
        final VideoDecoderFactory decoderFactory;

        encoderFactory = new DefaultVideoEncoderFactory(
                mRootEglBase.getEglBaseContext(),
                true,
                true);
        decoderFactory = new DefaultVideoDecoderFactory(mRootEglBase.getEglBaseContext());

        // 构造Factory
        AudioDeviceModule audioDeviceModule = JavaAudioDeviceModule.builder(mContext).createAudioDeviceModule();
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        mPeerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(audioDeviceModule)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();
        Log.i(TAG, "Create PeerConnection ..." + Thread.currentThread());
        LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<PeerConnection.IceServer>();

        PeerConnection.IceServer ice_server =
                PeerConnection.IceServer.builder("turn:49.232.155.214:3478")
                        .setPassword("mypasswd")
                        .setUsername("terry")
                        .createIceServer();

        iceServers.add(ice_server);

        PeerConnection.IceServer iceServerGoogle = PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer();
        iceServers.add(iceServerGoogle);

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        // Enable DTLS for normal calls and disable for loopback calls.
        rtcConfig.enableDtlsSrtp = true;
        //rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        Log.e(TAG, "createPeerConnection mPeerConnectionFactory:" + mPeerConnectionFactory + Thread.currentThread());
        mPeerConnection = mPeerConnectionFactory.createPeerConnection(rtcConfig,
                new PeerConnectionObserver() {
                    @Override
                    public void onIceCandidate(IceCandidate iceCandidate) {
                        Log.i(TAG, "onIceCandidate: " + iceCandidate);

                        try {
                            JSONObject message = new JSONObject();
                            //message.put("userId", RTCSignalClient.getInstance().getUserId());
                            message.put("type", "candidate");
                            message.put("label", iceCandidate.sdpMLineIndex);
                            message.put("id", iceCandidate.sdpMid);
                            message.put("candidate", iceCandidate.sdp);
                            SignalManager.getInstance().sendMessage(message);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
                        for (int i = 0; i < iceCandidates.length; i++) {
                            Log.i(TAG, "onIceCandidatesRemoved: " + iceCandidates[i]);
                        }
                        mPeerConnection.removeIceCandidates(iceCandidates);
                    }

                    @Override
                    public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                        MediaStreamTrack track = rtpReceiver.track();
                        if (track instanceof VideoTrack) {
                            Log.i(TAG, "onAddVideoTrack");
                            VideoTrack remoteVideoTrack = (VideoTrack) track;
                            remoteVideoTrack.setEnabled(true);
                            remoteVideoTrack.addSink(mRemoteSurfaceView);
                        }
                    }
                });
        if (mPeerConnection == null) {
            Log.e(TAG, "Failed to createPeerConnection !");
            return;
        }
        Log.e(TAG, "PeerConnection:" + mPeerConnection);
        initView();
        initObserver();
        handler.sendEmptyMessage(JOIN_HOME);
    }

    private VideoCapturer createVideoCapturer() {
        if (Camera2Enumerator.isSupported(this)) {
            return createCameraCapturer(new Camera2Enumerator(this));
        } else {
            return createCameraCapturer(new Camera1Enumerator(true));
        }
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Log.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Log.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        return null;
    }

    private SignalManager.OnSignalEventListener
            mOnSignalEventListener = new SignalManager.OnSignalEventListener() {

        @Override
        public void onConnected() {

        }

        @Override
        public void onConnecting() {
        }

        @Override
        public void onDisconnected() {
        }

        @Override
        public void onUserJoined(String roomName, String userID) {
            Log.i(TAG, "onUserJoined");
            handler.sendEmptyMessage(INVITE);
        }

        @Override
        public void onUserLeaved(String roomName, String userID) {
            mState = "leaved";
        }

        @Override
        public void onRemoteUserJoined(String roomName) {
            //调用call， 进行媒体协商
            Log.i(TAG, "onRemoteUserJoined");
            handler.sendEmptyMessage(CREATE_OFFER);
        }

        @Override
        public void onRemoteUserLeaved(String roomName, String userID) {
            handler.post(() -> {
                Log.i(TAG, "onRemoteUserLeaved");
                mState = "joined_unbind";
                handler.sendEmptyMessage(RELEASE);
            });
        }

        @Override
        public void onRoomFull(String roomName, String userID) {
            handler.sendEmptyMessage(RELEASE);
        }

        @Override
        public void onMessage(JSONObject message) {

            Log.i(TAG, "onMessage: " + message);
            handler.post(() -> {
                try {
                    String type = message.getString("type");
                    if (type.equals("answer")) {
                        onRemoteAnswerReceived(message);
                    } else if (type.equals("candidate")) {
                        onRemoteCandidateReceived(message);
                    } else {
                        Log.w(TAG, "the type is invalid: " + type);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });
        }

        private void onRemoteAnswerReceived(JSONObject message) {
            Message handleMsg = Message.obtain();
            handleMsg.what = SET_REMOTE_OFFER;
            handleMsg.obj = message;
            handler.sendMessage(handleMsg);
        }

        private void onRemoteCandidateReceived(JSONObject message) {
            try {
                IceCandidate remoteIceCandidate =
                        new IceCandidate(message.getString("id"),
                                message.getInt("label"),
                                message.getString("candidate"));

                mPeerConnection.addIceCandidate(remoteIceCandidate);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

}
