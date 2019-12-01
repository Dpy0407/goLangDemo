package com.demo.pushtotalk;

import android.util.Log;

import java.sql.Array;
import java.util.Arrays;

public class DemoMessage implements Common{
    private static final String TAG = "MSG";
    byte msgSrc;
    byte msgDst;
    byte msgType;
    int msgId;
    byte[] payload;
    int payloadLen;

    public boolean parse(byte[] data) {
        int msgLen = data.length;
        if (msgLen < MSG_BASE_LEN) {
            return false;
        }

        int magic = data[0] << 24 | data[1] << 16 | data[2] << 8 | data[3];

        if (magic != MAGIC_VALUE) {
            Log.e(TAG, "magic number error! magic:" + magic);
            return false;
        }

        this.msgSrc = data[4];
        this.msgType = data[5];
        this.msgId = data[6] << 24 | data[7] << 16 | data[8] << 8 | data[9];
        if (msgLen > MSG_BASE_LEN) {
            this.payload = Arrays.copyOfRange(data, 10, msgLen);
            this.payloadLen = msgLen - MSG_BASE_LEN;
        }

        return true;
    }

    static public byte[] int2arr(int a) {
        byte[] result = new byte[4];
        result[0] = (byte) (a & 0xFF);
        result[1] = (byte) ((a >> 8) & 0xFF);
        result[2] = (byte) ((a >> 16) & 0xFF);
        result[3] = (byte) ((a >> 24) & 0xFF);
        return result;
    }

    static public int arr2int(byte[] arr) {
        int result = 0;

        result = arr[0] | arr[1] << 8 | arr[2] << 16 | arr[3] << 24;

        return result;
    }

   static public String arr2HexString(byte[]data){
        String s="";
        for(int i=0;i<data.length;i++){
            s += String.format(" 0x%02X", data[i]);
        }
        return s;
    }

}
