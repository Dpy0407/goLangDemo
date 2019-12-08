package com.demo.pushtotalk;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.AnimationDrawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.speech.tts.Voice;
import android.view.MotionEvent;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.os.Bundle;
import android.view.View;
import android.content.Intent;
import android.util.Log;
import android.content.ServiceConnection;
import android.content.Context;


import android.widget.ListView;
import android.widget.Toast;
import android.media.MediaRecorder;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;

public class MainActivity extends AppCompatActivity implements Common {
    static String TAG = "[*** MAIN]";
    private TcpService tcpService = null;
    private MainHandlers mHandlers = new MainHandlers();
    private ProcessThread mProcessThread = new ProcessThread(this);
    private AudioProcess mAudioProcess = new AudioProcess(this);
    private VoiceListAdapter voiceAdapter;
    public List<VoiceBean> voiceList = new ArrayList<VoiceBean>();
    private VoiceListAdapter.ViewHolder lastHolder = null;


    public ClientState mClientState = ClientState.STATE_INIT;
    public Queue<DemoMessage> msgQue = new LinkedList<DemoMessage>();
    public BlockData mReviceData = new BlockData();
    public BlockData mSendData = new BlockData();
    public int mMsgId = 0;


    private Button mButtonConnect;
    private Button mButtonSend;
    private Button mButtonPush;
    private Button mButtonPlay;

    private ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        setContentView(R.layout.activity_main);

        mAudioProcess.PrintPaths();

        // bind service
        Intent intent = new Intent(this, TcpService.class);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);

        mProcessThread.start();
        voiceAdapter = new VoiceListAdapter(this, voiceList);

        mButtonConnect = (Button) findViewById(R.id.buttonConnect);
        mButtonSend = (Button) findViewById(R.id.buttenSend);
        mButtonPush = (Button) findViewById(R.id.pushToTalk);
        mButtonPlay = (Button) findViewById(R.id.buttonPlay);

        listView = findViewById(R.id.voice_list);
        listView.setAdapter(voiceAdapter);
        listView.setHeaderDividersEnabled(true);
        listView.setFooterDividersEnabled(true);


        mButtonConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "button clicked!!!");
                sendCmdToService(CMD_CONNECT_SERVER, null);
                DemoMessage msg = new DemoMessage();
                msg.msgSrc = MOBILE;
                msg.msgType = MSG_AUTH_REQ;
                msg.msgId = 0;

                sendMessage(msg);
                mClientState = ClientState.STATE_AUTHING;
            }
        });

        mButtonSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (BlockState.DATA_STATE_SENDING == mSendData.State) {
                    Log.e(TAG, "busy on sending...");
                    return;
                }

                mSendData.randomInit();
                byte[] data = mSendData.GetSendData();
                mProcessThread.dataSend(data);
                mSendData.State = BlockState.DATA_STATE_SENDING;
            }
        });

        mButtonPush.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    mAudioProcess.startRecord();
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    mAudioProcess.stopRecord();
                    if (mAudioProcess.lastRecordPath != null) {
                        VoiceBean vb = new VoiceBean();
                        if (vb.load(mAudioProcess.lastRecordPath)) {
                            voiceList.add(vb);
                        }
                        voiceAdapter.notifyDataSetChanged();

                        if (mClientState == ClientState.STATE_AUTHED) {
                            // send automatically
                            mAudioProcess.startSend(mAudioProcess.lastRecordPath);
                        } else {
                            //todo mark ui as not send
                        }

                    }
                }
                return false;
            }
        });

        mButtonPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "play: " + mAudioProcess.lastRecordPath);
                mAudioProcess.playAudio(mAudioProcess.lastRecordPath);
            }
        });

        verifyPermissions();

        loadHistoryVoice();
        voiceAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        unbindService(conn);
        super.onDestroy();
    }


    class MainHandlers implements DataHandlers {
        @Override
        public void onReciveHandle(byte[] data) {
            DemoMessage msg = new DemoMessage();
            if (!msg.parse(data)) {
                Log.e(TAG, "parse message failed.");
                return;
            }

            if (mClientState == ClientState.STATE_AUTHING && msg.msgType == MSG_ACK) {
                mClientState = ClientState.STATE_AUTHED;
//                Toast.makeText(MainActivity.this, "Connect to server success!", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Connect to server success!");
//                sendCmdToService(CMD_START_HEARTBEAT, null);

                tcpService.cmdHandle(CMD_START_HEARTBEAT, null);
                return;
            }

            if (msg.msgType == MSG_HEARTBEAT_ACK) {
                Log.d(TAG, "heartbeat ack.");
                return;
            }

            msgQue.offer(msg);
        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_H_VOICE_SAVE_SUCCESS:
                    VoiceBean vb = new VoiceBean();
                    if (vb.load(mAudioProcess.lastSavePath)) {
                        voiceList.add(vb);
                    }
                    voiceAdapter.notifyDataSetChanged();
                    break;
                case MSG_H_VOICE_SAVE_FAILED:
                    break;

                case MSG_H_VOICE_PLAYING:
                    VoiceListAdapter.ViewHolder h = (VoiceListAdapter.ViewHolder)msg.obj;
                    if(h!=null){
                        h.viewSpeaker.setBackgroundResource(R.drawable.voice_playing);
                        AnimationDrawable ani = (AnimationDrawable)h.viewSpeaker.getBackground();
                        ani.start();
                    }

                    break;

                case MSG_H_VOICE_PLAY_END:
                    VoiceListAdapter.ViewHolder vh = (VoiceListAdapter.ViewHolder)msg.obj;
                    if(vh!=null){
                        vh.viewSpeaker.setBackgroundResource(R.drawable.ic_voice_2);
                    }
                    break;

                default:
                    break;
            }


        }
    };


    private void sendMessage(DemoMessage msg) {
        if (tcpService == null) {
            Log.e(TAG, "service not ready");
            return;
        }
        byte[] data = msg.serialize();
//        sendCmdToService(CMD_SEND_DATA, data);

        tcpService.cmdHandle(CMD_SEND_DATA, data);
    }

    private void sendCmdToService(int cmd, byte[] data) {
        Intent intent = new Intent();
        intent.setAction(TCP_INTENT_ACTION_CMD);
        intent.putExtra("cmd", cmd);
        if (data != null) {
            intent.putExtra("data", data);
        }

        sendBroadcast(intent);
    }

    public ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            tcpService = ((TcpService.LocalBinder) iBinder).getService();
            tcpService.setHandlers(mHandlers);
            mProcessThread.SetTcpService(tcpService);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            tcpService = null;
        }
    };

    public void loadHistoryVoice() {
        File[] files = mAudioProcess.getAllFiles();

        for (int i = 0; i < files.length; i++) {
            VoiceBean vb = new VoiceBean();
            if (vb.load(files[i])) {
                voiceList.add(vb);
                Log.d(TAG, vb.getFilePath());
            }
        }
    }

    public void voicePlay(String path, VoiceListAdapter.ViewHolder holder) {
        Message msg= new Message();
        msg.what = MSG_H_VOICE_PLAYING;
        msg.obj = holder;
        mHandler.sendMessage(msg);

        mAudioProcess.playAudio(path);
        this.lastHolder = holder;
    }

    public void voicePlayStop() {
        if(this.lastHolder != null){
            Message msg= new Message();
            msg.what = MSG_H_VOICE_PLAY_END;
            msg.obj = this.lastHolder;
            mHandler.sendMessage(msg);

        }

        mAudioProcess.stopPlay();
    }

    public void onVoicePlayStop() {
        // todo update ui
        Message msg= new Message();
        msg.what = MSG_H_VOICE_PLAY_END;
        msg.obj = this.lastHolder;
        mHandler.sendMessage(msg);

        Log.d(TAG, "play end");
    }


    public void onSendVoice(byte[] vData) {
        if (BlockState.DATA_STATE_SENDING == mSendData.State) {
            Log.e(TAG, "busy on sending...");
            return;
        }

        mSendData.loadData(vData);

        byte[] data = mSendData.GetSendData();
        mProcessThread.dataSend(data);
        mSendData.State = BlockState.DATA_STATE_SENDING;
    }

    public void saveVoice(byte[] data){
        if(data.length >0){
            mAudioProcess.saveAudio(data);
        }
    }

    // the callback that save result, run in child thread.
    public void onSaveVoiceResult(boolean success) {
        Message msg = new Message();
        msg.what = success? MSG_H_VOICE_SAVE_SUCCESS: MSG_H_VOICE_SAVE_FAILED;
        mHandler.sendMessage(msg);
    }


    private static final int REQUEST_PERMISSIONS_CODE = 1;

    private void verifyPermissions() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_PERMISSIONS_CODE);
        }
    }

}
