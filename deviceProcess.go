package main

import (
	"encoding/binary"
	"fmt"
)

var reciveData IBlockData

func onDeviceProcess(ctx IContex, msg IMessage) {
	switch msg.msgType {
	case MSG_POST_DATA:

		break
	default:
		break
	}
}

func onDataRecieved(ctx IContex, msg IMessage) {
	var resp IMessage
	data := msg.payload
	token := binary.LittleEndian.Uint32(data[:4])
	seq := data[4]
	more := data[5]

	resp.msgSrc = SERVER
	resp.msgID = msg.msgID

	// this is the first sequency
	if 0 == seq {
		reciveData.blockToken = token
	} else {
		if token != reciveData.blockToken {
			fmt.Printf("data token error, current token = %d, recived token = %d", reciveData.blockToken, token)
			resp.msgType = MSG_ERROR
			goto SEND_LABLE
		}
	}

	reciveData.rawData = append(reciveData.rawData, data[6:]...)
	reciveData.lastSeq = seq

	if 0 == more {
		reciveData.state = DATA_STATE_SEND_REQUIRED
	} else {
		reciveData.state = DATA_STATE_RICIEVING
	}

SEND_LABLE:
}
