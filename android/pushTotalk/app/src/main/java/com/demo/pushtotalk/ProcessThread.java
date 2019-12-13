package com.demo.pushtotalk;

import android.util.Log;

public class ProcessThread extends Thread implements Common {
    static private String TAG = "*** ProcessThread";
    private MainActivity context = null;
    private TcpService service = null;

    ProcessThread(MainActivity ctx) {
        this.context = ctx;
    }

    public void SetTcpService(TcpService s) {
        this.service = s;
    }

    public void run() {
        try {
            while (true) {
                DemoMessage msg = context.msgQue.poll();
                if (msg != null) {
                    msgHandle(msg);
                }

                onStep();
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void onStep() {
        if (BlockState.DATA_STATE_SENDING == context.mSendData.State) {
            if (System.currentTimeMillis()-context.mSendData.SendTime > DATA_RETRY_TIMEOUT){
                if (context.mSendData.RetryCnt > 0) {
                    Log.e(TAG, "timeout, sending retry, cnt:"+ context.mSendData.RetryCnt);
                    byte[] data = context.mSendData.GetLastSendData();
                    dataSend(data);
                    context.mSendData.RetryCnt -= 1;
                } else {
                    // give up current data, init dataBlock
                    context.mSendData.Init();
                    context.onSendVoiceResult(false);
                }
            }
        }else if (BlockState.DATA_STATE_SEND_DONE == context.mSendData.State){
            context.mSendData.Init();
            context.onSendVoiceResult(true);
        }

        if (BlockState.DATA_STATE_DONE == context.mReviceData.State) {

            if((context.mReviceData.BlockToken & 0xF) != 0){
                // this is true data
                context.saveVoice(context.mReviceData.RawData);
            }else{
                // this is debug data
                context.mReviceData.dumpData(1);
            }

            context.mReviceData.Init();
        }
    }

    public void msgHandle(DemoMessage msg) {
        switch (msg.msgType) {
            case MSG_PUT_DATA:
                onDataReceived(msg);
                break;

            case MSG_DATA_CONTINUE:
                onDataContinue(msg);
                break;

            case MSG_DATA_ERROR:
                onDataError(msg);
                break;

            case MSG_DATA_ACK_DONE:
                onDataAckDone(msg);
                break;

            default:
                break;
        }
    }

    private void onDataReceived(DemoMessage msg) {
        DemoMessage resp = new DemoMessage();
        byte ret = context.mReviceData.OnRevice(msg.payload);
        resp.msgSrc = MOBILE;
        resp.msgId = msg.msgId;
        resp.msgType = ret;
        resp.payload = null;

        sendMessage(resp);
    }

    private void onDataContinue(DemoMessage msg) {
        if (BlockState.DATA_STATE_SENDING != context.mSendData.State) {
            Log.e(TAG, "invalid request");
            return;
        }

        byte[] data = context.mSendData.GetSendData();
        if(data == null){
            Log.e(TAG, "data is empty.");
            context.mSendData.Init();
            return;
        }

        dataSend(data);
    }


    private void onDataError(DemoMessage msg) {
        if(BlockState.DATA_STATE_SENDING == context.mSendData.State){
            byte[] data = context.mSendData.GetLastSendData();
            dataSend(data);
        }
    }


    private void onDataAckDone(DemoMessage msg) {
        context.mSendData.State = BlockState.DATA_STATE_SEND_DONE;
    }

    public void dataSend(byte[] data){
        if(data == null){
            return;
        }

        DemoMessage msg = new DemoMessage();
        msg.msgSrc = MOBILE;
        msg.msgType = MSG_POST_DATA;
        msg.msgId = context.mMsgId++;
        msg.payload = data;
        msg.payloadLen = data.length;

        sendMessage(msg);
    }

    public void sendMessage(DemoMessage msg) {
        byte[] data = msg.serialize();
        if (service == null) {
            Log.e(TAG, "service not ready!");
            return;
        }
        service.cmdHandle(CMD_SEND_DATA, data);
    }

}
