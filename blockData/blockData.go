package blockData

import (
	. "../message"
	"encoding/binary"
	"fmt"
	"time"
)

const DATA_BASE_SEND_STEP = 128
const DATA_DEVICE_SEND_STEP = 128
const DATA_MOBILE_SEND_STEP = 512
const DATA_RETRY_MAX_CNT = 3 // retry 3 times
const DATA_RETRY_TIMEOUT = 5 // retry timeout 5s

const (
	DATA_STATE_EMPTY = iota
	DATA_STATE_RICIEVING
	DATA_STATE_SEND_REQUIRED
	DATA_STATE_SENDING
	DATA_STATE_DONE = DATA_STATE_SEND_REQUIRED
)

type IBlockData struct {
	RawData    []byte
	DataLen    int
	BlockToken uint32
	LastSeq    uint8 // recieve sequence
	State      int
	SendStep   int   // send step
	SendSeq    uint8 // send sequence
	SendTime   int64 // send time, used for timeout handle
	RetryCnt   int
}

func (this *IBlockData) Init() {
	this.RawData = this.RawData[:0]
	this.DataLen = 0
	this.BlockToken = 0
	this.LastSeq = 0
	this.State = DATA_STATE_EMPTY
	this.SendStep = DATA_BASE_SEND_STEP
	this.SendSeq = 0
	this.SendTime = -1
	this.RetryCnt = DATA_RETRY_MAX_CNT
}

func (this *IBlockData) OnReceive(data []byte) byte {
	var ret byte
	token := binary.LittleEndian.Uint32(data[:4])
	seq := data[4]
	more := data[5]

	for {
		if this.State != DATA_STATE_EMPTY && this.State != DATA_STATE_RICIEVING {
			ret = MSG_INTERNAL_ERROR
			break
		}

		// this is the first sequence
		if 0 == seq {
			// do init
			this.Init()
			this.BlockToken = token
		} else {
			if token != this.BlockToken {
				fmt.Printf("data token error, current token = %d, received token = %d\r\n", this.BlockToken, token)
				ret = MSG_DATA_ERROR
				break
			}

			// some block lost before
			if this.LastSeq+1 != seq {
				fmt.Printf("data receive sequence error, LastSeq=%d, received seq = %d\r\n", this.LastSeq, seq)
				ret = MSG_DATA_ERROR
				break
			}
		}

		this.RawData = append(this.RawData, data[6:]...)
		this.LastSeq = seq

		if 0 == more {
			this.State = DATA_STATE_SEND_REQUIRED
			this.DataLen = len(this.RawData)
			this.SendSeq = 0
			ret = MSG_DATA_ACK_DONE
		} else {
			this.State = DATA_STATE_RICIEVING
			ret = MSG_DATA_CONTINUE
		}

		break
	}

	return ret
}

func (this *IBlockData) GetSendData() []byte {
	data := make([]byte, 4)
	binary.LittleEndian.PutUint32(data, this.BlockToken)
	var start, end int
	var more uint8 = 0
	for {
		if this.DataLen < this.SendStep {
			start = 0
			end = this.DataLen
			more = 0
			break
		}

		sendCnt := int(this.SendSeq) * this.SendStep

		len := this.DataLen - sendCnt
		if len <= 0 {
			return nil
		}
		var senLen int
		if len > this.SendStep {
			senLen = this.SendStep
			more = 1
		} else {
			senLen = len
			more = 0
		}
		start = sendCnt
		end = start + senLen
		break
	}

	data = append(data, this.SendSeq)
	data = append(data, more)
	data = append(data, this.RawData[start:end]...)

	this.SendSeq += 1
	this.SendTime = time.Now().Unix()
	this.RetryCnt = DATA_RETRY_MAX_CNT
	return data
}

// used for block resend
func (this *IBlockData) GetLastSendData() []byte {
	data := make([]byte, 4)
	binary.LittleEndian.PutUint32(data, this.BlockToken)
	var start, end int
	var more uint8 = 0

	for {
		if this.DataLen < this.SendStep {
			start = 0
			end = this.DataLen
			more = 0
			break
		}

		start = int((this.SendSeq - 1)) * this.SendStep

		if int(this.SendSeq)*this.SendStep >= this.DataLen {
			// the last block
			end = this.DataLen
			more = 0
		} else {
			end = start + this.SendStep
			more = 1
		}

		break
	}

	data = append(data, this.SendSeq-1)
	data = append(data, more)
	data = append(data, this.RawData[start:end]...)
	this.SendTime = time.Now().Unix()
	return data
}
