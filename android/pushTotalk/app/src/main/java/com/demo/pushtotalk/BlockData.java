package com.demo.pushtotalk;

import java.util.Arrays;
import java.util.Random;

import android.util.Log;


public class BlockData implements Common {
    static String TAG = "[*** BLKD]";
    public byte[] RawData;
    public int DataLen;
    public int BlockToken;
    public int LastSeq; // recieve sequence
    public BlockState State;
    public int SendStep;    // send step
    public int SendSeq; // send sequence
    public long SendTime;  // send time, used for timeout handle
    public int RetryCnt;
    public byte[] lastData;

    BlockData() {
        this.Init();
    }

    public void Init() {
        this.RawData = null;
        this.DataLen = 0;
        this.BlockToken = 0;
        this.LastSeq = 0;
        this.State = BlockState.DATA_STATE_EMPTY;
        this.SendSeq = 0;
        this.SendTime = -1;
        this.RetryCnt = DATA_RETRY_MAX_CNT;
    }

    public byte OnRevice(byte[] data) {
        byte ret;

        int offset = 0;
        int token = DemoMessage.arr2int(data);
        offset += 4;
        int seq = DemoMessage.arr2int(Arrays.copyOfRange(data, offset, offset + 4));
        offset += 4;
        byte more = data[offset];
        offset += 1;

        int len = data.length - offset;
        int i = 0;

        do {
            if (this.State != BlockState.DATA_STATE_EMPTY && this.State != BlockState.DATA_STATE_RICIEVING) {
                ret = MSG_INTERNAL_ERROR;
                break;
            }

            // this is the first sequence
            if (0 == seq) {
                // do init
                this.Init();
                this.BlockToken = token;
            } else {
                if (token != this.BlockToken) {
                    Log.d(TAG, "data token error, current token =" + this.BlockToken + "received token = " + token);
                    ret = MSG_DATA_ERROR;
                    break;
                }
            }

            // some block lost before
            if (seq != 0 && this.LastSeq + 1 != seq) {
                Log.d(TAG, "data seq error, will be abandoned, LastSeq = " + this.LastSeq + "received seq = " + seq);
            } else {
                int lastLen = 0;
                if (this.RawData != null) {
                    lastLen = this.RawData.length;
                    this.RawData = Arrays.copyOf(this.RawData, lastLen + len);
                } else {
                    this.RawData = new byte[len];
                }

                for (i = 0; i < len; i++) {
                    this.RawData[lastLen + i] = data[offset + i];
                }
                this.LastSeq = seq;
            }

            if (0 == more) {
                this.State = BlockState.DATA_STATE_DONE;
                this.DataLen = this.RawData.length;
                ret = MSG_DATA_ACK_DONE;
            } else {
                this.State = BlockState.DATA_STATE_RICIEVING;
                ret = MSG_DATA_CONTINUE;
            }

        } while (false);

        return ret;
    }


    public byte[] GetSendData() {
        byte[] data = DemoMessage.int2arr(this.BlockToken);
        int start, end;
        byte more = 0;
        int offset = data.length;
        int i = 0;

        do {
            if (this.DataLen < this.SendStep) {
                start = 0;
                end = this.DataLen;
                more = 0;
                break;
            }

            int sendCnt = this.SendSeq * this.SendStep;

            int len = this.DataLen - sendCnt;
            if (len <= 0) {
                return null;
            }

            int senLen;
            if (len > this.SendStep) {
                senLen = this.SendStep;
                more = 1;
            } else {
                senLen = len;
                more = 0;
            }
            start = sendCnt;
            end = start + senLen;
            break;
        } while (false);

        data = Arrays.copyOf(data, offset + 5 + end - start);
        byte[] seq = DemoMessage.int2arr(this.SendSeq);
        for (i = 0; i < 4; i++) {
            data[offset + i] = seq[i];
        }
        offset += 4;
        data[offset++] = more;

        for (i = start; i < end; i++) {
            data[offset + i] = this.RawData[i];
        }

        this.SendSeq += 1;
        this.SendTime = System.currentTimeMillis();
        this.RetryCnt = DATA_RETRY_MAX_CNT;
        this.lastData = data;
        return data;
    }

    public byte[] GetLastSendData() {
        this.SendTime = System.currentTimeMillis();
        return this.lastData;
    }


    public void randomInit() {
        this.Init();
        this.SendStep = DATA_MOBILE_SEND_STEP;

        Random rand = new Random(System.currentTimeMillis());
        this.BlockToken = rand.nextInt();
        int dlen = 2048 + rand.nextInt(4096);
        this.RawData = new byte[dlen];

        for (int i = 0; i < dlen - 4; i += 4) {
            int x = rand.nextInt();
            this.RawData[i] = (byte) (x & 0xFF);
            this.RawData[i + 1] = (byte) (x >> 8 & 0xFF);
            this.RawData[i + 2] = (byte) (x >> 16 & 0xFF);
            this.RawData[i + 3] = (byte) (x >> 24 & 0xFF);
        }

        int x = rand.nextInt();
        this.RawData[dlen - 4] = (byte) (x & 0xFF);
        this.RawData[dlen - 3] = (byte) (x >> 8 & 0xFF);
        this.RawData[dlen - 2] = (byte) (x >> 16 & 0xFF);
        this.RawData[dlen - 1] = (byte) (x >> 24 & 0xFF);

        this.DataLen = dlen;
        this.dumpData(0);
    }

    public void dumpData(int ori){
        String s ;
        int i =0;
        if (ori == 0) {
            s = ">>>";
        } else {
            s = "<<<";
        }

        Log.d(TAG, s + "    data token:" + String.format("0x%08X", this.BlockToken));
        Log.d(TAG, s + "    data len:  " + this.DataLen);

        int dlen = this.DataLen;
        if(dlen>32){
            String content = s + "    content:  [";

            content += DemoMessage.arr2HexString(Arrays.copyOfRange(this.RawData,0,8));
            content += " ...";

            content+= DemoMessage.arr2HexString(Arrays.copyOfRange(this.RawData,dlen-8,dlen));
            content +=" ]";

            Log.d(TAG, content);
        }
    }

}
