package com.demo.pushtotalk;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.os.IBinder;
import android.widget.Button;
import android.os.Bundle;
import android.view.View;
import android.content.Intent;
import android.util.Log;
import android.content.ServiceConnection;
import android.content.Context;
import android.widget.Toast;

import java.util.LinkedList;
import java.util.Queue;

public class MainActivity extends AppCompatActivity implements Common{
    static String TAG = "MAIN";
    private TcpService tcpService = null;
    private Button mButtonConnect;

    private ClientState mClientState = ClientState.STATE_INIT;
    private Queue<DemoMessage> msgQue = new LinkedList<DemoMessage>();
    private MainHandlers mHandlers = new MainHandlers();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG,"start!!!");
        Log.d(TAG,"start...");
        // bind service
        Intent intent = new Intent(this, TcpService.class);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);

        mButtonConnect = (Button) findViewById(R.id.buttonConnect);

        mButtonConnect.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                Log.d(TAG,"button clicked!!!");
                sendCmdToService(CMD_CONNECT_SERVER,null);
                DemoMessage msg = new DemoMessage();
                msg.msgSrc = MOBILE;
                msg.msgType = MSG_AUTH_REQ;
                msg.msgId =0;

                sendMessage(msg);
                mClientState = ClientState.STATE_AUTHING;
            }
        });


    }

    @Override
    protected void onDestroy() {
        unbindService(conn);
        super.onDestroy();
    }


    class MainHandlers implements DataHandlers{
        @Override
        public void onReciveHandle(byte[] data){
            DemoMessage msg = new DemoMessage();
            if (!msg.parse(data)){
                Log.e(TAG,"parse message failed.");
                return;
            }

            if(mClientState == ClientState.STATE_AUTHING  && msg.msgType == MSG_ACK){
                mClientState = ClientState.STATE_AUTHED;
//                Toast.makeText(MainActivity.this, "Connect to server success!", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Connect to server success!");
                sendCmdToService(CMD_START_HEARTBEAT,null);
                return;
            }

            if(msg.msgType == MSG_HEARTBEAT_ACK){
                Log.d(TAG, "heartbeat ack.");
                return;
            }

            msgQue.offer(msg);
        }
    }

    private void sendMessage(DemoMessage msg){
        byte[] data = msg.serialize();
        sendCmdToService(CMD_SEND_DATA, data);
    }

    private void sendCmdToService(int cmd, byte[] data){
        Intent intent = new Intent();
        intent.setAction(TCP_INTENT_ACTION_CMD);
        intent.putExtra("cmd", cmd);
        if(data!= null){
            intent.putExtra("data",data);
        }

        sendBroadcast(intent);
    }

    public ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            tcpService = ((TcpService.LocalBinder)iBinder).getService();
            tcpService.setHandlers(mHandlers);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            tcpService = null;
        }
    };
}
