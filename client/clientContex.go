package main

import (
	"fmt"
	"net"
	"time"
)
import . "../message"

type IClientContex struct {
	conn        *net.UDPConn
	msgId       uint32
	id          byte
	sendData    IDataExample
	recieveData IDataExample
}

func (this *IClientContex) authenticate() bool {
	var msg IMessage
	msg.MsgSrc = this.id
	msg.MsgType = MSG_AUTH_REQ
	msg.MsgId = this.msgId
	this.msgId += 1

	MessageSendWithoutAddr(this.conn, msg)
	msg = *this.getMessage()
	if msg.MsgType != MSG_ACK {
		return false
	}

	return true
}

func (this *IClientContex) getMessage() *IMessage {
	n, _, data := readFromConn(this.conn)
	return MessageParse(data[:n])
}

func (this *IClientContex) loop() {
	this.sendData.InitData()
	for {
		this.dataSend()
		time.Sleep(2 * time.Second)
		this.sendData.InitData()
		this.dataSend()
		break
	}

}

func (this *IClientContex) dataSend() {

	for {
		data := this.sendData.GetSendData()
		var msg IMessage
		msg.MsgSrc = this.id
		msg.MsgType = MSG_POST_DATA
		msg.MsgId = this.msgId
		this.msgId += 1
		msg.Payload = data

		retFlag := false
		for {
			fmt.Printf("send data to server, seq: %d\r\n", this.sendData.SendSeq)
			MessageSendWithoutAddr(this.conn, msg)
			this.sendData.RetryCnt -= 1
			resp := this.getMessage()
			fmt.Printf("get Ack: 0x%X\r\n", resp.MsgType)
			if resp.MsgType == MSG_DATA_CONTINUE {
				break
			} else if MSG_DATA_ACK_DONE == resp.MsgType {
				return
			} else {
				if this.sendData.RetryCnt > 0 {
					continue
				} else {
					retFlag = true
					break
				}
			}

		}

		if retFlag {
			return
		}

	}
}
