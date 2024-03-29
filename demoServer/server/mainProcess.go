package main

import (
	. "../blockData"
	. "../message"
	"net"
	"sync"
	"time"
)
import "log"

const (
	SERVER_STATE_INIT = iota
	SERVER_STATE_READY
)

const (
	RET_OK = iota
	RET_ERROR
)

type IContex struct {
	udpConn           *net.UDPConn
	tcpConn           *net.TCPConn
	deviceAddr        *net.UDPAddr
	mobileAddr        net.Addr
	device2mobileData IBlockData
	mobile2deviceData IBlockData
	State             int
	MsgId             uint32
	MsgIdMutex        *sync.Mutex
	StateMutex        *sync.Mutex
	fileTransMode     bool
	fileInfo          IFileInfo
}

func (this *IContex) sendMessage(msg IMessage) {
	this.StateMutex.Lock()
	if DEVICE == msg.MsgDst {
		MessageSend(this.udpConn, *this.deviceAddr, msg)
	} else if MOBILE == msg.MsgDst {
		MessageSendTCP(this.tcpConn, msg)
	}
	this.StateMutex.Unlock()
}

var main2deviceChan chan *IMessage = make(chan *IMessage, 5)
var main2mobileChan chan *IMessage = make(chan *IMessage, 5)

func readFromUDPConn(udpConn *net.UDPConn) (int, *net.UDPAddr, []byte) {
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

func onAuthenticate(ctx *IContex, msg *IMessage, addr *net.UDPAddr) {
	log.Printf("Auth message received. ip:%v, id = 0x%X...\r\n", addr, msg.MsgSrc)
	if DEVICE != msg.MsgSrc {
		log.Printf("Auth src error, should be device")
	}
	ctx.StateMutex.Lock()
	ctx.deviceAddr = addr
	if ctx.mobileAddr != nil && ctx.deviceAddr != nil {
		ctx.State = SERVER_STATE_READY
	}
	ctx.StateMutex.Unlock()

	msg.MsgType = MSG_ACK
	MessageSend(ctx.udpConn, *addr, *msg)
}

func onTcpAuthenticate(ctx *IContex, msg *IMessage, conn *net.TCPConn) {
	log.Printf("Auth message received. ip:%v, id = 0x%X...\r\n", conn.RemoteAddr(), msg.MsgSrc)
	if MOBILE != msg.MsgSrc {
		log.Printf("Auth src error, should be mobile")
		return
	}

	ctx.StateMutex.Lock()
	ctx.mobileAddr = conn.RemoteAddr()
	if ctx.tcpConn != nil {
		ctx.tcpConn.Close()
	}
	ctx.tcpConn = conn
	if ctx.deviceAddr != nil && ctx.tcpConn != nil {
		ctx.State = SERVER_STATE_READY
	}
	ctx.StateMutex.Unlock()

	msg.MsgType = MSG_ACK
	MessageSendTCP(ctx.tcpConn, *msg)
}

func onOffline(ctx *IContex, msg *IMessage) {
	ctx.StateMutex.Lock()
	if DEVICE == msg.MsgSrc {
		log.Printf("client offline, ip:%v, id = 0x%X...\r\n", ctx.deviceAddr, msg.MsgSrc)
		ctx.deviceAddr = nil
	} else if MOBILE == msg.MsgSrc {
		ctx.mobileAddr = nil
		if ctx.tcpConn != nil {
			log.Printf("client offline, ip:%v, id = 0x%X...\r\n", ctx.tcpConn.RemoteAddr(), msg.MsgSrc)
			ctx.tcpConn.Close()
			ctx.tcpConn = nil
		}
	}

	ctx.State = SERVER_STATE_INIT
	ctx.StateMutex.Unlock()
}

func onTcpHeatBeat(ctx *IContex, msg *IMessage) {
	log.Printf("heatbeat from 0x%X...\r\n", msg.MsgSrc)
	msg.MsgType = MSG_HEARTBEAT_ACK
	MessageSendTCP(ctx.tcpConn, *msg)
}

func onTCPClientLost(ctx *IContex, conn *net.TCPConn) {
	if ctx.tcpConn.RemoteAddr().String() != conn.RemoteAddr().String() {
		// do noting
		log.Printf("connect lost, ip:%v\r\n", conn.RemoteAddr())
		conn.Close()
		return
	}
	log.Printf("connect lost, ip:%v\r\n", ctx.tcpConn.RemoteAddr())
	ctx.StateMutex.Lock()
	ctx.mobileAddr = nil
	if ctx.tcpConn != nil {
		ctx.tcpConn.Close()
		ctx.tcpConn = nil
	}
	ctx.State = SERVER_STATE_INIT
	ctx.StateMutex.Unlock()
}

func tcpConnectLoop(ctx *IContex, tcpListener *net.TCPListener) {
	for {
		conn, err := tcpListener.AcceptTCP()
		if err != nil {
			log.Printf("tcp connet error")
		}
		log.Printf("client connect success.")

		n, data := ReadFromTCPConnTimeout(conn, 5*time.Second)

		//n, data := ReadFromTCPConn(conn)
		if n <= 0 {
			conn.Close()
			continue
		}

		msg := MessageParse(data[:n])
		if msg == nil {
			log.Println("msg parse error, n=", n)
			conn.Close()
			continue
		}

		if MSG_AUTH_REQ == msg.MsgType {
			onTcpAuthenticate(ctx, msg, conn)
		}
	}
}

func tcpProcessLoop(ctx *IContex) {
	for {
		if ctx.tcpConn == nil {
			continue
		}

		conn := ctx.tcpConn

		n, data := ReadFromTCPConn(conn)

		if n <= 0 {
			onTCPClientLost(ctx, conn)
			continue
		}
		msg := MessageParse(data[:n])
		if msg == nil {
			continue
		}

		if MSG_OFFLINE == msg.MsgType {
			onOffline(ctx, msg)
			continue
		}

		if MSG_HEARTBEAT == msg.MsgType {
			onTcpHeatBeat(ctx, msg)
			continue
		}

		if MSG_TRANS_DATA != msg.MsgType && MSG_TRANS_START != msg.MsgType {
			if SERVER_STATE_READY != ctx.State {
				log.Printf("client not ready, msg not handle, from id = 0x%X\r\n", msg.MsgSrc)
				continue
			}
		}

		if MOBILE != msg.MsgSrc {
			log.Printf("msg src error, should be mobile")
			continue
		}

		if MSG_DATA_CONTINUE == msg.MsgType || MSG_DATA_ACK_DONE == msg.MsgType {
			// ack from data receiver
			main2deviceChan <- msg
		} else {
			main2mobileChan <- msg
		}
	}
}

func processLoop(udpConn *net.UDPConn, tcpListener *net.TCPListener) {
	var ctx IContex
	ctx.fileTransMode = false
	ctx.udpConn = udpConn
	ctx.deviceAddr = nil
	ctx.mobileAddr = nil
	ctx.State = SERVER_STATE_INIT
	ctx.MsgId = 0
	ctx.MsgIdMutex = new(sync.Mutex)
	ctx.StateMutex = new(sync.Mutex)

	go tcpConnectLoop(&ctx, tcpListener)
	go tcpProcessLoop(&ctx)
	go deviceProcessLoop(&ctx)
	go mobileProcessLoop(&ctx)

	for true {
		n, addr, data := readFromUDPConn(ctx.udpConn)
		msg := MessageParse(data[:n])

		if msg == nil {
			continue
		}

		if MSG_AUTH_REQ == msg.MsgType {
			onAuthenticate(&ctx, msg, addr)
			continue
		} else if MSG_OFFLINE == msg.MsgType {
			onOffline(&ctx, msg)
			continue
		}

		if SERVER_STATE_READY != ctx.State {
			log.Printf("client not ready, msg not handle, from id = 0x%X\r\n", msg.MsgSrc)
			continue
		}

		if DEVICE != msg.MsgSrc {
			log.Printf("msg src error, should be device")
			continue
		}

		if MSG_DATA_CONTINUE == msg.MsgType || MSG_DATA_ACK_DONE == msg.MsgType {
			// ack from data receiver
			main2mobileChan <- msg
		} else {
			main2deviceChan <- msg
		}
	}

}
