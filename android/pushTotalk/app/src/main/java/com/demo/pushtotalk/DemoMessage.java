package com.demo.pushtotalk;

import android.util.Log;

import java.sql.Array;
import java.util.Arrays;
import java.util.List;

public class DemoMessage implements Common {
    private static final String TAG = "[*** MSG]";
    byte msgSrc;
    byte msgDst;
    byte msgType;
    int msgId;
    byte[] payload;
    int payloadLen;
    static int baseLen;

    DemoMessage() {
        payload = null;
        payloadLen = 0;
        msgId = 0;
        baseLen = 10;
    }

    public boolean parse(byte[] data) {
        int msgLen = data.length;
        if (msgLen < MSG_BASE_LEN) {
            return false;
        }

        int magic = data[0] | data[1] << 8 | data[2] << 16 | data[3] << 24;

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

    public byte[] serialize() {
        byte[] tmp = int2arr(MAGIC_VALUE);
        byte[] data = Arrays.copyOf(tmp, baseLen + payloadLen);
        data[4] = msgSrc;
        data[5] = msgType;
        tmp = int2arr(msgId);
        for (int i = 0; i < 4; i++) {
            data[6 + i] = tmp[i];
        }

        // payload
        for (int i = 0; i < payloadLen; i++) {
            data[baseLen + i] = payload[i];
        }
        return data;
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

        result |= arr[0] & 0xFF;
        result |= (arr[1] & 0xFF) << 8;
        result |= (arr[2] & 0xFF) << 16;
        result |= (arr[3] & 0xFF) << 24;

        return result;
    }

    static public String arr2HexString(byte[] data) {
        String s = "";
        for (int i = 0; i < data.length; i++) {
            s += String.format(" 0x%02X", data[i]);
        }
        return s;
    }

}
