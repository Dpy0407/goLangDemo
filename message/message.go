package message

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
	MSG_AUTH_REQ       = 0x40
	MSG_POST_DATA      = 0x41
	MSG_PUT_DATA       = 0x42
	MSG_OFFLINE        = 0x43
	MSG_DATA_CONTINUE  = 0x60
	MSG_DATA_ERROR     = 0x61
	MSG_INTERNAL_ERROR = 0x62
	MSG_DATA_ACK_DONE  = 0x63
	MSG_ACK            = 0x80
)

type IMessage struct {
	MsgSrc     byte
	MsgDst     byte
	MsgType    byte
	MsgId      uint32
	Payload    []byte
	PayloadLen uint32
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

	msg.MsgSrc = data[4]
	msg.MsgType = data[5]
	msg.MsgId = binary.LittleEndian.Uint32(data[6:10])
	if msgLen > MSG_BASE_LEN {
		msg.Payload = data[MSG_BASE_LEN:]
		msg.PayloadLen = uint32(msgLen - MSG_BASE_LEN)
	}

	return &msg
}

func MessageSerialize(msg IMessage) []byte {
	data := make([]byte, MSG_BASE_LEN)
	binary.LittleEndian.PutUint32(data, uint32(MAGIC_VALUE))
	data[4] = msg.MsgSrc
	data[5] = msg.MsgType
	binary.LittleEndian.PutUint32(data[6:], msg.MsgId)

	if msg.Payload != nil {
		data = append(data, msg.Payload...)
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

func MessageSendWithoutAddr(conn *net.UDPConn, msg IMessage) bool {
	data := MessageSerialize(msg)

	_, err := conn.Write(data)

	if err != nil {
		fmt.Printf("write failed, err: %v\n", err)
		return false
	}

	return true
}
