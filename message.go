package main

import (
	"encoding/binary"
	"net"
)
import "fmt"

const MAGIC_VALUE = 603160
const DIVICE = 0x30
const MOBILE = 0x31
const SERVER = 0x32

const (
	MSG_AUTH_REQ  = 0x40
	MSG_POST_DATA = 0x41

	MSG_CONTINUE = 0x60
	MSG_ERROR    = 0x61
	MSG_ACK      = 0x80
)

type IMessage struct {
	msgSrc     byte
	msgType    byte
	msgID      uint32
	payload    []byte
	payloadLen uint32
}

const MSG_BASE_LEN = 10

func MessageParse(data []byte) *IMessage {
	var msg IMessage

	msgLen := len(data)
	if msgLen < MSG_BASE_LEN {
		return nil
	}

	magic := binary.LittleEndian.Uint32(data[0:4])

	if magic != MAGIC_VALUE {
		fmt.Println("magic number error, %d", magic)
		return nil
	}

	msg.msgSrc = data[4]
	msg.msgType = data[5]
	msg.msgID = binary.LittleEndian.Uint32(data[6:10])
	if msgLen > MSG_BASE_LEN {
		msg.payload = data[MSG_BASE_LEN:]
		msg.payloadLen = uint32(msgLen - MSG_BASE_LEN)
	}

	return &msg
}

func MessageSerialize(msg IMessage) []byte {
	data := make([]byte, MSG_BASE_LEN)
	binary.LittleEndian.PutUint32(data, uint32(MAGIC_VALUE))
	data[4] = msg.msgSrc
	data[5] = msg.msgType
	binary.LittleEndian.PutUint32(data[6:], msg.msgID)

	if msg.payload != nil {
		data = append(data, msg.payload...)
	}

	return data
}

func MessageSend(conn *net.UDPConn, addr net.UDPAddr, msg IMessage) bool {
	data := MessageSerialize(msg)

	_, err := conn.WriteToUDP(data, &addr)

	if err != nil {
		fmt.Printf("write failed, err: %v\n", err)
		return false
	}

	return true
}
