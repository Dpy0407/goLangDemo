package com.demo.pushtotalk;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.util.Log;


import java.nio.channels.SocketChannel;
import java.net.InetSocketAddress;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class TcpService extends Service implements Common {
    //    private SocketChannel client = null;
    final String TAG = "TCPService";
    private Socket clientSocket = null;
    private InputStream inputStream = null;
    private OutputStream outputStream = null;

    CommandReceiver cmdReceiver;

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        cmdReceiver = new CommandReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(TCP_INTENT_ACTION_CMD);
        registerReceiver(cmdReceiver, filter);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
//        connectThread conn = new connectThread();
//        conn.start();
        Log.d(TAG, "onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }


    public class LocalBinder extends Binder {
        public TcpService getService() {
            return TcpService.this;
        }
    }

    public void StartServerListener() {
        ServerListener a = new ServerListener();
        a.start();
    }

    public void ConnectToServer() {
        try {
            if (clientSocket != null && clientSocket.isConnected()) {
                inputStream = clientSocket.getInputStream();
                outputStream = clientSocket.getOutputStream();
                return;
            }

            clientSocket = new Socket("192.168.3.13", 9090);
//            clientSocket.setSoTimeout(5*1000);
            if (clientSocket != null) {
                inputStream = clientSocket.getInputStream();
                outputStream = clientSocket.getOutputStream();
            }

            StartServerListener();

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();

        }
    }

    public void DisconnectToServer() {
        try {
            if (clientSocket != null) {
                clientSocket.close();
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


    public void SendDataToServer(byte[] data) {
        try {
            Log.d(TAG, "data len:" + data.length);
            outputStream.write(DemoMessage.int2arr(data.length));
            outputStream.write(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class connectThread extends Thread {
        public void run() {
            ConnectToServer();

            byte[] arr = new byte[10];
            arr[0] = MAGIC_VALUE & 0xFF;
            arr[1] = (MAGIC_VALUE >> 8) & 0xFF;
            arr[2] = (MAGIC_VALUE >> 16) & 0xFF;
            arr[3] = (MAGIC_VALUE >> 24) & 0xFF;

            arr[4] = MOBILE;
            arr[5] = MSG_AUTH_REQ;
            SendDataToServer(arr);
        }
    }


    private class ServerListener extends Thread {
        public void run() {
            try {
                while (true) {
                    byte[] buffer = new byte[10 * 1024];
                    int len = inputStream.read(buffer);

                    if (buffer.length > 0) {
                        sendMsg(Arrays.copyOf(buffer, len));
                    }
                    Thread.sleep(100);
                }
            } catch (CharacterCodingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    private void sendMsg(byte[] data) {
//        Intent intent = new Intent(Common.TCP_INTENT_ACTION_CMD);
//        intent.putExtra("data", data);
//        this.sendBroadcast(intent);

        Log.d(TAG, DemoMessage.arr2HexString(data));
    }


    private class CommandReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(TCP_INTENT_ACTION_CMD)) {
                int cmd = intent.getIntExtra("cmd", -1);//获取Extra信息
                if (cmd == CMD_CONNECT_SERVER) {
                    Log.d(TAG, "connect to server...");
                    connectThread c = new connectThread();
                    c.start();
                }
            }
        }
    }


}
