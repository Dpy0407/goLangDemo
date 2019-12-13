package com.demo.pushtotalk;


public interface Common {
    final String TCP_INTENT_ACTION_CMD = "android.intent.action.tcp.cmd";
    //
    final int CONFIG_HEARTBEAT_PERIOD = 30;  // second
    final String CONFIG_SERVER_IP = "192.168.43.61"; //192.168.43.61 101.207.235.190
    final int CONFIG_SERVER_PORT = 8081;
    final int CONFIG_SPEAK_MAX_SECOND = 5;

    final int MAGIC_VALUE = 603160;
    final byte DEVICE = 0x30;
    final byte MOBILE = 0x31;
    final byte SERVER = 0x32;

    final int MSG_BASE_LEN = 10;
    // --- define msg types ---
    final byte MSG_AUTH_REQ = 0x40;
    final byte MSG_POST_DATA = 0x41;
    final byte MSG_PUT_DATA = 0x42;
    final byte MSG_OFFLINE = 0x43;
    final byte MSG_TRANS_START = 0x50;
    final byte MSG_TRANS_DATA = 0x51;
    final byte MSG_TRANS_ACK = 0x52;
    final byte MSG_DATA_CONTINUE = 0x60;
    final byte MSG_DATA_ERROR = 0x61;
    final byte MSG_INTERNAL_ERROR = 0x62;
    final byte MSG_DATA_ACK_DONE = 0x63;
    final byte MSG_HEARTBEAT = 0x70;
    final byte MSG_HEARTBEAT_ACK = 0x71;
    final byte MSG_ACK = (byte) 0x80;

    // --- cmd define ---
    final int CMD_CONNECT_SERVER = 0x00;
    final int CMD_DISCONNECT_SERVER = 0x01;
    final int CMD_SEND_DATA = 0x03;
    final int CMD_START_HEARTBEAT = 0x04;
    final int CMD_NOTIFY_RESULT = 0x05;

    // --- block params ---
    final int DATA_MOBILE_SEND_STEP = 20 * 1024;
    final int DATA_RETRY_MAX_CNT = 1;
    final int DATA_RETRY_TIMEOUT = 2 * 1000; // ms

    // --- messages define used by UI & thread
    final int MSG_H_VOICE_SAVE_SUCCESS = 0;
    final int MSG_H_VOICE_SAVE_FAILED = 1;
    final int MSG_H_VOICE_PLAYING = 2;
    final int MSG_H_VOICE_PLAY_END = 3;
    final int MSG_H_VOICE_ALL_CLEARED = 4;
    final int MSG_H_VOICE_CLRAED = 5;

    final int MSG_H_VOICE_BUSY_SENDING = 10;
    final int MSG_H_VOICE_SEND_SUCCESS =11;
    final int MSG_H_VOICE_SEND_FAILED = 12;
    final int MSG_H_VOICE_SEND_RETRY = 13;

    final int MSG_H_CONNECT_FAILED = 20;
    final int MSG_H_CONNECT_SUCCESS = 21;
    final int MSG_H_AUTH_SUCCESS = 22;
    final int MSG_H_SERVER_DISCONNECT = 23;
    final int MSG_H_SERVER_NOT_CONNECT = 24;


    // --- voice type --
    enum VoiceType {
        SEND,
        RECEIVE,
        INVAID
    }

    enum VoiceStatus {
        NONE,
        SENDING,
        SEND_FAILED,
        UNREAD
    }

    enum ClientState {
        STATE_INIT,
        STATE_AUTHING,
        STATE_AUTHED
    }

    enum BlockState {
        DATA_STATE_EMPTY,
        DATA_STATE_RICIEVING,
        DATA_STATE_SEND_REQUIRED,
        DATA_STATE_TRANS_REQ,
        DATA_STATE_SENDING,
        DATA_STATE_SEND_DONE,
        DATA_STATE_DONE
    }

    interface DataHandlers {
        void onReciveHandle(byte[] data);

        void onNotify(int cmd, int value);
    }
}

