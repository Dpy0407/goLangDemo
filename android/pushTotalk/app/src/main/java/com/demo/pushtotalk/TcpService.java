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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.locks.ReadWriteLock;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.LinkedList;
import java.util.Queue;

public class TcpService extends Service implements Common {
    //    private SocketChannel client = null;
    final String TAG = "[*** TCPS]";
    CommandReceiver cmdReceiver;
    private Socket clientSocket = null;
    private InputStream inputStream = null;
    private OutputStream outputStream = null;
    private boolean isReadThreadStart = false;
    private boolean isSendThreadStart = false;
    private Lock sockeIStreamLock = new ReentrantLock();
    private Lock sockeOStreamLock = new ReentrantLock();
    private Queue<byte[]> sendQue = new LinkedList<byte[]>();
    private DataHandlers mHandlers = null;

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
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
    public void onDestroy() {
        unregisterReceiver(cmdReceiver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
//        connectThread conn = new connectThread();
//        conn.start();
        Log.d(TAG, "onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    public void setHandlers(DataHandlers handlers) {
        this.mHandlers = handlers;
    }

    public void DisconnectToServer() {
        try {
            if (clientSocket != null) {
                clientSocket.close();
            }

            sockeIStreamLock.lock();
            inputStream = null;
            sockeIStreamLock.unlock();

            sockeOStreamLock.lock();
            outputStream = null;
            sockeOStreamLock.unlock();

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void SendDataToServer(byte[] data) {
        sockeOStreamLock.lock();
        if (outputStream == null) {
            Log.e(TAG, "outputStream is null.");
            return;
        }

        if (data == null || data.length == 0) {
            Log.e(TAG, "empty data.");
            return;
        }

        try {
            Log.d(TAG, "data len:" + data.length);
            outputStream.write(DemoMessage.int2arr(data.length));
            outputStream.write(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
        sockeOStreamLock.unlock();
    }

    public void StartSendThread() {
        if (isSendThreadStart) {
            return;
        }

        SendThread s = new SendThread();
        s.start();
        isSendThreadStart = true;
    }

    public void StartReadThread() {
        if (isReadThreadStart) {
            return;
        }

        ReadThread r = new ReadThread();
        r.start();

        isReadThreadStart = true;
    }

    private void dumpData(byte[] data) {
        Log.d(TAG, DemoMessage.arr2HexString(data));
    }

    public void cmdHandle(int cmd, byte[] data) {
        switch (cmd) {
            case CMD_CONNECT_SERVER: {
                Log.d(TAG, "connect to server...");
                connectThread c = new connectThread();
                c.start();
            }
            break;

            case CMD_SEND_DATA: {
                if (data != null) {
                    sendQue.offer(data);
                }
            }
            break;
            case CMD_START_HEARTBEAT: {
                heartBeatThread h = new heartBeatThread();
                h.start();
            }
            break;
            default:
                Log.e(TAG, "invalide cmd.");
                break;


        }
    }

    public class LocalBinder extends Binder {
        public TcpService getService() {
            return TcpService.this;
        }
    }

    private class connectThread extends Thread {
        public void run() {
            try {
                if (clientSocket != null && clientSocket.isConnected()) {
                    return;
                }

                clientSocket = new Socket(CONFIG_SERVER_IP, CONFIG_SERVER_PORT);
//            clientSocket.setSoTimeout(5*1000);
                if (clientSocket != null && clientSocket.isConnected()) {
                    sockeIStreamLock.lock();
                    inputStream = clientSocket.getInputStream();
                    sockeIStreamLock.unlock();

                    sockeOStreamLock.lock();
                    outputStream = clientSocket.getOutputStream();
                    sockeOStreamLock.unlock();
                }


                StartReadThread();
                StartSendThread();

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();

            }
        }
    }

    private class heartBeatThread extends Thread {
        public void run() {
            DemoMessage msg = new DemoMessage();
            msg.msgSrc = MOBILE;
            msg.msgType = MSG_HEARTBEAT;
            msg.msgId = 0;
            byte[] data = msg.serialize();
            Log.d(TAG, "start heartbeat thread.");
            while (true) {
                if ((clientSocket != null) && clientSocket.isConnected()) {
                    SendDataToServer(data);
                } else {
                    return;
                }

                try {
                    Thread.sleep(CONFIG_HEARTBEAT_PERIOD * 1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();

                    return;
                }
            }
        }
    }

    private class SendThread extends Thread {
        public void run() {
            try {
                while (true) {
                    byte[] data = sendQue.poll();
                    if (data != null) {
                        SendDataToServer(data);
                    }

                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            isReadThreadStart = false;
        }
    }

    private class ReadThread extends Thread {
        private byte[] lastData;

        private int readData(byte[] out) {
            byte[] tmp = new byte[10 * 1024];
            int lenData = -1;
            int t = 0;
            int idx = 0;
            int i = 0;
            try {
                // if lastData include atlest one message, return imediatly.
                if (lastData != null && lastData.length > 4) {
                    int tmpLen = DemoMessage.arr2int(lastData);

                    if (lastData.length >= tmpLen + 4) {
                        for (i = 0; i < tmpLen; i++) {
                            out[i] = lastData[4 + i];
                        }

                        if (lastData.length == tmpLen + 4) {
                            lastData = null;
                        } else {
                            lastData = Arrays.copyOfRange(lastData, tmpLen + 4, lastData.length);
                        }

                        return tmpLen;
                    }
                }


                while (true) {
                    int n = inputStream.read(tmp);
                    if (n <= 0) {
                        t++;
                        if (t > 2) {
                            break;
                        }
                        continue;
                    }

                    if (-1 == lenData) {

                        int lastLen = 0;
                        if (lastData != null) {
                            lastLen = lastData.length;
                            lastData = Arrays.copyOf(lastData, lastLen + n);
                        } else {
                            lastData = new byte[n];
                        }

                        for (i = 0; i < n; i++) {
                            lastData[lastLen + i] = tmp[i];
                        }


                        if (lastData.length >= 4) {
                            lenData = DemoMessage.arr2int(lastData);
                            lastData = Arrays.copyOfRange(lastData, 4, lastData.length);

                            if (lastData.length <= lenData) {
                                for (i = 0; i < lastData.length; i++) {
                                    out[idx++] = lastData[i];
                                }

                                lastData = null;
                            } else {
                                for (i = 0; i < lenData; i++) {
                                    out[idx++] = lastData[i];
                                }

                                lastData = Arrays.copyOfRange(lastData, lenData, lastData.length);
                            }
                        } else {
                            continue;
                        }

                        if (idx == lenData) {
                            return lenData;
                        }
                    } else {
                        if ((n + idx) <= lenData) {
                            for (i = 0; i < n; i++) {
                                out[idx++] = tmp[i];
                            }
                        } else {
                            int cpLen = lenData - idx;
                            for (i = 0; i < cpLen; i++) {
                                out[idx++] = tmp[i];
                            }

                            lastData = Arrays.copyOfRange(tmp, cpLen, n);
                        }


                        if (idx == lenData) {
                            return lenData;
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();

            }

            return -1;
        }

        public void run() {
            try {
                while (true) {
                    byte[] buffer = new byte[10 * 1024];

                    int len = readData(buffer);

                    if (buffer.length > 0) {
                        if (mHandlers != null) {
                            mHandlers.onReciveHandle(Arrays.copyOf(buffer, len));
                        } else {
                            dumpData(Arrays.copyOf(buffer, len));
                        }

                    }
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            isReadThreadStart = false;
        }
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
                } else if (CMD_SEND_DATA == cmd) {
                    byte[] data = intent.getByteArrayExtra("data");
                    sendQue.offer(data);
                } else if (CMD_START_HEARTBEAT == cmd) {
                    heartBeatThread h = new heartBeatThread();
                    h.start();
                }
            }
        }
    }

}
