package main

import (
	. "../blockData"
	. "../message"
	"fmt"
	"log"
	"net"
	"os"
)

type IClientContex struct {
	udpConn      *net.UDPConn
	tcpConn      *net.TCPConn
	authed       bool
	MsgId        uint32
	id           byte
	sendData     IDataExample
	recieveData  IDataExample
	msgChan      chan *IMessage
	cmdChan      chan string
	fileTranMode bool
	fileInfo     IFileInfo
}

func (this *IClientContex) onExit() {
	if this.authed {
		var msg IMessage
		msg.MsgSrc = this.id
		msg.MsgType = MSG_OFFLINE
		msg.MsgId = this.MsgId
		this.MsgId += 1
		this.sendMessage(msg)
	}
}

func (this *IClientContex) authenticate() bool {
	var msg IMessage
	msg.MsgSrc = this.id
	msg.MsgType = MSG_AUTH_REQ
	msg.MsgId = this.MsgId
	this.MsgId += 1

	if DEVICE == this.id {
		MessageSendWithoutAddr(this.udpConn, msg)
	} else {
		MessageSendTCP(this.tcpConn, msg)
	}

	msg = *this.getMessage()
	if msg.MsgType != MSG_ACK {
		this.authed = false
		return false
	}
	this.authed = true
	return true
}

func (this *IClientContex) getMessage() *IMessage {
	var data []byte
	n := -1
	if DEVICE == this.id {
		n, _, data = readFromConn(this.udpConn)
	} else if MOBILE == this.id {
		n, data = ReadFromTCPConn(this.tcpConn)
		if n <= 0 {
			// tcp connect lost, exit
			os.Exit(0)
		}
	} else {
		return nil
	}

	return MessageParse(data[:n])
}

func (this *IClientContex) sendMessage(msg IMessage) {
	if DEVICE == this.id {
		MessageSendWithoutAddr(this.udpConn, msg)
	} else if MOBILE == this.id {
		MessageSendTCP(this.tcpConn, msg)
	}
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
		if msg != nil {
			this.msgChan <- msg
		}
	}
}

//func readFromTCPConn(tcpConn *net.TCPConn) (int, []byte) {
//	data := make([]byte, 10*1024)
//	t := 0
//	for true {
//		n, err := tcpConn.Read(data)
//		if err != nil {
//			t++
//			log.Printf("tcp read failed, err: %v\r\n", err)
//			if t > 2 {
//				// tcp connect lost, exit
//				os.Exit(0)
//			}
//			continue
//		}
//
//		if n < MSG_BASE_LEN {
//			log.Println("Invalid Data")
//			continue
//		}
//
//		return n, data
//	}
//
//	// never
//	return -1, nil
//}

func readFromConn(udpConn *net.UDPConn) (int, *net.UDPAddr, []byte) {
	data := make([]byte, 2048)
	for true {
		n, addr, err := udpConn.ReadFromUDP(data)
		if err != nil {
			log.Printf("read failed from addr: %v, err: %v\r\n", addr, err)
			continue
		}

		if n < MSG_BASE_LEN {
			log.Println("Invalid Data")
			continue
		}

		return n, addr, data
	}

	// never
	return -1, nil, nil
}
