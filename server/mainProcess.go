package main

import (
	. "../blockData"
	. "../message"
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
	State             int
	MsgId             uint32
	MsgIdMutex        *sync.Mutex
}

func (this *IContex) sendMessage(msg IMessage) {
	var dstAddr *net.UDPAddr
	if DIVICE == msg.MsgDst {
		dstAddr = this.deviceAddr
	} else if MOBILE == msg.MsgDst {
		dstAddr = this.mobileAddr
	}

	if dstAddr != nil {
		MessageSend(this.conn, *dstAddr, msg)
	} else {
		fmt.Printf("message dump:\r\n %v\r\n", msg)
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
		if DIVICE == msg.MsgSrc && MSG_AUTH_REQ == msg.MsgType {
			fmt.Printf("device auth message received. from %v...\r\n", addr)
			ctx.deviceAddr = addr
			msg.MsgType = MSG_ACK
			MessageSend(ctx.conn, *addr, *msg)
			break
		}
	}

}

func onAuthenticate(ctx *IContex, msg *IMessage, addr *net.UDPAddr) {
	fmt.Printf("Auth message received. from %v...\r\n", addr)
	if DIVICE == msg.MsgSrc {
		ctx.deviceAddr = addr
	} else if MOBILE == msg.MsgSrc {
		ctx.mobileAddr = addr
	}
	msg.MsgType = MSG_ACK
	MessageSend(ctx.conn, *addr, *msg)
}

func processLoop(conn *net.UDPConn) {
	var ctx IContex
	ctx.conn = conn
	ctx.deviceAddr = nil
	ctx.mobileAddr = nil
	ctx.State = SERVER_STATE_INIT
	ctx.MsgId = 0
	ctx.MsgIdMutex = new(sync.Mutex)

	go deviceProcessLoop(&ctx)
	go mobileProcessLoop(&ctx)

	for true {
		n, addr, data := readFromConn(ctx.conn)
		msg := MessageParse(data[:n])

		if MSG_AUTH_REQ == msg.MsgType {
			onAuthenticate(&ctx, msg, addr)
			ctx.State = SERVER_STATE_READY
			continue
		}

		if SERVER_STATE_READY != ctx.State {
			continue
		}

		if DIVICE == msg.MsgSrc {
			main2deviceChan <- msg
		} else if MOBILE == msg.MsgSrc {
			main2mobileChan <- msg
		}

	}

}
