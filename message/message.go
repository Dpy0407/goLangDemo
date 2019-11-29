package message

import (
	"encoding/binary"
	"net"
)
import "log"

const MAGIC_VALUE = 603160
const DEVICE = 0x30
const MOBILE = 0x31
const SERVER = 0x32

const (
	MSG_AUTH_REQ       = 0x40
	MSG_POST_DATA      = 0x41
	MSG_PUT_DATA       = 0x42
	MSG_OFFLINE        = 0x43
	MSG_TRANS_START    = 0x50
	MSG_TRANS_DATA     = 0x51
	MSG_TRANS_ACK      = 0x52
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
		log.Printf("magic number error, %d\r\n", magic)
		log.Println(data)
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

func MessageSend(udpConn *net.UDPConn, addr net.UDPAddr, msg IMessage) bool {
	if udpConn == nil {
		log.Printf("udp conn is empty!")
		return false
	}

	data := MessageSerialize(msg)

	_, err := udpConn.WriteToUDP(data, &addr)

	if err != nil {
		log.Printf("write failed, err: %v\n", err)
		return false
	}

	return true
}

func MessageSendWithoutAddr(udpConn *net.UDPConn, msg IMessage) bool {
	if udpConn == nil {
		log.Printf("udp conn is empty!")
		return false
	}

	data := MessageSerialize(msg)

	_, err := udpConn.Write(data)

	if err != nil {
		log.Printf("write failed, err: %v\n", err)
		return false
	}

	return true
}

func MessageSendTCP(tcpConn *net.TCPConn, msg IMessage) bool {
	if tcpConn == nil {
		log.Printf("tcp conn is empty!")
		return false
	}

	data := MessageSerialize(msg)

	tmp := make([]byte, 4)
	binary.LittleEndian.PutUint32(tmp, uint32(len(data)))
	data = append(tmp, data...)

	//log.Println(data)
	_, err := tcpConn.Write(data)

	if err != nil {
		log.Printf("write failed, err: %v\n", err)
		return false
	}

	return true
}

var glastData []byte

func ReadFromTCPConn(tcpConn *net.TCPConn) (int, []byte) {
	tmp := make([]byte, 10*1024)
	var data []byte
	lenData := -1
	t := 0
	for true {
		n, err := tcpConn.Read(tmp)
		if err != nil {
			t++
			log.Printf("tcp read failed, err: %v\r\n", err)
			if t > 2 {
				break
			}
			continue
		}

		if -1 == lenData {
			glastData = append(glastData, tmp[:n]...)
			if len(glastData) >= 4 {
				lenData = int(binary.LittleEndian.Uint32(glastData[0:4]))
				glastData = glastData[4:]

				if len(glastData) <= lenData {
					data = append(data, glastData...)
					glastData = glastData[0:0]
				} else {
					data = append(data, glastData[:lenData]...)
					glastData = glastData[lenData:]
				}
			} else {
				continue
			}

			if len(data) == lenData {
				return lenData, data
			}
		} else {
			if n+len(data) <= lenData {
				data = append(data, tmp[:n]...)
			} else {
				data = append(data, tmp[:lenData-len(data)]...)
				glastData = append(glastData, tmp[lenData-len(data):n]...)
			}

			if len(data) == lenData {
				return lenData, data
			}
		}

	}

	return 0, nil
}
