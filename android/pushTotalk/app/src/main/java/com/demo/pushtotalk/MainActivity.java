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


public class MainActivity extends AppCompatActivity implements Common{
    static String TAG = "MAIN";
    private TcpService tcpService = null;
    private Button mButtonConnect;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG,"start!!!");

// bind service
        Intent intent = new Intent(this, TcpService.class);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);

        mButtonConnect = (Button) findViewById(R.id.buttonConnect);

        mButtonConnect.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
              sendCmdToService(CMD_CONNECT_SERVER,null);
            }
        });


    }

    @Override
    protected void onDestroy() {
        unbindService(conn);
        super.onDestroy();
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
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            tcpService = null;
        }
    };
}
