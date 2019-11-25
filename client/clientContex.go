package main

import (
	"fmt"
	"net"
)
import . "../message"

type IClientContex struct {
	conn        *net.UDPConn
	authed      bool
	MsgId       uint32
	id          byte
	sendData    IDataExample
	recieveData IDataExample
	msgChan     chan *IMessage
	cmdChan     chan string
}

func (this *IClientContex) onExit() {
	if this.authed {
		var msg IMessage
		msg.MsgSrc = this.id
		msg.MsgType = MSG_OFFLINE
		msg.MsgId = this.MsgId
		this.MsgId += 1
		MessageSendWithoutAddr(this.conn, msg)
	}
}

func (this *IClientContex) authenticate() bool {
	var msg IMessage
	msg.MsgSrc = this.id
	msg.MsgType = MSG_AUTH_REQ
	msg.MsgId = this.MsgId
	this.MsgId += 1

	MessageSendWithoutAddr(this.conn, msg)
	msg = *this.getMessage()
	if msg.MsgType != MSG_ACK {
		this.authed = false
		return false
	}
	this.authed = true
	return true
}

func (this *IClientContex) getMessage() *IMessage {
	n, _, data := readFromConn(this.conn)
	return MessageParse(data[:n])
}

func (this *IClientContex) sendMessage(msg IMessage) {
	MessageSendWithoutAddr(this.conn, msg)
}

func getCommand(ctx *IClientContex) {
	//time.AfterFunc(2*time.Second, func() {
	//	ctx.cmdChan <- "send"
	//})
	for {
		var cmd string
		fmt.Scanln(&cmd)
		if cmd != "" {
			ctx.cmdChan <- cmd
		}
	}
}

func (this *IClientContex) loop() {
	this.cmdChan = make(chan string, 5)
	this.msgChan = make(chan *IMessage, 5)

	go getCommand(this)
	go onProcess(this)
	for {
		msg := this.getMessage()
		this.msgChan <- msg
	}
	//this.sendData.InitData()
	//this.dataSend()

}

func (this *IClientContex) dataSend() {

	for {
		data := this.sendData.GetSendData()
		var msg IMessage
		msg.MsgSrc = this.id
		msg.MsgType = MSG_POST_DATA
		msg.MsgId = this.MsgId
		this.MsgId += 1
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
