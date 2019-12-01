package com.demo.pushtotalk;


public interface Common {
    final String TCP_INTENT_ACTION_CMD = "android.intent.action.tcp.cmd";


    final int MAGIC_VALUE = 603160;
    final byte DEVICE = 0x30;
    final byte MOBILE = 0x31;
    final byte SERVER = 0x32;

    final int  MSG_BASE_LEN = 10;
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
    final byte MSG_ACK = (byte)0x80;

    // --- cmd define ---
    final int CMD_CONNECT_SERVER = 0x00;
    final int CMD_DISCONNECT_SERVER = 0x01;
    final int CMD_SEND_DATA = 0x03;



}

