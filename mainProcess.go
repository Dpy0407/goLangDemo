package main

import (
	"net"
	"sync"
)
import "fmt"

const (
	SERVER_STATE_INIT = iota
	SERVER_STATE_READY
)

const (
	RET_OK = iota
	RET_ERROR
)

type IContex struct {
	conn              *net.UDPConn
	deviceAddr        *net.UDPAddr
	mobileAddr        *net.UDPAddr
	device2mobileData IBlockData
	mobile2deviceData IBlockData
	state             int
	msgId             uint32
	msgIdMutex        *sync.Mutex
}

func (this *IContex) sendMessage(msg IMessage) {
	var dstAddr *net.UDPAddr
	if DIVICE == msg.msgDst {
		dstAddr = this.deviceAddr
	} else if MOBILE == msg.msgDst {
		dstAddr = this.mobileAddr
	}

	if dstAddr != nil {
		MessageSend(this.conn, *dstAddr, msg)
	} else {
		fmt.Printf("message dump: %v", msg)
	}

}

var main2deviceChan chan *IMessage = make(chan *IMessage, 5)
var main2mobileChan chan *IMessage = make(chan *IMessage, 5)

func readFromConn(conn *net.UDPConn) (int, *net.UDPAddr, []byte) {
	data := make([]byte, 1024)
	for true {
		n, addr, err := conn.ReadFromUDP(data)
		if err != nil {
			fmt.Println("read failed from addr: %v, err: %v\n", addr, err)
			continue
		}

		if n < MSG_BASE_LEN {
			fmt.Println("Invalid Data")
			continue
		}

		return n, addr, data
	}

	// never
	return -1, nil, nil
}

func clientConnectConfirm(ctx IContex) {
	for true {
		n, addr, data := readFromConn(ctx.conn)

		msg := MessageParse(data[:n])
		if DIVICE == msg.msgSrc && MSG_AUTH_REQ == msg.msgType {
			fmt.Println("device auth message recieved.")
			ctx.deviceAddr = addr
			msg.msgType = MSG_ACK
			MessageSend(ctx.conn, *addr, *msg)
			break
		}
	}

}

func processLoop(conn *net.UDPConn) {
	var ctx IContex
	ctx.conn = conn
	ctx.deviceAddr = nil
	ctx.mobileAddr = nil
	ctx.state = SERVER_STATE_INIT
	ctx.msgId = 0
	ctx.msgIdMutex = new(sync.Mutex)

	for true {
		// we need to wait device connet to server first
		if SERVER_STATE_READY != ctx.state {
			clientConnectConfirm(ctx)
			ctx.state = SERVER_STATE_READY

			go deviceProcessLoop(ctx)
			go mobileProcessLoop(ctx)
		}

		n, _, data := readFromConn(ctx.conn)

		msg := MessageParse(data[:n])

		if DIVICE == msg.msgSrc {
			main2deviceChan <- msg
		} else if MOBILE == msg.msgSrc {
			main2mobileChan <- msg
		}

	}

}
