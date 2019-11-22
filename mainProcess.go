package main

import "net"
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
	conn       *net.UDPConn
	deviceAddr *net.UDPAddr
	mobileAddr *net.UDPAddr
	state      int
}

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

	for true {
		// we need to wait device connet to server first
		if SERVER_STATE_READY != ctx.state {
			clientConnectConfirm(ctx)
			ctx.state = SERVER_STATE_READY
		}

		n, _, data := readFromConn(ctx.conn)

		msg := MessageParse(data[:n])

		if DIVICE == msg.msgSrc {
			onDeviceProcess(ctx, *msg)
		}

	}

}
