package com.demo.pushtotalk;

import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.AnimationDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.MotionEvent;
import android.widget.Button;
import android.os.Bundle;
import android.view.View;
import android.content.Intent;
import android.util.Log;
import android.content.ServiceConnection;
import android.content.Context;


import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Queue;
import java.io.File;

public class MainActivity extends Activity implements Common {
    static String TAG = "[*** MAIN]";
    private TcpService tcpService = null;
    private MainHandlers mHandlers = new MainHandlers();
    private ProcessThread mProcessThread = new ProcessThread(this);
    private AudioProcess mAudioProcess = new AudioProcess(this);
    private VoiceListAdapter voiceAdapter;
    public List<VoiceBean> voiceList = new ArrayList<VoiceBean>();
    private VoiceListAdapter.ViewHolder lastHolder = null;

    public ConfigModel mConfigModel = null;

    public ClientState mClientState = ClientState.STATE_INIT;
    public Queue<DemoMessage> msgQue = new LinkedList<DemoMessage>();
    public BlockData mReviceData = new BlockData();
    public BlockData mSendData = new BlockData();
    public int mMsgId = 0;
    private boolean isVoiceSending = false;
    private int currentSendingIndx = -1;


    public ConnPopWindow connPopWindow = null;
    public MenuPopWindow menuPopWindow = null;
    public RecordPopupWindow recordPopWindow = null;

    private Button mButtonPush;
    private View mButtonTitleMenu;

    private ImageView connectStatusView;

    private ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        setContentView(R.layout.activity_main);

        this.mConfigModel = new ConfigModel(this);
        // bind service
        Intent intent = new Intent(this, TcpService.class);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);

        mProcessThread.start();
        voiceAdapter = new VoiceListAdapter(this, voiceList);

        this.menuPopWindow = new MenuPopWindow(this);
        this.connPopWindow = new ConnPopWindow(this);
        this.recordPopWindow = new RecordPopupWindow(this);

        mButtonPush = (Button) findViewById(R.id.pushToTalk);
        mButtonTitleMenu = (View) findViewById(R.id.title_menu_button);
        connectStatusView = (ImageView) findViewById(R.id.connect_status);


        listView = findViewById(R.id.voice_list);
        listView.setAdapter(voiceAdapter);
        listView.setHeaderDividersEnabled(true);
        listView.setFooterDividersEnabled(true);


        mButtonPush.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    mButtonPush.setBackgroundResource(R.drawable.push_button_1);
                    mAudioProcess.startRecord();
                    recordPopWindow.showPopupWindow();
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    mButtonPush.setBackgroundResource(R.drawable.push_button_0);
                    if (!recordPopWindow.isRecordStoped) {
                        mAudioProcess.stopRecord();
                    }

                    recordPopWindow.dismiss();
                    if (mAudioProcess.lastRecordPath != null) {
                        VoiceBean vb = new VoiceBean();
                        if (vb.load(mAudioProcess.lastRecordPath)) {
                            voiceList.add(vb);

                            if (mClientState == ClientState.STATE_AUTHED) {
                                // send automatically
                                startSendVoice(voiceList.size() - 1);
                            } else {
                                Message m = new Message();
                                m.what = MSG_H_SERVER_NOT_CONNECT;
                                mHandler.sendMessage(m);
                                vb.status = VoiceStatus.SEND_FAILED;
                            }

                            voiceAdapter.notifyDataSetChanged();
                        }
                    }
                }
                return false;
            }
        });


        mButtonTitleMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                showTitleMenu(v);
                menuPopWindow.showPopupWindow(v);
            }
        });


        verifyPermissions();

        loadHistoryVoice();
        voiceAdapter.notifyDataSetChanged();

        listView.post(new Runnable() {
            @Override
            public void run() {
                if (voiceAdapter.getCount() > 0) {
                    listView.setSelection(voiceAdapter.getCount() - 1);
                }
            }
        });

    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy...");
        disconnectServer();
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

                Message m = new Message();
                m.what = MSG_H_AUTH_SUCCESS;
                mHandler.sendMessage(m);

                tcpService.cmdHandle(CMD_START_HEARTBEAT, null);
                return;
            }

            if (msg.msgType == MSG_HEARTBEAT_ACK) {
                Log.d(TAG, "heartbeat ack.");
                return;
            }

            msgQue.offer(msg);
        }


        @Override
        public void onNotify(int cmd, int value) {
            if (cmd == CMD_NOTIFY_RESULT) {

                switch (value) {

                    case MSG_H_SERVER_DISCONNECT:
                    case MSG_H_CONNECT_FAILED: {
                        Message msg = new Message();
                        msg.what = value;
                        mHandler.sendMessage(msg);
                        mClientState = ClientState.STATE_INIT;
                    }
                    break;


                    case MSG_H_CONNECT_SUCCESS: {
                        DemoMessage msg = new DemoMessage();
                        msg.msgSrc = MOBILE;
                        msg.msgType = MSG_AUTH_REQ;
                        msg.msgId = 0;

                        sendMessage(msg);
                        mClientState = ClientState.STATE_AUTHING;
                    }
                    break;
                    default:
                        break;
                }
            }
        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_H_VOICE_SAVE_SUCCESS:
                    VoiceBean vb = new VoiceBean();
                    if (vb.load(mAudioProcess.lastSavePath)) {
                        vb.status = VoiceStatus.UNREAD;
                        voiceList.add(vb);
                    }
                    voiceAdapter.notifyDataSetChanged();
                    break;
                case MSG_H_VOICE_SAVE_FAILED:
                    break;

                case MSG_H_VOICE_PLAYING:
                    VoiceListAdapter.ViewHolder h = (VoiceListAdapter.ViewHolder) msg.obj;
                    if (h != null) {
                        h.viewSpeaker.setBackgroundResource(R.drawable.voice_playing);
                        AnimationDrawable ani = (AnimationDrawable) h.viewSpeaker.getBackground();
                        ani.start();
                    }

                    break;

                case MSG_H_VOICE_PLAY_END:
                    VoiceListAdapter.ViewHolder vh = (VoiceListAdapter.ViewHolder) msg.obj;
                    if (vh != null) {
                        vh.viewSpeaker.setBackgroundResource(R.drawable.ic_voice_2);
                    }
                    break;

                case MSG_H_VOICE_CLRAED:
                case MSG_H_VOICE_ALL_CLEARED:
                    voiceAdapter.notifyDataSetChanged();
                    break;

                case MSG_H_CONNECT_FAILED:
                    Toast.makeText(MainActivity.this, "Conect to server failed...", Toast.LENGTH_SHORT).show();
                    break;

                case MSG_H_AUTH_SUCCESS:
                    connectStatusView.setBackgroundResource(R.drawable.circle_green);
                    break;
                case MSG_H_SERVER_DISCONNECT:
                    Toast.makeText(MainActivity.this, "Server disconnected...", Toast.LENGTH_SHORT).show();
                    connectStatusView.setBackgroundResource(R.drawable.circle_gray);
                    break;
                case MSG_H_SERVER_NOT_CONNECT:
                    Toast.makeText(MainActivity.this, "Server not connect...", Toast.LENGTH_SHORT).show();
                    break;

                case MSG_H_VOICE_BUSY_SENDING:
                    Toast.makeText(MainActivity.this, "Message sending busy...", Toast.LENGTH_SHORT).show();
                    break;

                case MSG_H_VOICE_SEND_FAILED:
                    Toast.makeText(MainActivity.this, "Message sending failed...", Toast.LENGTH_SHORT).show();
                    int idx = msg.arg1;
                    if (idx >= 0 && idx < voiceList.size()) {
                        vb = voiceList.get(idx);
                        vb.status = VoiceStatus.SEND_FAILED;
                        voiceList.set(idx, vb);
                        voiceAdapter.notifyDataSetChanged();
                    }
                    break;

                case MSG_H_VOICE_SEND_RETRY:
                    idx = msg.arg1;
                    if (idx >= 0 && idx < voiceList.size()){
                        vb = voiceList.get(idx);
                        vb.status = VoiceStatus.NONE;
                        voiceList.set(idx, vb);
                        voiceAdapter.notifyDataSetChanged();
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

    public TcpService getTcpService() {
        return tcpService;
    }

    //------- network about ----------
    public void connectServer() {
        if (tcpService != null) {
            tcpService.setServerAddress(mConfigModel.serverIp, mConfigModel.serverPort);
            tcpService.cmdHandle(CMD_CONNECT_SERVER, null);
        }
    }

    private void disconnectServer() {
        if (this.mClientState == ClientState.STATE_AUTHED) {
            DemoMessage msg = new DemoMessage();
            msg.msgSrc = MOBILE;
            msg.msgType = MSG_OFFLINE;
            msg.msgId = 0;
            sendMessage(msg);
            mClientState = ClientState.STATE_INIT;
        }
    }


    //--------- media about -----------

    public void startRecord() {
        mAudioProcess.startRecord();
    }

    public void stopRecord() {
        mAudioProcess.stopRecord();
    }

    public void loadHistoryVoice() {
        File[] files = mAudioProcess.getAllFiles();

        if (files == null) {
            return;
        }

        for (int i = 0; i < files.length; i++) {
            VoiceBean vb = new VoiceBean();
            if (vb.load(files[i])) {
                voiceList.add(vb);
            }
        }
    }

    public void clearAllVoice() {
        mAudioProcess.clearAllFiles();
        voiceList.clear();

        Message msg = new Message();
        msg.what = MSG_H_VOICE_ALL_CLEARED;
        mHandler.sendMessage(msg);
    }

    public void deleteVoice(int index){
        VoiceBean vb = voiceList.get(index);
        mAudioProcess.clearFile(vb.getFilePath());

        voiceList.remove(index);
        Message msg = new Message();
        msg.what = MSG_H_VOICE_CLRAED;
        mHandler.sendMessage(msg);
    }

    public void voicePlay(String path, VoiceListAdapter.ViewHolder holder) {
        Message msg = new Message();
        msg.what = MSG_H_VOICE_PLAYING;
        msg.obj = holder;
        mHandler.sendMessage(msg);

        mAudioProcess.playAudio(path);
        this.lastHolder = holder;
    }

    public void voicePlayStop() {
        if (this.lastHolder != null) {
            Message msg = new Message();
            msg.what = MSG_H_VOICE_PLAY_END;
            msg.obj = this.lastHolder;
            mHandler.sendMessage(msg);

        }

        mAudioProcess.stopPlay();
    }

    public void onVoicePlayStop() {
        // todo update ui
        Message msg = new Message();
        msg.what = MSG_H_VOICE_PLAY_END;
        msg.obj = this.lastHolder;
        mHandler.sendMessage(msg);

        Log.d(TAG, "play end");
    }
    public void retrySendVoice(int idx){
        Message msg = new Message();
        msg.what = MSG_H_VOICE_SEND_RETRY;
        msg.arg1 = idx;
        mHandler.sendMessage(msg);

        startSendVoice(idx);
    }

    synchronized public void startSendVoice(int idx) {
        if (isVoiceSending) {
            Message msg = new Message();
            msg.what = MSG_H_VOICE_BUSY_SENDING;
            mHandler.sendMessage(msg);
            return;
        }
        currentSendingIndx = idx;
        isVoiceSending = true;
        String path = voiceList.get(idx).getFilePath();
        mAudioProcess.startSend(path);
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

    public void onSendVoiceResult(boolean success) {
        isVoiceSending = false;
        Message msg = new Message();
        msg.what = success ? MSG_H_VOICE_SEND_SUCCESS : MSG_H_VOICE_SEND_FAILED;
        msg.arg1 = currentSendingIndx;
        mHandler.sendMessage(msg);
        currentSendingIndx = -1;
    }

    public void saveVoice(byte[] data) {
        if (data.length > 0) {
            mAudioProcess.saveAudio(data);
        }
    }

    // the callback that save result, run in child thread.
    public void onSaveVoiceResult(boolean success) {
        Message msg = new Message();
        msg.what = success ? MSG_H_VOICE_SAVE_SUCCESS : MSG_H_VOICE_SAVE_FAILED;
        mHandler.sendMessage(msg);
    }

    public void sendTestData() {
        if (BlockState.DATA_STATE_SENDING == mSendData.State) {
            Log.e(TAG, "busy on sending...");
            return;
        }

        mSendData.randomInit();
        byte[] data = mSendData.GetSendData();
        mProcessThread.dataSend(data);
        mSendData.State = BlockState.DATA_STATE_SENDING;
    }


    private void showTitleMenu(View view) {
        PopupMenu popupMenu = new PopupMenu(MainActivity.this, view);
        popupMenu.getMenuInflater().inflate(R.menu.menu_items, popupMenu.getMenu());
        popupMenu.show();
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
